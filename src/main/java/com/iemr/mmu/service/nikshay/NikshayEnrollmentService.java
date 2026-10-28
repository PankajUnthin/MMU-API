package com.iemr.mmu.service.nikshay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iemr.mmu.config.NikshayProperties;
import com.iemr.mmu.data.nikshay.dtos.AddIndiaTbRequest;
import com.iemr.mmu.data.nikshay.dtos.AddIndiaTbResponse;
import com.iemr.mmu.data.nikshay.dtos.DuplicateCandidateDto;
import com.iemr.mmu.data.nikshay.dtos.DuplicateCheckResult;
import com.iemr.mmu.data.nikshay.dtos.EnrollmentCandidateDto;
import com.iemr.mmu.data.nikshay.dtos.EnrollmentOutcome;
import com.iemr.mmu.data.nikshay.dtos.ValidationResultDto;
import com.iemr.mmu.data.nikshay.entity.NikshayBeneficiarySync;
import com.iemr.mmu.data.nikshay.entity.NikshayDeviceConfiguration;
import com.iemr.mmu.data.nikshay.entity.NikshayDeviceVillageMapping;
import com.iemr.mmu.data.nikshay.entity.NikshayEpisode;
import com.iemr.mmu.exception.NikshayApiException;
import com.iemr.mmu.exception.NikshayConfigException;
import com.iemr.mmu.exception.NikshayException;
import com.iemr.mmu.exception.NikshayLoginRejectedException;
import com.iemr.mmu.exception.NikshaySessionExpiredException;
import com.iemr.mmu.exception.NikshayUnavailableException;
import com.iemr.mmu.repo.nikshay.NikshayBeneficiaryRepository;
import com.iemr.mmu.repo.nikshay.NikshayBeneficiarySyncRepository;
import com.iemr.mmu.repo.nikshay.NikshayDeviceConfigurationRepository;
import com.iemr.mmu.repo.nikshay.NikshayDeviceVillageMappingRepository;
import com.iemr.mmu.repo.nikshay.NikshayEpisodeRepository;
import com.iemr.mmu.service.nikshay.scheduler.NikshayJobManager;
import com.iemr.mmu.utils.nikshay.NikshayClient;
import com.iemr.mmu.utils.nikshay.NikshaySession;
import com.iemr.mmu.utils.nikshay.NikshaySyncConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.iemr.mmu.utils.nikshay.NikshaySyncConstants.RS_CONTACT_TRACING_PENDING;
import static com.iemr.mmu.utils.nikshay.NikshaySyncConstants.RS_MULTIPLE_DUPLICATES;

