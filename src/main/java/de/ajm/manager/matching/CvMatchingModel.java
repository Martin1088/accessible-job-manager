package de.ajm.manager.matching;

import de.ajm.manager.jobimport.llm.JobPostingLlmClient;
import de.ajm.manager.jobimport.llm.LlmExtractionSpec;

/**
 * The model the CV matching talks to - its own client instance, configured from
 * {@link CvMatchingProperties}.
 *
 * <p>A wrapper rather than a second {@link JobPostingLlmClient} bean on purpose: the
 * posting parser injects that type, and a second bean of it would make that injection
 * ambiguous (or, with {@code @Primary}, quietly hand the parser the wrong one). Only this
 * package can reach the matching's client.
 */
public final class CvMatchingModel {

    private final JobPostingLlmClient client;
    private final String description;
    private final boolean enforcesListLength;

    /**
     * @param enforcesListLength whether the provider holds the answer to a schema's
     *                           {@code maxItems} - see {@link CvMatchSpec#forLanguage}
     */
    CvMatchingModel(JobPostingLlmClient client, String description, boolean enforcesListLength) {
        this.client = client;
        this.description = description;
        this.enforcesListLength = enforcesListLength;
    }

    boolean enforcesListLength() {
        return enforcesListLength;
    }

    <T> T ask(String userContent, LlmExtractionSpec<T> spec) {
        return client.extract(userContent, spec);
    }

    @Override
    public String toString() {
        return description;
    }
}
