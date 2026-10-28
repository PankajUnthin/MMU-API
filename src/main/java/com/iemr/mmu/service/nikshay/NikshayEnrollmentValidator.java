package com.iemr.mmu.service.nikshay;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

import com.iemr.mmu.data.nikshay.dtos.AddIndiaTbRequest;
import com.iemr.mmu.data.nikshay.dtos.DuplicateCandidateDto;
import com.iemr.mmu.data.nikshay.dtos.DuplicateCheckRequest;
import com.iemr.mmu.data.nikshay.dtos.EnrollmentCandidateDto;
import com.iemr.mmu.data.nikshay.dtos.ValidationResultDto;
import com.iemr.mmu.data.nikshay.entity.NikshayDeviceConfiguration;
import com.iemr.mmu.data.nikshay.entity.NikshayDeviceVillageMapping;
import com.iemr.mmu.repo.nikshay.EnrollmentCandidate;
import org.springframework.stereotype.Component;

/**
 * All DATA-SHAPING for enrollment. Pure: no DB, no HTTP.
 * 1. validate()        – BRD V1–V16 (auto-fix where allowed, else block)
 * 2. findFirmMatches() – JS evaluateDuplicates(): name + gender + phone + DOB/age
 * 3. actionFor()       – BRD §7 Stage → action table
 * 4. buildDuplicateQuery() / buildAddRequest() – DTOs for the client
 */
@Component
public class NikshayEnrollmentValidator {

    public static final DateTimeFormatter DDMMYYYY = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final Pattern ENGLISH_NAME = Pattern.compile("^[A-Za-z][A-Za-z .'-]*$");
    private static final Pattern PHONE10 = Pattern.compile("^[1-9]\\d{9}$");
    private static final Pattern PINCODE = Pattern.compile("^\\d{6}$");

    /**
     * What to do with a single firm match, from the BRD §7 table.
     */
    public enum DuplicateAction {LINK, NEW_EPISODE, NO_ACTION, UNMAPPED}

    // ------------------------------------------------------------------ 1. validation

    public ValidationResultDto validate(EnrollmentCandidateDto c) {
        ValidationResultDto r = new ValidationResultDto();

        // names (V5, V6)
        String first = clean(c.getFirstName());
        String last = clean(join(c.getMiddleName(), c.getLastName()));
        if (first == null) return r.fail("V16", "FirstName", "missing");
        if (!ENGLISH_NAME.matcher(first).matches()) return r.fail("V5", "FirstName", "English letters only: " + first);
        if (last == null) return r.fail("V6", "LastName", "middle/last name required");
        if (!ENGLISH_NAME.matcher(last).matches()) return r.fail("V5", "LastName", "English letters only: " + last);
        r.getCleaned().setFirstName(first);
        r.getCleaned().setLastName(last);

        // gender (V11)
        String g = mapGender(c.getGender());
        if (g == null) return r.fail("V11", "Gender", "missing or unmapped: " + c.getGender());
        r.getCleaned().setGender(g);

        // age / dob (V1–V4): AMRIT stores DOB; age is derived
        if (c.getDob() == null) return r.fail("V3", "DOB", "missing");
        int age = c.getAgeYears() != null ? c.getAgeYears() : java.time.Period.between(c.getDob(), LocalDate.now()).getYears();
        if (age < 1) return r.fail("V2", "Age", "under 12 months – manual enrollment");
        if (age > 99) return r.fail("V3", "Age", "age " + age + " outside 1–99");
        r.getCleaned().setAge(age);
        r.getCleaned().setDob(c.getDob());

        // phone (V7 auto-fix, V8)
        String rawPhone = c.getPhone();
        String phone = rawPhone == null ? "" : rawPhone.replaceAll("[\\s-]", "");
        if (phone.startsWith("+91")) phone = phone.substring(3);
        if (phone.startsWith("0") && phone.length() == 11) phone = phone.substring(1);
        if (!phone.equals(rawPhone == null ? "" : rawPhone))
//            r.getAutoFixes().add(new AutoFix("V7", "PrimaryPhone", rawPhone, phone));
        if (!PHONE10.matcher(phone).matches())
            return r.fail("V8", "PrimaryPhone", "must be 10 digits, no leading 0: " + rawPhone);
        r.getCleaned().setPhone(phone);

        // address (V9)
        String addr = clean(c.getAddress());
        if (addr == null) return r.fail("V9", "Address", "missing");
        if (addr.length() > 2000) return r.fail("V9", "Address", "over 2000 chars");
        r.getCleaned().setAddress(addr);

        // caste (V11)
        String caste = mapCaste(c.getCommunity());
        if (caste == null) return r.fail("V11", "caste", "missing or unmapped: " + c.getCommunity());
//        r.setCaste(caste);

        // optional, mapped-or-Unknown
        r.getCleaned().setMaritalStatus(orUnknown(c.getMaritalStatus()));
        r.getCleaned().setOccupation(orUnknown(c.getOccupation()));
        r.getCleaned().setHivStatus(mapHiv(c.getHivStatus()));
//        r.getCleaned().setTypeOfCaseFinding(mapCaseFinding(c.getExtraFieldsJson()));
//        r.getCleaned().setSymptomCsv(symptomsCsv(c.getSymptoms()));
        String pin = clean(c.getPincode());
//        r.getCleaned().setPincode(pin != null && PINCODE.matcher(pin).matches() ? pin : null);

        // key population (V12) – only matters for TPT-direct
        String kp = clean(c.getKeyPopulation());
        if (c.isContact()) {
            if (kp == null || "Other".equalsIgnoreCase(kp) || "Not Applicable".equalsIgnoreCase(kp))
                return r.fail("V12", "KeyPopulation", "contact must have a valid key-population factor");
            r.getCleaned().setKeyPopulation(kp);
        } else {
            r.getCleaned().setKeyPopulation(kp != null ? kp : "Not Applicable");
        }
        return r;
    }

