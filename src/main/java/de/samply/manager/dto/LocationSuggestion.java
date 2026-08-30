package de.samply.manager.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LocationSuggestion(String street, String city, String postcode, String country) {
}
