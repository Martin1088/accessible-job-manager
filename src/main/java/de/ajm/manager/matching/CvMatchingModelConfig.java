package de.ajm.manager.matching;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ajm.manager.jobimport.llm.AzureJobPostingLlmClient;
import de.ajm.manager.jobimport.llm.JobPostingLlmClient;
import de.ajm.manager.jobimport.llm.OllamaJobPostingLlmClient;
import de.ajm.manager.jobimport.llm.OpenAiJobPostingLlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the matching's own model client from {@code cv-matching}. The two client classes
 * are the posting parser's - only their settings differ - so a provider fix reaches both.
 */
@Slf4j
@Configuration
class CvMatchingModelConfig {

    @Bean
    CvMatchingModel cvMatchingModel(CvMatchingProperties props, ObjectMapper objectMapper,
                                    MessageSource messageSource) {
        JobPostingLlmClient client;
        if (props.isOpenai()) {
            client = new OpenAiJobPostingLlmClient(
                    props.openai().baseUrl(), props.openai().model(), props.openai().apiKey(),
                    props.connectTimeoutSeconds(), props.readTimeoutSeconds(),
                    props.maxOutputTokens(), objectMapper, messageSource);
        } else if (props.isAzure()) {
            client = new AzureJobPostingLlmClient(
                    props.azure().endpoint(), props.azure().deployment(),
                    props.azure().apiVersion(), props.azure().apiKey(),
                    props.connectTimeoutSeconds(), props.readTimeoutSeconds(),
                    objectMapper, messageSource);
        } else {
            client = new OllamaJobPostingLlmClient(
                    props.ollamaUrl(), props.model(),
                    props.connectTimeoutSeconds(), props.readTimeoutSeconds(),
                    props.maxOutputTokens(), props.contextWindow(),
                    objectMapper, messageSource);
        }
        log.info("CV matching uses {}", props.describe());
        // Ollama compiles the schema into a grammar, so maxItems holds; Azure's strict
        // mode does not take the keyword on every API version, and an arbitrary
        // OpenAI-compatible server makes no promise about it either.
        return new CvMatchingModel(client, props.describe(), !props.isAzure() && !props.isOpenai());
    }
}
