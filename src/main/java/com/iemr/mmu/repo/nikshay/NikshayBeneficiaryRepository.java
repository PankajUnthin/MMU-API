package com.iemr.mmu.repo.nikshay;

import com.iemr.mmu.data.nikshay.entity.BeneficiaryDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

@Repository
public interface NikshayBeneficiaryRepository extends JpaRepository<BeneficiaryDetails, Long> {

    @Query(value = """
            SELECT  b.BeneficiaryRegID        AS benRegId,
                    b.FirstName               AS firstName,
                    b.MiddleName              AS middleName,
                    b.LastName                AS lastName,
                    b.Gender                  AS gender,
                    b.DOB                     AS dob,
                    b.MaritalStatus           AS maritalStatus,
                    b.occupation              AS occupation,
                    b.community               AS community,
                    b.HIVStatus               AS hivStatus,
                    b.address                 AS address,
                    b.ExtraFields             AS extraFields,
                    c.PreferredPhoneNum       AS phone,
                    h.household_id            AS householdId,
                    h.village_id              AS villageId,
                    h.tracing_status          AS tracingStatus,
                    hm.is_index_patient       AS isIndexPatient,
                    hm.is_contact             AS isContact,
                    b.nikshayId               AS nikshayId,
                    e.episode_no              AS currentEpisodeNo,
                    COALESCE(b.nikshayAttemptCount, 0) AS attemptCount,
                    b.nikshaySyncStatus       AS syncStatus
            FROM    i_beneficiarydetails b
            JOIN    t_household_member hm  ON hm.ben_reg_id = b.BeneficiaryRegID AND hm.is_active = 1
            JOIN    t_household h          ON h.household_id = hm.household_id   AND h.is_active = 1
            LEFT JOIN i_beneficiarycontacts c ON c.BeneficiaryRegID = b.BeneficiaryRegID
            LEFT JOIN t_nikshay_episode e  ON e.ben_reg_id = b.BeneficiaryRegID AND e.is_current = 1
            WHERE   b.Deleted = 0
              AND   b.nikshayId IS NULL                                   -- TODO re-screen predicate for new episodes
              AND   COALESCE(b.nikshayAttemptCount, 0) < :maxAttempts
              AND  (b.nikshayNextRetryAt IS NULL OR b.nikshayNextRetryAt <= NOW())
              AND  (
                     b.nikshaySyncStatus IS NULL
                  OR b.nikshaySyncStatus = 'PENDING'
                  OR (b.nikshaySyncStatus = 'FAILED' AND b.nikshaySyncFailureReason = 'PORTAL_UNAVAILABLE')
                  OR (b.nikshaySyncStatus = 'HELD'   AND b.nikshaySyncFailureReason = 'CONTACT_TRACING_PENDING'
                                                     AND h.tracing_status = 'COMPLETED')
                   )
            ORDER BY h.village_id, hm.is_index_patient DESC, b.BeneficiaryRegID
            LIMIT :batchSize
            """, nativeQuery = true)
    List<EnrollmentCandidate> fetchEnrollmentCandidates(@Param("maxAttempts") int maxAttempts,
                                                        @Param("batchSize") int batchSize);

