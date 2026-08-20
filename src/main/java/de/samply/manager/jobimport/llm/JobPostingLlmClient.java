package de.samply.manager.jobimport.llm;

import de.samply.manager.dto.JobPostingExtraction;

/**
 * Asks an LLM to extract fields from a job posting's text, in the shape a
 * given {@link LlmExtractionSpec} demands. Implementations are swapped via
 * job-posting.parser.provider - see OllamaJobPostingLlmClient and
 * AzureJobPostingLlmClient.
 */
public interface JobPostingLlmClient {

    <T> T extract(String postingText, LlmExtractionSpec<T> spec);

    /** The whole-posting overview behind /api/posting/overview. */
    default JobPostingExtraction extract(String postingText) {
        return extract(postingText, JobPostingLlmSpecs.OVERVIEW);
    }
}
