package com.iemr.mmu.repo.nikshay;


import com.iemr.mmu.data.nikshay.entity.NikshayDeviceVillageMapping;
import com.iemr.mmu.data.nikshay.entity.NikshayEpisode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface NikshayEpisodeRepository extends JpaRepository<NikshayEpisode, Long> {

    Optional<NikshayEpisode> findByBenRegIdAndIsCurrentTrue(Long benRegId);

    List<NikshayEpisode> findByBenRegIdOrderByEpisodeNoDesc(Long benRegId);

    Optional<NikshayEpisode> findByNikshayEpisodeId(String nikshayEpisodeId);

    @Query("SELECT COALESCE(MAX(e.episodeNo), 0) FROM NikshayEpisode e WHERE e.benRegId = :benRegId")
    int maxEpisodeNo(@Param("benRegId") Long benRegId);

    @Modifying
    @Transactional
    @Query("UPDATE NikshayEpisode e SET e.isCurrent = false WHERE e.benRegId = :benRegId AND e.isCurrent = true")
    int clearCurrent(@Param("benRegId") Long benRegId);

    @Query("""
            SELECT e FROM NikshayEpisode e
            WHERE e.isCurrent = true AND e.linkStatus <> 'LINKED_NO_ACTION'
              AND e.benRegId IN (:benRegIds)
            """)
    List<NikshayEpisode> findCurrentPushable(@Param("benRegIds") List<Long> benRegIds);

    @Query("""
            select m from NikshayDeviceVillageMapping m, NikshayDeviceConfiguration d
            where  m.deviceDetailId = d.id
              and  m.villageId in :villageIds
              and  m.isActive = true and d.isActive = true
              and  d.deviceType = :deviceType
            order by m.villageId, m.id
            """)
    List<NikshayDeviceVillageMapping> findActiveForVillages(@Param("villageIds") Collection<Integer> villageIds,
                                                            @Param("deviceType") String deviceType);
}