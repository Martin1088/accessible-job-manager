package de.samply.manager.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.samply.manager.types.ApplicationMethod;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ApplicationMethodSuggestion(ApplicationMethod method, String email, String applicationUrl) {
}
