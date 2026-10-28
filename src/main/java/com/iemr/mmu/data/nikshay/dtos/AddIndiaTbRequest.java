package com.iemr.mmu.data.nikshay.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AddIndiaTbRequest {
    @JsonProperty("TypeOfPatient")
    @Builder.Default
    private String typeOfPatient = "IndiaTbPublic";
    @JsonProperty("Stage")
    @Builder.Default
    private String stage = "PRESUMPTIVE_OPEN";
    @JsonProperty("RegistrationDate")
    private String registrationDate;        // dd-MM-yyyy
    @JsonProperty("SelectedHierarchyId")
    private Integer selectedHierarchyId;    // device nikshay_facility_id
    @JsonProperty("FirstName")
    private String firstName;
    @JsonProperty("LastName")
    private String lastName;
    @JsonProperty("FathersName")
    private String fathersName;
    @JsonProperty("Age")
    private String age;
    @JsonProperty("Gender")
    private String gender;
    @JsonProperty("PrimaryPhone")
    private String primaryPhone;
    @JsonProperty("SecondaryPhone1")
    private String secondaryPhone1;
    @JsonProperty("SecondaryPhone2")
    private String secondaryPhone2;
    @JsonProperty("SecondaryPhone3")
    private String secondaryPhone3;
    @JsonProperty("Address")
    private String address;
    @JsonProperty("Ward")
    private String ward;
    @JsonProperty("Taluka")
    private String taluka;
    @JsonProperty("Landmark")
    private String landmark;
    @JsonProperty("ResidenceHierarchyId")
    private Integer residenceHierarchyId;   // nikshay_village_id
    @JsonProperty("ResidenceHierarchyAll")
    private List<Integer> residenceHierarchyAll; // [1,state,district,tu,village]
    @JsonProperty("Pincode")
    private String pincode;
    @JsonProperty("Area")
    @Builder.Default
    private String area = "Unknown";
    @JsonProperty("MaritalStatus")
    @Builder.Default
    private String maritalStatus = "Unknown";
    @JsonProperty("Occupation")
    @Builder.Default
    private String occupation = "Unknown";
    @JsonProperty("SocioeconomicStatus")
    @Builder.Default
    private String socioeconomicStatus = "Unknown";
    @JsonProperty("KeyPopulation")
    @Builder.Default
    private String keyPopulation = "Not Applicable";
    @JsonProperty("VaccinationDate")
    private String vaccinationDate;
    @JsonProperty("Symptom")
    @Builder.Default
    private String symptom = "Asymptomatic,"; // comma-joined, trailing comma
    @JsonProperty("HIVStatus")
    @Builder.Default
    private String hivStatus = "Unknown";
    @JsonProperty("ContactPersonPhone")
    private String contactPersonPhone;
    @JsonProperty("ContactPersonName")
    private String contactPersonName;
    @JsonProperty("ContactPersonAddress")
    private String contactPersonAddress;
    @JsonProperty("parentEpisodeId")
    private Long parentEpisodeId;            // set only for new episode
    @JsonProperty("IsPossibleDuplicate")
    @Builder.Default
    private boolean isPossibleDuplicate = false;
    @JsonProperty("Source")
    private String source;
    @JsonProperty("SourcePatientId")
    private String sourcePatientId;
    @JsonProperty("TypeOfCaseFinding")
    private String typeOfCaseFinding;        // "Active (Community screening)" | "Passive (Routine programme)"
    @JsonProperty("InformantId")
    private String informantId;
    @JsonProperty("DateOfBirth")
    private String dateOfBirth;              // dd-MM-yyyy
    @JsonProperty("FollowUpsDone")
    @Builder.Default
    private List<Object> followUpsDone = List.of();
    @JsonProperty("TbWinReferenceId")
    private String tbWinReferenceId;
    @JsonProperty("caste")
    private String caste;                    // SC | ST | Others
    @JsonProperty("healthId")
    private String healthId;
    @JsonProperty("healthAddress")
    private String healthAddress;
}
