package com.iemr.mmu.repo.nikshay;

import com.iemr.mmu.data.nikshay.entity.NikshayDeviceVillageMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface NikshayDeviceVillageMappingRepository extends JpaRepository<NikshayDeviceVillageMapping, Integer> {


    @Query("""
            SELECT m FROM NikshayDeviceVillageMapping m
            JOIN   NikshayDeviceConfiguration d ON d.id = m.deviceDetailId
            WHERE  m.villageId IN (:villageIds)
              AND  m.isActive = true
              AND  d.isActive = true
              AND  d.deviceType = :deviceType
            ORDER BY m.villageId, m.id
            """)
    List<NikshayDeviceVillageMapping> findActiveByVillagesAndDeviceType(@Param("villageIds") Set<Integer> villageIds,
                                                                        @Param("deviceType") String deviceType);

    List<NikshayDeviceVillageMapping> findByDeviceDetailIdAndIsActiveTrue(Integer deviceDetailId);

    @Query("SELECT m.villageId FROM NikshayDeviceVillageMapping m WHERE m.deviceDetailId = :deviceDetailId AND m.isActive = true")
    List<Integer> findVillageIdsByDevice(@Param("deviceDetailId") Integer deviceDetailId);
}