/**
 * THE Ni-kshay ID-generation flow.
 *
 *  run(ctx)                       – scheduler entry: fetch → bucket by device → per device: precheck,
 *                                   validate, login, loop beneficiaries, logout → counters to manager
 *  runForBeneficiaries(ids, user) – manual push, same code path
 *
 * Persistence is per beneficiary, immediately after each portal call, in its own short transaction,
 * guarded by "nikshayId IS NULL" (see NikshayBeneficiaryDao). A crash mid-run therefore never loses
 * an ID that Ni-kshay already issued, and a re-run never creates a second one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NikshayEnrollmentService {

    private final NikshayProperties props;
    private final NikshayClient client;
    private final NikshayEnrollmentValidator validator;
    private final NikshayBeneficiaryRepository beneficiaryRepo;
    private final NikshayDeviceConfigurationRepository deviceRepo;
    private final NikshayDeviceVillageMappingRepository villageMapRepo;
    private final NikshayEpisodeRepository episodeRepo;
    private final NikshayBeneficiarySyncRepository syncRepo;
    private final NikshayJobManager jobManager;
    private final TransactionTemplate tx;
    private final ObjectMapper om;

    /** Per-run context handed in by the scheduler / manual-push controller. */
    public record RunContext(Long jobRunId, String triggeredBy, Integer userId) {}

    // ======================================================================
    //  entry points
    // ======================================================================

    /** Scheduled run: picks up every retryable beneficiary up to batch size. */
    public void run(RunContext ctx) {
        List<EnrollmentCandidateDto> candidates = beneficiaryRepo
                .fetchEnrollmentCandidates(props.getJob().getMaxAttempts(), props.getJob().getBatchSize())
                .stream().map(p -> EnrollmentCandidateDto.from(p, om)).toList();
        jobManager.candidatesFetched(ctx.jobRunId(), candidates.size());
        log.info("[run {}] {} candidate(s)", ctx.jobRunId(), candidates.size());
        if (candidates.isEmpty()) return;
        process(ctx, candidates);
    }

    /** Manual push from the UI for explicit beneficiaries (status filter bypassed, attempts reset). */
    public void runForBeneficiaries(RunContext ctx, List<Long> benRegIds) {
        tx.executeWithoutResult(st -> beneficiaryRepo.resetForReprocess(benRegIds));
        List<EnrollmentCandidateDto> candidates = beneficiaryRepo.fetchByIds(benRegIds)
                .stream().map(p -> EnrollmentCandidateDto.from(p, om)).toList();
        jobManager.candidatesFetched(ctx.jobRunId(), candidates.size());
        if (candidates.isEmpty()) return;
        process(ctx, candidates);
    }

    // ======================================================================
    //  Phase 2 – bucket by device
    // ======================================================================

    private void process(RunContext ctx, List<EnrollmentCandidateDto> candidates) {
        // beneficiaries with no household → no village → cannot resolve a device
        List<EnrollmentCandidateDto> noVillage = candidates.stream().filter(c -> c.getVillageId() == null).toList();
        if (!noVillage.isEmpty()) {
            bucketFailure(ctx, noVillage, null, NikshaySyncConstants.RS_DEVICE_CONFIG_MISSING,
                    "Beneficiary has no household / village – cannot resolve NAAT device", false, null);
            candidates = candidates.stream().filter(c -> c.getVillageId() != null).toList();
        }

        Set<Integer> villageIds = candidates.stream().map(EnrollmentCandidateDto::getVillageId).collect(Collectors.toSet());
        Map<Integer, NikshayDeviceVillageMapping> villageToMapping = resolveVillageMappings(villageIds);
        Map<Integer, NikshayDeviceConfiguration> devices = loadDevices(villageToMapping.values());

        // bucket: device → candidates ; candidates whose village has no mapping → failure
        Map<Integer, List<EnrollmentCandidateDto>> buckets = new LinkedHashMap<>();
        List<EnrollmentCandidateDto> noDevice = new ArrayList<>();
        for (EnrollmentCandidateDto c : candidates) {
            NikshayDeviceVillageMapping m = villageToMapping.get(c.getVillageId());
            if (m == null || !devices.containsKey(m.getDeviceDetailId())) noDevice.add(c);
            else buckets.computeIfAbsent(m.getDeviceDetailId(), k -> new ArrayList<>()).add(c);
        }
        if (!noDevice.isEmpty())
            bucketFailure(ctx, noDevice, null, NikshaySyncConstants.RS_DEVICE_CONFIG_MISSING, "No active NAAT device mapped to village", false, null);

        // Phase 3–5 per device
        for (Map.Entry<Integer, List<EnrollmentCandidateDto>> e : buckets.entrySet()) {
            NikshayDeviceConfiguration device = devices.get(e.getKey());
            try {
                processDevice(ctx, device, e.getValue(), villageToMapping);
            } catch (RuntimeException ex) {
                log.error("[run {}] device {} aborted: {}", ctx.jobRunId(), device.getDeviceId(), ex.getMessage(), ex);
                jobManager.errorSummary(ctx.jobRunId(), "device " + device.getDeviceId() + ": " + ex.getMessage());
            }
            jobManager.deviceProcessed(ctx.jobRunId());
        }
    }

    /** village → one active NAAT mapping. More than one for a village = config error → treated as none. */
    private Map<Integer, NikshayDeviceVillageMapping> resolveVillageMappings(Set<Integer> villageIds) {
        Map<Integer, NikshayDeviceVillageMapping> out = new HashMap<>();
        Set<Integer> ambiguous = new HashSet<>();
        for (NikshayDeviceVillageMapping m : villageMapRepo.findActiveByVillagesAndDeviceType(villageIds, NikshaySyncConstants.DEVICE_TYPE_NAAT)) {
            if (out.containsKey(m.getVillageId())) ambiguous.add(m.getVillageId());
            else out.put(m.getVillageId(), m);
        }
        for (Integer v : ambiguous) { log.error("Village {} is mapped to more than one active NAAT device – skipping", v); out.remove(v); }
        return out;
    }

    private Map<Integer, NikshayDeviceConfiguration> loadDevices(Collection<NikshayDeviceVillageMapping> mappings) {
        Set<Integer> ids = mappings.stream().map(NikshayDeviceVillageMapping::getDeviceDetailId).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        return deviceRepo.findAllById(ids).stream()
                .filter(d -> Boolean.TRUE.equals(d.getIsActive()) && NikshaySyncConstants.DEVICE_TYPE_NAAT.equalsIgnoreCase(d.getDeviceType()))
                .collect(Collectors.toMap(NikshayDeviceConfiguration::getId, d -> d));
    }

    // ======================================================================
    //  Phase 3–5 – one device
    // ======================================================================

    private void processDevice(RunContext ctx, NikshayDeviceConfiguration device, List<EnrollmentCandidateDto> bucket,
                               Map<Integer, NikshayDeviceVillageMapping> villageToMapping) {
        log.info("[run {}] device {} ({}) – {} beneficiaries", ctx.jobRunId(), device.getDeviceId(), device.getPhiUserId(), bucket.size());

        // ---- Phase 3a: device-level pre-check (no network)
        String cfgProblem = precheckDevice(device);
        if (cfgProblem != null) {
            bucketFailure(ctx, bucket, device, NikshaySyncConstants.RS_DEVICE_CONFIG_MISSING, cfgProblem, false, null);
            return;
        }

        // ---- Phase 3b: validation per beneficiary (no network)
        Map<Long, ValidationResultDto> validated = new LinkedHashMap<>();
        List<EnrollmentCandidateDto> toProcess = new ArrayList<>();
        for (EnrollmentCandidateDto c : bucket) {
            NikshayDeviceVillageMapping vm = villageToMapping.get(c.getVillageId());
            if (vm.getNikshayVillageId() == null) {
                persist(ctx, c, EnrollmentOutcome.builder().benRegId(c.getBenRegId()).outcome(EnrollmentOutcome.Outcome.FAILED)
                        .failureReason(NikshaySyncConstants.RS_DEVICE_CONFIG_MISSING)
                        .failureDetail("nikshay_village_id missing for village " + c.getVillageId())
                        .deviceConfigId(device.getId()).build());
                continue;
            }
            ValidationResultDto v = validator.validate(c);
            if (!v.isValid()) {
                persist(ctx, c, EnrollmentOutcome.builder().benRegId(c.getBenRegId()).outcome(EnrollmentOutcome.Outcome.FAILED)
                        .failureReason(NikshaySyncConstants.RS_VALIDATION_FAILED).failureDetail(v.failureDetail())
                        .autoFixes(v.getAutoFixes()).deviceConfigId(device.getId()).build());
                continue;
            }
            validated.put(c.getBenRegId(), v);
            toProcess.add(c);
        }
        if (toProcess.isEmpty()) { log.info("[run {}] device {}: nothing left after validation", ctx.jobRunId(), device.getDeviceId()); return; }

        // index patients first so contacts can hang off them in a later step
        toProcess.sort(Comparator.comparing((EnrollmentCandidateDto c) -> !c.isIndexPatient()).thenComparing(EnrollmentCandidateDto::getBenRegId));

        // ---- Phase 4: login once per device
        NikshaySession session;
        try {
            session = client.login(device);
        } catch (NikshayLoginRejectedException e) {
            bucketFailure(ctx, toProcess, device, NikshaySyncConstants.RS_DEVICE_LOGIN_FAILED, e.getMessage(), false, null);
            jobManager.deviceLoginFailed(ctx.jobRunId());
            return;
        } catch (NikshayUnavailableException e) {
            bucketFailure(ctx, toProcess, device, NikshaySyncConstants.RS_PORTAL_UNAVAILABLE, "Login: " + e.getMessage(), true, backoff(1));
            return;
        } catch (NikshayConfigException e) {
            bucketFailure(ctx, toProcess, device, NikshaySyncConstants.RS_DEVICE_CONFIG_MISSING, e.getMessage(), false, null);
            return;
        }

        // ---- Phase 5: per beneficiary
        int consecutiveTransient = 0;
        try {
            for (int i = 0; i < toProcess.size(); i++) {
                EnrollmentCandidateDto c = toProcess.get(i);
                if (consecutiveTransient >= props.getJob().getConsecutiveFailThreshold()) {
                    // portal is down – don't burn attempts on the rest of this bucket
                    bucketFailure(ctx, toProcess.subList(i, toProcess.size()), device, NikshaySyncConstants.RS_PORTAL_UNAVAILABLE,
                            "Skipped: portal unavailable (" + consecutiveTransient + " consecutive failures)", true, backoff(1));
                    break;
                }
                EnrollmentOutcome out;
                try {
                    out = enrollOne(session, device, villageToMapping.get(c.getVillageId()), c, validated.get(c.getBenRegId()));
                } catch (NikshaySessionExpiredException se) {
                    log.warn("[run {}] session expired on device {} – re-login once", ctx.jobRunId(), device.getDeviceId());
                    try {
                        session = client.login(device);
                        out = enrollOne(session, device, villageToMapping.get(c.getVillageId()), c, validated.get(c.getBenRegId()));
                    } catch (NikshayLoginRejectedException lr) {
                        bucketFailure(ctx, toProcess.subList(i, toProcess.size()), device, NikshaySyncConstants.RS_DEVICE_LOGIN_FAILED, lr.getMessage(), false, null);
                        jobManager.deviceLoginFailed(ctx.jobRunId());
                        break;
                    } catch (NikshayException ex2) {
                        out = transientFailure(c, device, ex2);
                    }
                }
                consecutiveTransient = NikshaySyncConstants.RS_PORTAL_UNAVAILABLE.equals(out.getFailureReason()) ? consecutiveTransient + 1 : 0;
                persist(ctx, c, out);
                pace();
            }
        } finally {
            client.logout(session);
        }
    }

    private String precheckDevice(NikshayDeviceConfiguration d) {
        if (d.getPhiUserId() == null || d.getPhiUserId().isBlank() || d.getPassword() == null || d.getPassword().isBlank())
            return "Wrapper login not configured for device " + d.getDeviceId();
        if (d.getNikshayFacilityId() == null)
            return "nikshay_facility_id missing for device " + d.getDeviceId();
        if (d.getMoreData() != null && d.getMoreData().contains("servicesEnabled") && !d.getMoreData().contains("NAAT"))
            return "NAAT service not enabled on device " + d.getDeviceId();
        return null;
    }

    // ======================================================================
    //  Phase 5 – one beneficiary (pure portal interaction, returns an outcome, never throws
    //            except NikshaySessionExpiredException which the caller handles)
    // ======================================================================

    private EnrollmentOutcome enrollOne(NikshaySession session, NikshayDeviceConfiguration device,
                                        NikshayDeviceVillageMapping village, EnrollmentCandidateDto c, ValidationResultDto v) {
        long t0 = System.currentTimeMillis();
        EnrollmentOutcome.EnrollmentOutcomeBuilder b = EnrollmentOutcome.builder()
                .benRegId(c.getBenRegId()).deviceConfigId(device.getId())
                .nikshayFacilityId(device.getNikshayFacilityId()).autoFixes(v.getAutoFixes());
        try {
            // ---- duplicate check
            DuplicateCheckResult dup = client.getPossibleDuplicates(session, validator.buildDuplicateQuery(v));
//            b.dupCheckResponse(dup.getRawBody()).httpStatus(dup.getHttpStatus());
            String hitIds = dup.getAllHits().stream().map(h -> String.valueOf(h.getPatientId())).collect(Collectors.joining(" "));
            b.possibleDuplicateOf(hitIds.isEmpty() ? null : hitIds);

            List<DuplicateCandidateDto> firm = validator.findFirmMatches(dup.getAllHits(), v);

            if (firm.size() > 1) {
                return b.outcome(EnrollmentOutcome.Outcome.HELD).failureReason(RS_MULTIPLE_DUPLICATES)
                        .failureDetail(firm.size() + " firm matches: " + firm.stream().map(h -> String.valueOf(h.getPatientId())).collect(Collectors.joining(",")))
                        .durationMs(ms(t0)).build();
            }

            if (firm.size() == 1) {
                DuplicateCandidateDto h = firm.get(0);
                b.stageFound(h.getStage()).outcomeAssigned(h.getIsPatientOutcomeAssigned());
                switch (validator.actionFor(h)) {
                    case LINK:
                        return b.outcome(EnrollmentOutcome.Outcome.LINKED).linkStatus("LINKED").nikshayParentId(String.valueOf(h.getPatientId()))
                                .nikshayEpisodeId(String.valueOf(h.getPatientId())).createdByWrapper(false).durationMs(ms(t0)).build();
                    case NO_ACTION:
                        return b.outcome(EnrollmentOutcome.Outcome.LINKED_NO_ACTION).linkStatus("LINKED_NO_ACTION").nikshayParentId(String.valueOf(h.getPatientId()))
                                .nikshayEpisodeId(String.valueOf(h.getPatientId())).createdByWrapper(false).durationMs(ms(t0)).build();
                    case NEW_EPISODE:
                        return addPatient(session, device, village, v, b, h.getPatientId(), true, t0);
                    default:
                        return b.outcome(EnrollmentOutcome.Outcome.FAILED).failureReason(NikshaySyncConstants.RS_NIKSHAY_VALIDATION_ERROR)
                                .failureDetail("Unmapped Ni-kshay stage: " + h.getStage()).durationMs(ms(t0)).build();
                }
            }

            // ---- NEW patient: household gate applies only here
            if (!c.householdTracingComplete()) {
                return b.outcome(EnrollmentOutcome.Outcome.HELD).failureReason(RS_CONTACT_TRACING_PENDING)
                        .failureDetail("Household " + c.getHouseholdId() + " tracing not completed").durationMs(ms(t0)).build();
            }
            boolean loose = !dup.hasAnyHit() && props.getJob().isCreateOnLooseMatch();
            return addPatient(session, device, village, v, b, null, loose, t0);

        } catch (NikshaySessionExpiredException e) {
            throw e;                                   // caller re-logs in
        } catch (NikshayApiException e) {
            return b.outcome(EnrollmentOutcome.Outcome.FAILED).failureReason(NikshaySyncConstants.RS_NIKSHAY_VALIDATION_ERROR).failureDetail(e.getMessage())
                    .httpStatus(e.getHttpStatus()).durationMs(ms(t0)).build();
        } catch (NikshayUnavailableException e) {
            return transientFailure(c, device, e);
        }
    }

    private EnrollmentOutcome addPatient(NikshaySession session, NikshayDeviceConfiguration device, NikshayDeviceVillageMapping village,
                                         ValidationResultDto v, EnrollmentOutcome.EnrollmentOutcomeBuilder b,
                                         Long parentEpisodeId, boolean isPossibleDuplicate, long t0) {
        AddIndiaTbRequest req = validator.buildAddRequest(v, device, village, parentEpisodeId, isPossibleDuplicate);
//        b.addRequestSummary(toJson(req));
        AddIndiaTbResponse res = client.addIndiaTb(session, req);
//        b.addResponse(res.getRawBody()).httpStatus(res.getHttpStatus()).createdByWrapper(true);
        if (parentEpisodeId != null) {
            return b.outcome(EnrollmentOutcome.Outcome.NEW_EPISODE_CREATED).linkStatus("NEW_EPISODE")
                    .nikshayParentId(parentEpisodeId.toString()).nikshayEpisodeId(res.getPatientId().toString()).durationMs(ms(t0)).build();
        }
        return b.outcome(EnrollmentOutcome.Outcome.ENROLLED).linkStatus("NEW")
                .nikshayParentId(res.getPatientId().toString()).nikshayEpisodeId(res.getPatientId().toString()).durationMs(ms(t0)).build();
    }

    private EnrollmentOutcome transientFailure(EnrollmentCandidateDto c, NikshayDeviceConfiguration device, NikshayException e) {
        return EnrollmentOutcome.builder().benRegId(c.getBenRegId()).deviceConfigId(device.getId())
                .outcome(EnrollmentOutcome.Outcome.FAILED).failureReason(NikshaySyncConstants.RS_PORTAL_UNAVAILABLE).failureDetail(e.getMessage())
                .httpStatus(e.getHttpStatus()).build();
    }

    // ======================================================================
    //  persistence – one short transaction per beneficiary, AFTER the HTTP call
    // ======================================================================

    private void persist(RunContext ctx, EnrollmentCandidateDto c, EnrollmentOutcome out) {
        tx.executeWithoutResult(status -> {
            int attemptNo = (c.getAttemptCount() == null ? 0 : c.getAttemptCount()) + 1;
            Long episodeId = null;

            switch (out.getOutcome()) {
                case ENROLLED, LINKED, NEW_EPISODE_CREATED, LINKED_NO_ACTION -> {
                    int updated = beneficiaryRepo.markSynced(c.getBenRegId(), String.valueOf(out.getNikshayParentId()),
                            out.isCreatedByWrapper(), out.getOutcome() == EnrollmentOutcome.Outcome.LINKED_NO_ACTION
                                    ? NikshaySyncConstants.ST_LINKED_NO_ACTION : NikshaySyncConstants.ST_SYNCED);
                    if (updated == 0) {
                        log.warn("Beneficiary {} already has a Ni-kshay id – outcome {} recorded but not applied",
                                c.getBenRegId(), out.getOutcome());
                    } else {
                        episodeId = saveEpisode(c, out);
                    }
                }
                case HELD -> beneficiaryRepo.markHeld(c.getBenRegId(), out.getFailureReason());
                case FAILED -> {
                    boolean portal = NikshaySyncConstants.RS_PORTAL_UNAVAILABLE.equals(out.getFailureReason());
                    int nextAttempt = attemptNo;
                    String reason = out.getFailureReason();
                    if (out.isCountsAsAttempt() && nextAttempt >= props.getJob().getMaxAttempts()) reason = NikshaySyncConstants.RS_MAX_ATTEMPTS;
                    beneficiaryRepo.markFailed(c.getBenRegId(), NikshaySyncConstants.ST_FAILED, portal && !NikshaySyncConstants.RS_MAX_ATTEMPTS.equals(reason) ? Timestamp.valueOf(backoff(nextAttempt)) : null);
                    out.setFailureReason(reason);
                }
            }

            NikshayBeneficiarySync s = new NikshayBeneficiarySync();
            s.setBenRegId(c.getBenRegId());
            s.setEpisodeId(episodeId);
            s.setJobRunId(ctx.jobRunId());
            s.setDeviceConfigId(out.getDeviceConfigId());
            s.setAttemptNo(attemptNo);
            s.setTriggeredBy(ctx.triggeredBy());
            s.setTriggeredUserId(ctx.userId());
            s.setOutcome(out.getOutcome().name());
            s.setFailureReason(out.getFailureReason());
            s.setFailureDetail(trunc(out.getFailureDetail(), 1000));
            s.setNikshayId(out.getNikshayParentId() == null ? null : String.valueOf(out.getNikshayParentId()));
            s.setNikshayEpisodeId(out.getNikshayEpisodeId() == null ? null : String.valueOf(out.getNikshayEpisodeId()));
            s.setStageFound(out.getStageFound());
            s.setPossibleDuplicateOf(trunc(out.getPossibleDuplicateOf(), 255));
//            s.setDupCheckResponse(jsonOrNull(out.getDupCheckResponse()));
//            s.setAddRequestSummary(jsonOrNull(out.getAddRequestSummary()));
            s.setAddResponse(jsonOrNull(out.getAddResponseJson()));
            s.setAutoFixes(out.getAutoFixes() == null || out.getAutoFixes().isEmpty() ? null : toJson(out.getAutoFixes()));
            s.setHttpStatus(out.getHttpStatus());
            s.setDurationMs((int) out.getDurationMs());
            syncRepo.save(s);
        });
        jobManager.count(ctx.jobRunId(), out.getOutcome());
        log.info("[run {}] ben {} → {}{}", ctx.jobRunId(), c.getBenRegId(), out.getOutcome(),
                out.getFailureReason() != null ? " (" + out.getFailureReason() + ")" : "");
    }

    private Long saveEpisode(EnrollmentCandidateDto c, EnrollmentOutcome out) {
        episodeRepo.clearCurrent(c.getBenRegId());
        NikshayEpisode e = new NikshayEpisode();
        e.setBenRegId(c.getBenRegId());
        e.setEpisodeNo(episodeRepo.maxEpisodeNo(c.getBenRegId()) + 1);
        e.setNikshayParentId(String.valueOf(out.getNikshayParentId()));
        e.setNikshayEpisodeId(String.valueOf(out.getNikshayEpisodeId()));
        e.setLinkStatus(out.getLinkStatus());
        e.setStageFound(out.getStageFound());
        e.setOutcomeAssigned(out.getOutcomeAssigned());
        e.setCreatedByWrapper(out.isCreatedByWrapper());
        e.setIsCurrent(true);
        e.setDeviceConfigId(out.getDeviceConfigId());
        e.setNikshayFacilityId(out.getNikshayFacilityId());
        return episodeRepo.save(e).getEpisodeId();
    }

    /** Same outcome for many beneficiaries (no device, login rejected, portal down mid-bucket). */
    private void bucketFailure(RunContext ctx, List<EnrollmentCandidateDto> list, NikshayDeviceConfiguration device,
                               String reason, String detail, boolean countAttempt, LocalDateTime nextRetry) {
        if (list.isEmpty()) return;
        tx.executeWithoutResult(status -> {
            beneficiaryRepo.markBucketFailed(list.stream().map(EnrollmentCandidateDto::getBenRegId).toList(), reason);
            for (EnrollmentCandidateDto c : list) {
                NikshayBeneficiarySync s = new NikshayBeneficiarySync();
                s.setBenRegId(c.getBenRegId());
                s.setJobRunId(ctx.jobRunId());
                s.setDeviceConfigId(device == null ? null : device.getId());
                s.setAttemptNo((c.getAttemptCount() == null ? 0 : c.getAttemptCount()) + 1);
                s.setTriggeredBy(ctx.triggeredBy());
                s.setTriggeredUserId(ctx.userId());
                s.setOutcome(EnrollmentOutcome.Outcome.FAILED.name());
                s.setFailureReason(reason);
                s.setFailureDetail(trunc(detail, 1000));
                syncRepo.save(s);
            }
        });
        jobManager.countFailed(ctx.jobRunId(), list.size());
        log.warn("[run {}] {} beneficiaries → FAILED/{}: {}", ctx.jobRunId(), list.size(), reason, detail);
    }

    // ======================================================================
    //  helpers
    // ======================================================================

    private LocalDateTime backoff(int attempt) {
        return LocalDateTime.now().plusMinutes((long) props.getJob().getRetryBackoffMinutes() * Math.max(1, attempt));
    }
    private void pace() {
        int d = props.getJob().getCallDelayMs();
        if (d > 0) try { Thread.sleep(d); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
    private static long ms(long t0) { return System.currentTimeMillis() - t0; }
    private static String trunc(String s, int n) { return s == null || s.length() <= n ? s : s.substring(0, n); }
    private String toJson(Object o) { try { return om.writeValueAsString(o); } catch (Exception e) { return null; } }
    /** JSON columns reject non-JSON text; wrap plain strings. */
    private String jsonOrNull(String body) {
        if (body == null) return null;
        try { om.readTree(body); return body; } catch (Exception e) { return toJson(Map.of("text", body)); }
    }
}