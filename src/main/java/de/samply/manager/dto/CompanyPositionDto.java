package de.samply.manager.dto;

import de.samply.manager.types.ApplicationMethod;
import de.samply.manager.types.Gender;
import de.samply.manager.types.Language;
import de.samply.manager.types.TriageState;
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
    /**
     * Read on create, ignored on update: after a position exists, its state
     * changes through the queue's accept/dismiss endpoints and nowhere else.
     * Absent on create means NEW - the advisor's pages, which file into their
     * own catalogue rather than into anyone's queue, send ACCEPTED.
     */
    private TriageState triageState;
    private LocalDateTime createdAt;
}