    /**
     * Same projection for an explicit list — manual push from the UI.
     */
    @Query(value = """
            SELECT  b.BeneficiaryRegID AS benRegId, b.FirstName AS firstName, b.MiddleName AS middleName,
                    b.LastName AS lastName, b.Gender AS gender, b.DOB AS dob, b.MaritalStatus AS maritalStatus,
                    b.occupation AS occupation, b.community AS community, b.HIVStatus AS hivStatus,
                    b.address AS address, b.ExtraFields AS extraFields, c.PreferredPhoneNum AS phone,
                    h.household_id AS householdId, h.village_id AS villageId, h.tracing_status AS tracingStatus,
                    hm.is_index_patient AS isIndexPatient, hm.is_contact AS isContact,
                    b.nikshayId AS nikshayId, e.episode_no AS currentEpisodeNo,
                    COALESCE(b.nikshayAttemptCount, 0) AS attemptCount, b.nikshaySyncStatus AS syncStatus
            FROM    i_beneficiarydetails b
            JOIN    t_household_member hm ON hm.ben_reg_id = b.BeneficiaryRegID AND hm.is_active = 1
            JOIN    t_household h         ON h.household_id = hm.household_id   AND h.is_active = 1
            LEFT JOIN i_beneficiarycontacts c ON c.BeneficiaryRegID = b.BeneficiaryRegID
            LEFT JOIN t_nikshay_episode e ON e.ben_reg_id = b.BeneficiaryRegID AND e.is_current = 1
            WHERE   b.BeneficiaryRegID IN (:benRegIds) AND b.Deleted = 0
            ORDER BY h.village_id, hm.is_index_patient DESC, b.BeneficiaryRegID
            """, nativeQuery = true)
    List<EnrollmentCandidate> fetchByIds(@Param("benRegIds") List<Long> benRegIds);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE i_beneficiarydetails
            SET    nikshayId               = COALESCE(nikshayId, :nikshayId),
                   nikshayCreatedByAmrit   = :createdByAmrit,
                   nikshaySyncStatus       = :syncStatus,
                   nikshaySyncFailureReason = NULL,
                   nikshayNextRetryAt      = NULL,
                   nikshayAttemptCount     = 0,
                   LastModDate             = NOW()
            WHERE  BeneficiaryRegID = :benRegId
            """, nativeQuery = true)
    int markSynced(@Param("benRegId") Long benRegId,
                   @Param("nikshayId") String nikshayId,
                   @Param("createdByAmrit") boolean createdByAmrit,
                   @Param("syncStatus") String syncStatus);      // SYNCED | LINKED_NO_ACTION


    @Modifying
    @Transactional
    @Query(value = """
            UPDATE i_beneficiarydetails
            SET    nikshaySyncStatus = 'HELD', nikshaySyncFailureReason = :reason,
                   nikshayNextRetryAt = NULL, LastModDate = NOW()
            WHERE  BeneficiaryRegID = :benRegId AND nikshayId IS NULL
            """, nativeQuery = true)
    int markHeld(@Param("benRegId") Long benRegId, @Param("reason") String reason);

    /**
     * FAILED — counts an attempt; nextRetryAt only for transient reasons.
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE i_beneficiarydetails
            SET    nikshaySyncStatus = 'FAILED', nikshaySyncFailureReason = :reason,
                   nikshayAttemptCount = COALESCE(nikshayAttemptCount, 0) + 1,
                   nikshayNextRetryAt = :nextRetryAt, LastModDate = NOW()
            WHERE  BeneficiaryRegID = :benRegId AND nikshayId IS NULL
            """, nativeQuery = true)
    int markFailed(@Param("benRegId") Long benRegId,
                   @Param("reason") String reason,
                   @Param("nextRetryAt") Timestamp nextRetryAt);


    // ------------------------------------------------------------------ Q4 bucket-level

    /**
     * Whole device bucket: no device / login rejected / service not enabled. No retry, no attempt increment.
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE i_beneficiarydetails
            SET    nikshaySyncStatus = 'FAILED', nikshaySyncFailureReason = :reason,
                   nikshayNextRetryAt = NULL, LastModDate = NOW()
            WHERE  BeneficiaryRegID IN (:benRegIds) AND nikshayId IS NULL
            """, nativeQuery = true)
    int markBucketFailed(@Param("benRegIds") List<Long> benRegIds, @Param("reason") String reason);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE i_beneficiarydetails
            SET    nikshaySyncStatus = 'PENDING', nikshaySyncFailureReason = NULL,
                   nikshayNextRetryAt = NULL, nikshayAttemptCount = 0, LastModDate = NOW()
            WHERE  BeneficiaryRegID IN (:benRegIds) AND nikshayId IS NULL
            """, nativeQuery = true)
    int resetForReprocess(@Param("benRegIds") List<Long> benRegIds);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE i_beneficiarydetails b
            JOIN   t_household_member hm ON hm.ben_reg_id = b.BeneficiaryRegID
            JOIN   t_household h         ON h.household_id = hm.household_id
            SET    b.nikshaySyncStatus = 'PENDING', b.nikshaySyncFailureReason = NULL,
                   b.nikshayNextRetryAt = NULL, b.nikshayAttemptCount = 0
            WHERE  h.village_id IN (:villageIds)
              AND  b.nikshayId IS NULL
              AND  b.nikshaySyncStatus = 'FAILED'
              AND  b.nikshaySyncFailureReason IN ('DEVICE_CONFIG_MISSING','DEVICE_LOGIN_FAILED','SERVICE_NOT_ENABLED')
            """, nativeQuery = true)
    int releaseConfigFailures(@Param("villageIds") List<Integer> villageIds);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE i_beneficiarydetails
            SET    nikshaySyncStatus = 'PENDING', nikshaySyncFailureReason = NULL,
                   nikshayNextRetryAt = NULL, nikshayAttemptCount = 0
            WHERE  BeneficiaryRegID = :benRegId AND nikshayId IS NULL
              AND  nikshaySyncFailureReason IN ('VALIDATION_FAILED','NIKSHAY_VALIDATION_ERROR','MAX_ATTEMPTS')
            """, nativeQuery = true)
    int releaseAfterDataFix(@Param("benRegId") Long benRegId);

    // ------------------------------------------------------------------ UI reads
    @Query(value = """
            SELECT b.nikshaySyncFailureReason AS reason, COUNT(*) AS cnt
            FROM   i_beneficiarydetails b
            WHERE  b.nikshayId IS NULL AND b.nikshaySyncFailureReason IS NOT NULL
            GROUP BY b.nikshaySyncFailureReason ORDER BY cnt DESC
            """, nativeQuery = true)
    List<Object[]> countFailuresByReason();
}