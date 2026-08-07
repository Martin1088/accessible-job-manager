package de.samply.manager.jobimport.extractor;

import de.samply.manager.dto.CompanyDto;

import java.util.Map;

/**
 * Final result of the extraction pipeline. Builds on the existing company
 * DTO chain (instead of a parallel structure), so the import can continue
 * straight into CompanyService.createCompany/updateCompany.
 *
 * provenance: per field, the tier the value came from - basis for the
 * "please review" badge in the UI on HEURISTIC/LLM.
 */
public record JobPosting(
        CompanyDto company,
        String sourceJobId,
        String postedAt,
        String deadline,
        String employmentType,
        Map<String, ConfidenceTier> provenance
) {}
