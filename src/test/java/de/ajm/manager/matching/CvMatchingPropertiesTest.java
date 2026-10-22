package de.ajm.manager.matching;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fallback rules live in the placeholders of the <em>main</em> {@code application.yml},
 * so this reads that file itself - the test {@code application.yml} that shadows it on the
 * classpath only repeats part of the block.
 */
class CvMatchingPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(CvMatchingProperties.class)
    static class Config {}

    private static List<PropertySource<?>> mainApplicationYml() {
        try {
            return new YamlPropertySourceLoader().load("main application.yml",
                    new FileSystemResource("src/main/resources/application.yml"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> mainApplicationYml()
                    .forEach(source -> context.getEnvironment().getPropertySources().addLast(source)))
            .withUserConfiguration(Config.class);

    @Test
    void withNothingSetItIsTheParsersModel() {
        runner.withPropertyValues("job-posting.parser.model=qwen2.5:3b", "job-posting.parser.ollama-url=http://ollama:11434")
                .run(context -> {
                    CvMatchingProperties props = context.getBean(CvMatchingProperties.class);
                    assertThat(props.provider()).isEqualTo("ollama");
                    assertThat(props.model()).isEqualTo("qwen2.5:3b");
                    assertThat(props.ollamaUrl()).isEqualTo("http://ollama:11434");
                    // Its own values rather than the parser's 120s / 300 tokens.
                    assertThat(props.readTimeoutSeconds()).isEqualTo(300);
                    assertThat(props.maxOutputTokens()).isEqualTo(4000);
                    assertThat(props.contextWindow()).isEqualTo(16384);
                });
    }

    @Test
    void anOwnModelReplacesOnlyTheModel() {
        runner.withPropertyValues("job-posting.parser.model=qwen2.5:3b", "CV_MATCHING_MODEL=qwen2.5:14b")
                .run(context -> {
                    CvMatchingProperties props = context.getBean(CvMatchingProperties.class);
                    assertThat(props.model()).isEqualTo("qwen2.5:14b");
                    assertThat(props.provider()).isEqualTo("ollama");
                });
    }

    @Test
    void canUseAzureWhileTheParserRunsOnOllama() {
        runner.withPropertyValues(
                        "job-posting.parser.provider=ollama",
                        "CV_MATCHING_LLM_PROVIDER=azure",
                        "CV_MATCHING_AZURE_OPENAI_ENDPOINT=https://example.openai.azure.com",
                        "CV_MATCHING_AZURE_OPENAI_DEPLOYMENT=gpt-4o",
                        "CV_MATCHING_AZURE_OPENAI_API_KEY=secret")
                .run(context -> {
                    CvMatchingProperties props = context.getBean(CvMatchingProperties.class);
                    assertThat(props.isAzure()).isTrue();
                    assertThat(props.azure().deployment()).isEqualTo("gpt-4o");
                    assertThat(props.azure().apiVersion()).isEqualTo("2024-08-01-preview");
                    assertThat(props.azure().toString()).doesNotContain("secret");
                    assertThat(props.describe()).doesNotContain("secret");
                });
    }

    @Test
    void inheritsTheParsersAzureSettingsWithTheProvider() {
        runner.withPropertyValues(
                        "job-posting.parser.provider=azure",
                        "job-posting.parser.azure.endpoint=https://parser.openai.azure.com",
                        "job-posting.parser.azure.deployment=gpt-4o-mini",
                        "job-posting.parser.azure.api-key=k")
                .run(context -> {
                    CvMatchingProperties props = context.getBean(CvMatchingProperties.class);
                    assertThat(props.isAzure()).isTrue();
                    assertThat(props.azure().endpoint()).isEqualTo("https://parser.openai.azure.com");
                });
    }

    @Test
    void anUnknownProviderFailsStartup() {
        runner.withPropertyValues("CV_MATCHING_LLM_PROVIDER=gemini")
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContaining("'ollama', 'azure' or 'openai'"));
    }

    @Test
    void canUseAnOpenAiCompatibleEndpoint() {
        runner.withPropertyValues(
                        "CV_MATCHING_LLM_PROVIDER=openai",
                        "CV_MATCHING_OPENAI_BASE_URL=https://api.scaleway.ai/v1",
                        "CV_MATCHING_OPENAI_MODEL=mistral-small-3.2-24b-instruct-2506",
                        "CV_MATCHING_OPENAI_API_KEY=secret")
                .run(context -> {
                    CvMatchingProperties props = context.getBean(CvMatchingProperties.class);
                    assertThat(props.isOpenai()).isTrue();
                    assertThat(props.openai().model()).isEqualTo("mistral-small-3.2-24b-instruct-2506");
                    assertThat(props.openai().toString()).doesNotContain("secret");
                    assertThat(props.describe()).doesNotContain("secret")
                            .contains("mistral-small-3.2-24b-instruct-2506");
                });
    }

    @Test
    void inheritsTheParsersOpenAiSettingsWithTheProvider() {
        runner.withPropertyValues(
                        "job-posting.parser.provider=openai",
                        "job-posting.parser.openai.base-url=https://api.scaleway.ai/v1",
                        "job-posting.parser.openai.model=llama-3.3-70b-instruct",
                        "job-posting.parser.openai.api-key=k")
                .run(context -> {
                    CvMatchingProperties props = context.getBean(CvMatchingProperties.class);
                    assertThat(props.isOpenai()).isTrue();
                    assertThat(props.openai().baseUrl()).isEqualTo("https://api.scaleway.ai/v1");
                });
    }

    @Test
    void openAiWithoutAKeyFailsStartup() {
        runner.withPropertyValues("CV_MATCHING_LLM_PROVIDER=openai",
                        "CV_MATCHING_OPENAI_BASE_URL=https://api.scaleway.ai/v1",
                        "CV_MATCHING_OPENAI_MODEL=mistral-small-3.2-24b-instruct-2506")
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContaining("api-key"));
    }

    @Test
    void azureWithoutAKeyFailsStartup() {
        runner.withPropertyValues("CV_MATCHING_LLM_PROVIDER=azure",
                        "CV_MATCHING_AZURE_OPENAI_ENDPOINT=https://example.openai.azure.com",
                        "CV_MATCHING_AZURE_OPENAI_DEPLOYMENT=gpt-4o")
                .run(context -> assertThat(context).getFailure()
                        .rootCause().hasMessageContaining("api-key"));
    }

    @Test
    void buildsTheConfiguredClient() {
        runner.withUserConfiguration(CvMatchingModelConfig.class)
                .withBean(com.fasterxml.jackson.databind.ObjectMapper.class)
                .withBean(org.springframework.context.MessageSource.class,
                        de.ajm.manager.coverletter.CoverLetterFixtures::messageSource)
                .withPropertyValues("CV_MATCHING_MODEL=qwen2.5:14b")
                .run(context -> assertThat(context.getBean(CvMatchingModel.class).toString())
                        .contains("ollama model 'qwen2.5:14b'"));
    }
}
