package de.ajm.manager.company.fieldsuggestion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.ajm.manager.application.ApplicationMethod;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApplicationMethodSuggestion(ApplicationMethod method, String email, String applicationUrl) {
}
