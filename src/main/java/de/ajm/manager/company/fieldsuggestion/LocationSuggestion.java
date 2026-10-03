package de.ajm.manager.company.fieldsuggestion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LocationSuggestion(String street, String city, String postcode, String country) {
}
