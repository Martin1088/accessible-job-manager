package de.ajm.manager.company.fieldsuggestion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.ajm.manager.types.Gender;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PositionSuggestion(String title, String employmentType, Gender contactGender,
                                 String contactTitle, String contactLastName, String email) {
}
