package com.iemr.mmu.data.nikshay.dtos;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DuplicateCandidateDto {
    @JsonProperty("PatientId")
    private Long patientId;
    @JsonProperty("FirstName")
    private String firstName;
    @JsonProperty("LastName")
    private String lastName;
    @JsonProperty("Gender")
    private String gender;
    @JsonProperty("PrimaryPhoneNumber")
    private String primaryPhoneNumber;
    @JsonProperty("Age")
    private Integer age;
    @JsonProperty("DateOfBirth")
    private String dateOfBirth;
    @JsonProperty("Stage")
    private String stage;                     // "Presumptive (Open)" …
    @JsonProperty("IsPatientOutcomeAssigned")
    private Boolean isPatientOutcomeAssigned;
    @JsonProperty("OnTreatment")
    private Boolean onTreatment;
}