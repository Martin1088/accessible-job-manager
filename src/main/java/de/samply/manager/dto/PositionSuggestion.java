package de.samply.manager.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.samply.manager.types.Gender;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PositionSuggestion(String title, String employmentType, Gender contactGender,
                                 String contactTitle, String contactLastName, String email) {
}
