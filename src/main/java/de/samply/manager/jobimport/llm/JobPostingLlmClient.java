package de.samply.manager.jobimport.llm;

import de.samply.manager.dto.JobPostingExtraction;

/**
 * Asks an LLM to extract job posting fields (title/company/location/
 * employmentType) from plain text. Implementations are swapped via
 * job-posting.parser.provider - see OllamaJobPostingLlmClient and
 * AzureJobPostingLlmClient.
 */
public interface JobPostingLlmClient {

    JobPostingExtraction extract(String postingText);
}
