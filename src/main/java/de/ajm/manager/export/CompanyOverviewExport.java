package de.ajm.manager.export;

import java.time.LocalDate;

import de.ajm.manager.application.ApplicationStatus;

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
