package com.iemr.mmu.data.nikshay.entity;

import com.google.gson.annotations.Expose;
import com.iemr.mmu.utils.mapper.OutputMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Data;

import java.sql.Timestamp;

/**
 * A village served by a device (m_nikshay_device_configuration). One device serves
 * many villages; a village has at most one active device of each type (NAAT,
 * XRAY). All location IDs are
 * Ni-kshay IDs (m_nikshay_*). The job joins the beneficiary's
 * i_beneficiaryaddress.PermVillageId with village_id to find the device login.
 */
@Data
@Entity
@Table(name = "m_nikshay_device_village_mapping")
public class NikshayDeviceVillageMapping {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Expose
	@Column(name = "id")
	private Integer id;

	@Expose
	@Column(name = "device_detail_id")
	private Integer deviceDetailId;

	@Expose
	@Column(name = "state_id")
	private Integer stateId;

	@Expose
	@Column(name = "district_id")
	private Integer districtId;

	@Expose
	@Column(name = "tbu_id")
	private Integer tbuId;

	@Expose
	@Column(name = "facility_id")
	private Integer facilityId;

	@Expose
	@Column(name = "village_id")
	private Integer villageId;

	@Expose
	@Column(name = "nikshay_village_id")
	private Integer nikshayVillageId;

	@Expose
	@Column(name = "is_active")
	private Boolean isActive = true;

	@Expose
	@Column(name = "created_by")
	private String createdBy;

	@Expose
	@Column(name = "created_at", insertable = false, updatable = false)
	private Timestamp createdAt;

	@Expose
	@Column(name = "updated_by")
	private String updatedBy;

	@Expose
	@Column(name = "updated_at", insertable = false, updatable = false)
	private Timestamp updatedAt;

	@Expose
	@Column(name = "more_data", columnDefinition = "json")
	private String moreData;

	@Transient
	private OutputMapper outputMapper = new OutputMapper();

	@Override
	public String toString() {
		return outputMapper.gson().toJson(this);
	}
}
