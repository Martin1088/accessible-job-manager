package de.samply.manager.dto;

import java.time.LocalDate;

import de.samply.manager.model.ApplicationStatus;

public record CompanyOverviewExport(
    String companyName,
    String locations,
    String positionTitle,
    String contact,
    String contactEmail,
    String contactWebsite,
    String positionNotes,
    ApplicationStatus applicationStatus,
    LocalDate appliedDate,
    String applicationNotes
) {} 
