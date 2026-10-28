package com.iemr.mmu.data.nikshay.dtos;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DeviceContextDto {
    private Integer deviceConfigId;
    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String phiUserId;
    private String passwordEncrypted;
    private Integer nikshayFacilityId;
    private Integer villageId;
    private Integer nikshayStateId, nikshayDistrictId, nikshayTbuId, nikshayVillageId;

    public List<Integer> residenceHierarchyAll() {
        return List.of(1, nikshayStateId, nikshayDistrictId, nikshayTbuId, nikshayVillageId);
    }
}
