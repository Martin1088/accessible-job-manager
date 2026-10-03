package de.ajm.manager.company.fieldsuggestion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CompanySuggestion(String name, String website) {
}
