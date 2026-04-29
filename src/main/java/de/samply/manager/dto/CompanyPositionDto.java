package de.samply.manager.dto;

import de.samply.manager.model.Gender;
import lombok.Data;

@Data
public class CompanyPositionDto {
    private Long id;
    private String title;
    private Gender contactGender;
    private String contactTitle;
    private String contactLastName;
    private String email;
    private String website;
    private String notes;
}
