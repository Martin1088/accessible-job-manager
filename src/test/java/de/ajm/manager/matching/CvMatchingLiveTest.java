package de.ajm.manager.matching;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.ajm.manager.coverletter.CoverLetterFixtures;
import de.ajm.manager.jobimport.llm.OllamaJobPostingLlmClient;
import de.ajm.manager.matching.CvMatchResult.SkillAssessment;
import de.ajm.manager.testing.DevServices;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The matching prompt against a real model, for judging a model before configuring it:
 *
 * <pre>./gradlew test --tests "*CvMatchingLiveTest" -Dmatching.live=true -Dollama.model=qwen2.5:14b</pre>
 *
 * Opt-in twice over: without {@code -Dmatching.live} it skips, because a local model takes
 * minutes for one comparison and a normal test run must not wait on that just because
 * Ollama happens to be up; and it skips again when Ollama is unreachable. The result is
 * printed, since whether the requirements are <em>right</em> is a judgement a test cannot
 * make - it asserts only what any usable answer has.
 */
class CvMatchingLiveTest {

    private static final String POSTING = """
            Frontend Engineer Accessibility (m/w/d) - Aurum Fintech SE, Frankfurt am Main
            Ihre Aufgaben: Sie entwickeln barrierefreie Oberflächen für unser Online-Banking.
            Ihr Profil: Mehrjährige Erfahrung mit Angular und TypeScript. Sehr gute Kenntnisse
            der WCAG 2.2 und von Screenreadern. Fließendes Deutsch und gutes Englisch.
            Erfahrung mit React ist von Vorteil. Ein abgeschlossenes Informatikstudium oder eine
            vergleichbare Ausbildung.""";

    private static final String CV = """
            Jane Doe - Frontend Developer
            2019-2026 Stadtwerke Springfield: Angular and TypeScript applications for citizens,
            audited against WCAG 2.1 AA, tested with NVDA and VoiceOver.
            Education: Fachinformatikerin für Anwendungsentwicklung (IHK), 2019.
            Languages: German (native), English (C1).""";

    @Test
    void comparesACvWithAPosting() {
        assumeTrue(Boolean.getBoolean("matching.live"), "opt-in: -Dmatching.live=true");
        assumeTrue(DevServices.ollamaReachable(), "Ollama not reachable at " + DevServices.ollamaUrl());

        OllamaJobPostingLlmClient client = new OllamaJobPostingLlmClient(
                DevServices.ollamaUrl(), DevServices.ollamaModel(), 5, 600, 4000, 16384,
                new ObjectMapper(), CoverLetterFixtures.messageSource());
        long startedAt = System.nanoTime();
        CvMatchSpec.RawMatch raw = client.extract(CvMatchSpec.userContent(POSTING, CV), CvMatchSpec.forLanguage("de", true));
        long seconds = (System.nanoTime() - startedAt) / 1_000_000_000;

        CvMatchResult result = CvMatchGuard.clean(raw, POSTING, CV, UUID.randomUUID(), "de", LocalDateTime.now());

        System.out.printf("%n=== %s, %ds ===%n%s - %s%n", DevServices.ollamaModel(), seconds,
                result == null ? "(no usable result)" : result.level(), result == null ? "" : result.summary());
        if (result != null) {
            for (SkillAssessment s : result.skills()) {
                System.out.printf("  %-8s %-12s %-30s posting=%s cv=%s%n", s.status(), s.importance(), s.skill(),
                        s.postingQuote() != null, s.cvEvidence() != null);
            }
        }

        assertThat(result).as("a usable answer").isNotNull();
        assertThat(result.skills()).isNotEmpty();
        assertThat(result.summary()).isNotBlank();
    }
}
