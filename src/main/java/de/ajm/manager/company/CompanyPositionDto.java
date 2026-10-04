package de.ajm.manager.company;

import de.ajm.manager.application.ApplicationMethod;
import de.ajm.manager.types.Gender;
import de.ajm.manager.types.Language;
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
