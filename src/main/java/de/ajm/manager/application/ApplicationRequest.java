package de.ajm.manager.application;


import java.time.LocalDate;

public record ApplicationRequest(
        Long companyPositionId,
        ApplicationStatus status,
        LocalDate appliedDate,
        String notes
) {}
