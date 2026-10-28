package com.iemr.mmu.data.nikshay.dtos;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DuplicateCheckRequest {
    private String primaryPhone;
    private String gender;
    private String firstName;
    private String lastName;
    private Integer age;
    private String dateOfBirth;
}
