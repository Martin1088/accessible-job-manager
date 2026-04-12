package de.samply.angulartemplate.dto;

public record CoverLetterRequest(
        String company,
        String street,
        String city,
        String position,
        String contact
) {}