    // ------------------------------------------------------------------ 2. firm match (ported from enroll.js)

    /**
     * Portal returns phone+gender hits; tighten to name + gender + phone + DOB (age fallback).
     */
    public List<DuplicateCandidateDto> findFirmMatches(List<DuplicateCandidateDto> hits, ValidationResultDto v) {
        if (hits == null || hits.isEmpty()) return List.of();
        String fullName = norm(v.getCleaned().getFirstName() + " " + v.getCleaned().getLastName());
        String gender = norm(v.getCleaned().getGender());
        String phone = v.getCleaned().getPhone();
        String dob = normalizeDob(v.getCleaned().getDob().toString());

        List<DuplicateCandidateDto> firm = new ArrayList<>();
        for (DuplicateCandidateDto h : hits) {
            if (!nameMatches(h, fullName)) continue;
            if (!gender.equals(norm(h.getGender()))) continue;
            String hp = h.getPrimaryPhoneNumber() == null ? "" : h.getPrimaryPhoneNumber().replaceAll("\\D", "");
            if (!phone.equals(hp)) continue;
            String hd = normalizeDob(h.getDateOfBirth());
            if (!hd.isEmpty() && !dob.isEmpty()) {
                if (hd.equals(dob)) firm.add(h);
                continue;
            }
            if (h.getAge() != null && v.getCleaned().getAge() != null) {
                if (h.getAge().equals(v.getCleaned().getAge())) firm.add(h);
                continue;
            }
            // nothing comparable → not firm
        }
        return firm;
    }

    private static boolean nameMatches(DuplicateCandidateDto h, String fullName) {
        String hay = norm(join(h.getFirstName(), h.getLastName()));
        if (hay.contains(fullName)) return true;
        Set<String> words = new HashSet<>(Arrays.asList(hay.split(" ")));
        return Arrays.stream(fullName.split(" ")).allMatch(words::contains);
    }

    // ------------------------------------------------------------------ 3. BRD §7 action table

    /**
     * Presumptive–Open                      → LINK (tests pushed)
     * Presumptive–Closed / Outcome assigned → NEW_EPISODE
     * Diagnosed not on treatment / closed   → NO_ACTION / NEW_EPISODE
     * On treatment                          → NO_ACTION
     */
    public DuplicateAction actionFor(DuplicateCandidateDto h) {
        String stage = h.getStage() == null ? "" : h.getStage().toLowerCase(Locale.ROOT);
        boolean outcome = Boolean.TRUE.equals(h.getIsPatientOutcomeAssigned());
        boolean onTreatment = Boolean.TRUE.equals(h.getOnTreatment()) || stage.contains("on treatment") || stage.contains("ontreatment");
        boolean closed = stage.contains("closed");

        if (onTreatment) return DuplicateAction.NO_ACTION;
        if (stage.contains("presumptive")) return closed ? DuplicateAction.NEW_EPISODE : DuplicateAction.LINK;
        if (stage.contains("diagnosed"))
            return closed || outcome ? DuplicateAction.NEW_EPISODE : DuplicateAction.NO_ACTION;
        if (outcome || stage.contains("outcome")) return DuplicateAction.NEW_EPISODE;
        return DuplicateAction.UNMAPPED;
    }

    // ------------------------------------------------------------------ 4. DTO builders

