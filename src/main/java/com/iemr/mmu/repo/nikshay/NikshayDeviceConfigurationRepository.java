package com.iemr.mmu.repo.nikshay;

import com.iemr.mmu.data.nikshay.entity.NikshayDeviceConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NikshayDeviceConfigurationRepository extends JpaRepository<NikshayDeviceConfiguration, Integer> {

    Optional<NikshayDeviceConfiguration> findByDeviceId(String deviceId);

    List<NikshayDeviceConfiguration> findByDeviceTypeAndIsActiveTrue(String deviceType);   // NAAT | XRAY

    List<NikshayDeviceConfiguration> findByIdInAndIsActiveTrue(List<Integer> ids);
}
