package de.ajm.manager.jobimport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JobPostingExtraction(String title, String company, String location, String employmentType) {
}
