package de.ajm.manager.matching;

import de.ajm.manager.matching.CvMatchResult.MatchLevel;
import de.ajm.manager.matching.CvMatchResult.SkillAssessment;
import de.ajm.manager.matching.CvMatchResult.SkillCategory;
import de.ajm.manager.matching.CvMatchResult.SkillImportance;
import de.ajm.manager.matching.CvMatchResult.SkillStatus;
import de.ajm.manager.matching.CvMatchSpec.RawMatch;
import de.ajm.manager.matching.CvMatchSpec.RawSkill;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CvMatchGuardTest {

    private static final String POSTING = """
            Frontend Engineer (m/w/d)
            Sie bringen mehrjährige Erfahrung mit Angular mit und kennen
            die WCAG 2.2. Fließendes Deutsch. Kenntnisse in React sind ein Plus.""";
    private static final String CV = """
            Jane Doe - Frontend developer
            2019-2026: Angular applications for a public authority, audited against WCAG 2.1.""";
    private static final UUID CV_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 12, 0);

    private static RawSkill skill(String name, String importance, String status, String postingQuote, String cvEvidence) {
        return new RawSkill(name, "TECHNICAL", importance, status, postingQuote, cvEvidence, "Because.", "Learn it.");
    }

    private static RawMatch answer(String level, RawSkill... skills) {
        return new RawMatch("Frontend Engineer", "Aurum", "de", "en", List.of(skills), level, "A fair fit.");
    }

    private static CvMatchResult clean(RawMatch raw) {
        return CvMatchGuard.clean(raw, POSTING, CV, CV_ID, "de", NOW);
    }

    @Test
    void keepsAQuoteThatOccursInItsSourceAcrossLineBreaksAndCase() {
        CvMatchResult result = clean(answer("STRONG",
                skill("Angular", "REQUIRED", "MET",
                        "mehrjährige Erfahrung mit Angular mit und kennen die WCAG 2.2",
                        "\"Angular applications for a public authority\"")));

        SkillAssessment angular = result.skills().getFirst();
        assertThat(angular.postingQuote()).isEqualTo("mehrjährige Erfahrung mit Angular mit und kennen die WCAG 2.2");
        assertThat(angular.cvEvidence()).isEqualTo("\"Angular applications for a public authority\"");
    }

    @Test
    void dropsAQuoteTheTextDoesNotContain() {
        CvMatchResult result = clean(answer("PARTIAL",
                skill("WCAG", "REQUIRED", "PARTIAL", "Expert knowledge of WCAG 2.2", "certified accessibility expert")));

        SkillAssessment wcag = result.skills().getFirst();
        assertThat(wcag.postingQuote()).isNull();
        assertThat(wcag.cvEvidence()).isNull();
    }

    @Test
    void dropsARequirementWithAnUnknownValueOrNoNameAndKeepsTheRest() {
        CvMatchResult result = clean(answer("PARTIAL",
                skill("Angular", "REQUIRED", "MET", null, null),
                skill("Deutsch", "REQUIRED", "FULFILLED", null, null),
                new RawSkill("React", "FRAMEWORK", "NICE_TO_HAVE", "MISSING", null, null, "x", "y"),
                skill(" ", "REQUIRED", "MET", null, null),
                skill("angular", "REQUIRED", "MET", null, null)));

        assertThat(result.skills()).extracting(SkillAssessment::skill).containsExactly("Angular");
    }

    @Test
    void acceptsValuesInAnotherCase() {
        CvMatchResult result = clean(new RawMatch(null, null, null, null,
                List.of(new RawSkill("Angular", "technical", "required", "met", null, null, "r", null)), "strong", "s"));

        assertThat(result.level()).isEqualTo(MatchLevel.STRONG);
        assertThat(result.skills().getFirst().category()).isEqualTo(SkillCategory.TECHNICAL);
    }

    @Test
    void keepsSuggestionsOnlyWhereThereIsAGap() {
        CvMatchResult result = clean(answer("PARTIAL",
                skill("Angular", "REQUIRED", "MET", null, null),
                skill("React", "NICE_TO_HAVE", "MISSING", null, null)));

        assertThat(result.skills()).extracting(SkillAssessment::suggestion).containsExactly(null, "Learn it.");
    }

    @Test
    void derivesTheLevelWhenTheModelsIsUnusable() {
        RawSkill met = skill("A", "REQUIRED", "MET", null, null);
        RawSkill missing = skill("B", "REQUIRED", "MISSING", null, null);
        RawSkill optionalMissing = skill("C", "NICE_TO_HAVE", "MISSING", null, null);

        assertThat(clean(answer(null, met, optionalMissing)).level()).isEqualTo(MatchLevel.STRONG);
        assertThat(clean(answer("EXCELLENT", met, skill("A2", "REQUIRED", "MET", null, null), missing)).level())
                .isEqualTo(MatchLevel.PARTIAL);
        assertThat(clean(answer(null, met, missing, skill("B2", "REQUIRED", "MISSING", null, null))).level())
                .isEqualTo(MatchLevel.WEAK);
    }

    @Test
    void judgesAllRequirementsWhenThePostingMarksNoneRequired() {
        List<SkillAssessment> skills = List.of(
                new SkillAssessment("A", SkillCategory.TECHNICAL, SkillImportance.NICE_TO_HAVE, SkillStatus.MISSING,
                        null, null, "", null));

        assertThat(CvMatchGuard.levelOf(skills)).isEqualTo(MatchLevel.WEAK);
    }

    @Test
    void capsTheNumberOfRequirements() {
        List<RawSkill> many = new ArrayList<>();
        for (int i = 0; i < CvMatchGuard.MAX_SKILLS + 5; i++) {
            many.add(skill("Skill " + i, "REQUIRED", "MET", null, null));
        }

        CvMatchResult result = clean(new RawMatch(null, null, null, null, many, "STRONG", "s"));

        assertThat(result.skills()).hasSize(CvMatchGuard.MAX_SKILLS);
    }

    @Test
    void keepsTwoLetterLanguageCodesOnly() {
        CvMatchResult result = clean(new RawMatch(null, null, "DE", "english",
                List.of(skill("A", "REQUIRED", "MET", null, null)), "STRONG", "s"));

        assertThat(result.postingLanguage()).isEqualTo("de");
        assertThat(result.cvLanguage()).isNull();
    }

    @Test
    void carriesTheRequestNotTheModelForWhatTheServerKnows() {
        CvMatchResult result = clean(answer("STRONG", skill("A", "REQUIRED", "MET", null, null)));

        assertThat(result.cvDocumentId()).isEqualTo(CV_ID);
        assertThat(result.language()).isEqualTo("de");
        assertThat(result.generatedAt()).isEqualTo(NOW);
        assertThat(result.postingTitle()).isEqualTo("Frontend Engineer");
    }

    @Test
    void hasNothingToShowWithoutAUsableRequirement() {
        assertThat(clean(answer("STRONG"))).isNull();
        assertThat(clean(answer("STRONG", skill("A", "SOMETIMES", "MET", null, null)))).isNull();
        assertThat(clean(new RawMatch(null, null, null, null, null, "STRONG", "s"))).isNull();
        assertThat(CvMatchGuard.clean(null, POSTING, CV, CV_ID, "de", NOW)).isNull();
    }
}
