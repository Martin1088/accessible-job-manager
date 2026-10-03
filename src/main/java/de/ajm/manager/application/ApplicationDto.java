package de.ajm.manager.application;


import java.time.LocalDate;
import java.time.LocalDateTime;

public record ApplicationDto(
        Long id,
        Long companyPositionId,
        String positionTitle,
        String companyName,
        ApplicationStatus status,
        LocalDate appliedDate,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
