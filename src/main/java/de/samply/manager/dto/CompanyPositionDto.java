package de.samply.manager.dto;

import de.samply.manager.types.Gender;
import de.samply.manager.types.Language;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CompanyPositionDto {
    private Long id;
    private String title;
    private Gender contactGender;
    private String contactTitle;
    private String contactLastName;
    private Language applyLanguage;
    private String email;
    private String website;
    private String notes;
    private LocalDateTime createdAt;
}
