package de.samply.manager.dto;

import de.samply.manager.types.ApplicationMethod;
import de.samply.manager.types.Gender;
import de.samply.manager.types.Language;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CompanyPositionDto {
    private Long id;
    @NotBlank(message = "{error.company.position.title.blank}")
    private String title;
    private Gender contactGender;
    private String contactTitle;
    private String contactLastName;
    private Language applyLanguage;
    @Email(message = "{error.company.position.email.invalid}")
    private String email;
    private String website;
    private String notes;
    private ApplicationMethod applicationMethod;
    private LocalDateTime createdAt;
}
