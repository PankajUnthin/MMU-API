package com.iemr.mmu.data.nikshay.dtos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.time.LocalDate;
import java.time.Period;

@Data
public class EnrollmentCandidateDto {
    private Long benRegId;
    private String firstName;
    private String middleName;
    private String lastName;
    private String gender;
    private LocalDate dob;
    private Integer ageYears;
    private String phone;
    private String address;
    private String maritalStatus;
    private String occupation;
    private String community;          // caste source
    private String hivStatus;
    private String extraFieldsJson;
    private String keyPopulation;      // from ExtraFields if present
    private String symptoms;           // from ExtraFields if present (pipe separated)
    private String pincode;            // from ExtraFields if present

    private String nikshaySyncStatus;
    private String nikshaySyncFailureReason;
    private Integer attemptCount;

    private Long householdId;
    private Integer villageId;
    private String tracingStatus;      // PENDING | COMPLETED | null (no household)
    private boolean indexPatient;
    private boolean contact;

    public boolean householdTracingComplete() { return "COMPLETED".equalsIgnoreCase(tracingStatus); }

    public static EnrollmentCandidateDto from(com.iemr.mmu.repo.nikshay.EnrollmentCandidate p, ObjectMapper om) {
        EnrollmentCandidateDto c = new EnrollmentCandidateDto();
        c.benRegId = p.getBenRegId();
        c.firstName = p.getFirstName();
        c.middleName = p.getMiddleName();
        c.lastName = p.getLastName();
        c.gender = p.getGender();
        if (p.getDob() != null) {
            c.dob = p.getDob().toLocalDate();
            c.ageYears = Period.between(c.dob, LocalDate.now()).getYears();
        }
        c.maritalStatus = p.getMaritalStatus();
        c.occupation = p.getOccupation();
        c.community = p.getCommunity();
        c.hivStatus = p.getHivStatus();
        c.address = p.getAddress();
        c.phone = p.getPhone();
//        c.nikshaySyncStatus = p.getNikshaySyncStatus();
//        c.nikshaySyncFailureReason = p.getNikshaySyncFailureReason();
        c.attemptCount = p.getAttemptCount() == null ? 0 : p.getAttemptCount();
        c.householdId = p.getHouseholdId();
        c.villageId = p.getVillageId();
        c.tracingStatus = p.getTracingStatus();
//        c.indexPatient = Boolean.TRUE.equals(p.getIndexPatient());
//        c.contact = Boolean.TRUE.equals(p.getContact());
        c.extraFieldsJson = p.getExtraFields();
        if (c.extraFieldsJson != null && !c.extraFieldsJson.isBlank()) {
            try {
                JsonNode n = om.readTree(c.extraFieldsJson);
                if (c.address == null) c.address = text(n, "address");
                c.keyPopulation = text(n, "keyPopulation");
                c.symptoms = text(n, "symptoms");
                c.pincode = text(n, "pincode");
            } catch (Exception ignore) { /* malformed JSON → validator flags missing fields */ }
        }
        return c;
    }

    private static String text(JsonNode n, String f) {
        JsonNode v = n.get(f);
        return v == null || v.isNull() ? null : v.asText();
    }
}