    public DuplicateCheckRequest buildDuplicateQuery(ValidationResultDto v) {
        return DuplicateCheckRequest.builder()
                .primaryPhone(v.getCleaned().getPhone()).gender(v.getCleaned().getGender())
                .firstName(v.getCleaned().getFirstName()).lastName(v.getCleaned().getLastName())
                .age(v.getCleaned().getAge()).dateOfBirth(v.getCleaned().getDob().toString()).build();
    }

    public AddIndiaTbRequest buildAddRequest(ValidationResultDto v, NikshayDeviceConfiguration device,
                                             NikshayDeviceVillageMapping village, Long parentEpisodeId,
                                             boolean isPossibleDuplicate) {
        return AddIndiaTbRequest.builder()
                .registrationDate(LocalDate.now().format(DDMMYYYY))
                .selectedHierarchyId(device.getNikshayFacilityId())
                .firstName(v.getCleaned().getFirstName()).lastName(v.getCleaned().getLastName())
                .age(String.valueOf(v.getCleaned().getAge())).gender(v.getCleaned().getGender())
                .primaryPhone(v.getCleaned().getPhone()).address(v.getCleaned().getAddress())
                .residenceHierarchyId(village.getNikshayVillageId())
                .residenceHierarchyAll(List.of(1, village.getStateId(), village.getDistrictId(), village.getTbuId(), village.getNikshayVillageId()))
//                .pincode(v.getCleaned().getPincode())
                .maritalStatus(v.getCleaned().getMaritalStatus()).occupation(v.getCleaned().getOccupation())
                .keyPopulation(v.getCleaned().getKeyPopulation())
//                .symptom(v.getCleaned().getSymptomCsv()).hivStatus(v.getCleaned().getHivStatus())
                .parentEpisodeId(parentEpisodeId).isPossibleDuplicate(isPossibleDuplicate)
                .typeOfCaseFinding(v.getCleaned().getCaseFindingType())
                .dateOfBirth(v.getCleaned().getDob().toString()).caste(v.getCleaned().getCommunity())
                .build();
    }

    // ------------------------------------------------------------------ mapping helpers

    static String mapGender(String g) {
        if (g == null) return null;
        switch (g.trim().toLowerCase(Locale.ROOT)) {
            case "male":
            case "m":
                return "Male";
            case "female":
            case "f":
                return "Female";
            case "transgender":
            case "t":
            case "other":
                return "Transgender";
            default:
                return null;
        }
    }

    static String mapCaste(String c) {
        if (c == null) return null;
        String u = c.trim().toUpperCase(Locale.ROOT);
        if (u.equals("SC")) return "SC";
        if (u.equals("ST")) return "ST";
        if (u.isEmpty()) return null;
        return "Others";
    }

    static String mapHiv(String h) {
        if (h == null) return "Unknown";
        String l = h.trim().toLowerCase(Locale.ROOT);
        if (l.contains("reactive") && !l.contains("non")) return "Reactive";
        if (l.contains("non")) return "Non-Reactive";
        if (l.contains("positive")) return "Reactive";
        if (l.contains("negative")) return "Non-Reactive";
        return "Unknown";
    }

    static String mapCaseFinding(String extraJson) {
        if (extraJson != null && extraJson.toLowerCase(Locale.ROOT).contains("passive"))
            return "Passive (Routine programme)";
        return "Active (Community screening)";
    }

    static String symptomsCsv(String pipe) {
        String s = pipe == null || pipe.isBlank() ? "Asymptomatic" : pipe;
        String joined = Arrays.stream(s.split("\\|")).map(String::trim).filter(x -> !x.isEmpty()).reduce((a, b) -> a + "," + b).orElse("Asymptomatic");
        return joined.endsWith(",") ? joined : joined + ",";
    }

    static String orUnknown(String s) {
        String c = clean(s);
        return c == null ? "Unknown" : c;
    }

    static String clean(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    static String join(String a, String b) {
        String x = clean(a), y = clean(b);
        if (x == null) return y;
        if (y == null) return x;
        return x + " " + y;
    }

    static String norm(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 01-01-1991 / 1991-01-01 / 1/1/1991 / 1991 → yyyy-MM-dd for comparison (ported).
     */
    static String normalizeDob(String v) {
        if (v == null || v.isBlank()) return "";
        String s = v.trim();
        java.util.regex.Matcher m = Pattern.compile("^(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2})").matcher(s);
        if (m.find()) return m.group(1) + "-" + pad(m.group(2)) + "-" + pad(m.group(3));
        m = Pattern.compile("^(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})").matcher(s);
        if (m.find()) return m.group(3) + "-" + pad(m.group(2)) + "-" + pad(m.group(1));
        m = Pattern.compile("^(\\d{4})$").matcher(s);
        if (m.find()) return m.group(1) + "-01-01";
        return norm(s);
    }

    private static String pad(String n) {
        return n.length() == 1 ? "0" + n : n;
    }
}
