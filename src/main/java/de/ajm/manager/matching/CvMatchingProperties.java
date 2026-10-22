package de.ajm.manager.matching;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;
import java.util.Set;

/**
 * The model that holds a CV against a job posting ({@code cv-matching} in
 * {@code application.yml}).
 *
 * <p>Every value falls back to the posting parser's ({@code job-posting.parser}) through
 * the placeholders in {@code application.yml}, so a deployment that sets nothing compares
 * with the model it already runs. Setting any one value - typically only the model -
 * gives the matching its own: a 3B model that reads four fields off a posting well is
 * usually too small to judge a CV requirement by requirement. The provider can differ
 * too, Ollama for the parser and Azure OpenAI for the matching or the other way round.
 *
 * <p>Read timeout, output tokens and context window have their own defaults rather than
 * the parser's: a result runs to thousands of tokens, not three hundred, and a posting
 * plus a CV does not fit the context Ollama gives a model by default.
 *
 * <p>Configuration defects fail startup, the same way {@code LegalProperties} does.
 */
@ConfigurationProperties(prefix = "cv-matching")
public record CvMatchingProperties(
        String provider,
        String ollamaUrl,
        String model,
        Integer connectTimeoutSeconds,
        Integer readTimeoutSeconds,
        Integer maxOutputTokens,
        Integer contextWindow,
        Azure azure) {

    public static final String OLLAMA = "ollama";
    public static final String AZURE = "azure";
    private static final Set<String> PROVIDERS = Set.of(OLLAMA, AZURE);

    public CvMatchingProperties {
        provider = provider == null || provider.isBlank() ? OLLAMA : provider.trim().toLowerCase(Locale.ROOT);
        connectTimeoutSeconds = orDefault(connectTimeoutSeconds, 5);
        readTimeoutSeconds = orDefault(readTimeoutSeconds, 300);
        maxOutputTokens = orDefault(maxOutputTokens, 4000);
        contextWindow = orDefault(contextWindow, 16384);
        azure = azure == null ? new Azure(null, null, null, null) : azure;

        validate(provider, ollamaUrl, model, connectTimeoutSeconds, readTimeoutSeconds, maxOutputTokens,
                contextWindow, azure);
    }

    public boolean isAzure() {
        return AZURE.equals(provider);
    }

    /** What the startup log names: enough to tell which model answers, never a secret. */
    public String describe() {
        return isAzure()
                ? "azure deployment '" + azure.deployment() + "' at " + azure.endpoint()
                : "ollama model '" + model + "' at " + ollamaUrl + " (context " + contextWindow + " tokens)";
    }

    public record Azure(String endpoint, String deployment, String apiVersion, String apiKey) {

        /** The key is masked, so a logged or actuator-exposed properties object does not leak it. */
        @Override
        public String toString() {
            return "Azure[endpoint=" + endpoint + ", deployment=" + deployment + ", apiVersion=" + apiVersion
                    + ", apiKey=" + (apiKey == null || apiKey.isBlank() ? "<unset>" : "****") + "]";
        }
    }

    private static int orDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private static void validate(String provider, String ollamaUrl, String model, int connectTimeoutSeconds,
                                 int readTimeoutSeconds, int maxOutputTokens, int contextWindow, Azure azure) {
        if (!PROVIDERS.contains(provider)) {
            throw new IllegalStateException("cv-matching.provider is '" + provider
                    + "'; it has to be 'ollama' or 'azure'.");
        }
        if (connectTimeoutSeconds <= 0 || readTimeoutSeconds <= 0 || maxOutputTokens <= 0) {
            throw new IllegalStateException("cv-matching timeouts and max-output-tokens have to be positive.");
        }
        if (contextWindow < 0) {
            throw new IllegalStateException("cv-matching.context-window cannot be negative; 0 leaves the model's default.");
        }
        if (OLLAMA.equals(provider) && (blank(ollamaUrl) || blank(model))) {
            throw new IllegalStateException("cv-matching.provider is ollama, but ollama-url or model is empty."
                    + " Both fall back to job-posting.parser - set them there or under cv-matching.");
        }
        if (AZURE.equals(provider) && (blank(azure.endpoint()) || blank(azure.deployment()) || blank(azure.apiKey()))) {
            throw new IllegalStateException("cv-matching.provider is azure, but the azure endpoint, deployment"
                    + " or api-key is empty. They fall back to job-posting.parser.azure - set them there or"
                    + " under cv-matching.azure.");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
