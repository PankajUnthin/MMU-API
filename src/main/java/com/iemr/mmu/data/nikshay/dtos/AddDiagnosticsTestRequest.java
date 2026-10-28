package com.iemr.mmu.data.nikshay.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AddDiagnosticsTestRequest {
    @JsonProperty("PatientId")
    private String patientId;
    @JsonProperty("ReasonForTesting")
    private String reasonForTesting;    // "TB Screening"
    @JsonProperty("TestType")
    private String testType;            // "Chest X Ray" | "Truenat (MTB)"
    @JsonProperty("TestingFacility")
    private KeyValue testingFacility;
    @JsonProperty("SampleAvailability")
    private String sampleAvailability;  // "Absent" for CXR
    @JsonProperty("ResultAvailability")
    private String resultAvailability;  // "Present"
    @JsonProperty("ResultReportedBy")
    private String resultReportedBy;    // "TBMJA"
    @JsonProperty("FinalInterpretation")
    private String finalInterpretation;
}
