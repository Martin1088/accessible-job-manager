package de.ajm.manager.matching;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.ajm.manager.jobimport.llm.LlmExtractionSpec;
import de.ajm.manager.matching.CvMatchResult.MatchLevel;
import de.ajm.manager.matching.CvMatchResult.SkillCategory;
import de.ajm.manager.matching.CvMatchResult.SkillImportance;
import de.ajm.manager.matching.CvMatchResult.SkillStatus;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static de.ajm.manager.jobimport.llm.LlmSchema.nullableEnum;
import static de.ajm.manager.jobimport.llm.LlmSchema.nullableString;

/**
 * What the model is asked and the shape it has to answer in.
 *
 * <p>The answer maps onto {@link RawMatch}, whose values are all strings: a model that
 * writes one enum value wrong must cost that one requirement, not the whole answer -
 * which is what mapping straight onto the enums would do. {@link CvMatchGuard} turns the
 * raw answer into a {@link CvMatchResult}.
 *
 * <p>The properties are ordered on purpose. Ollama generates fields in schema order, so
 * the requirements come first and the overall level and summary last - written after the
 * model has gone through the requirements rather than before.
 */
final class CvMatchSpec {

    /** Caps the top-level strings in the client; {@link CvMatchGuard} caps the nested ones. */
    static final int MAX_FIELD_LENGTH = 2_000;

    /**
     * How many requirements are asked for - and, where the provider enforces it, allowed.
     * A small model at temperature 0 can fall into listing the same requirement over and
     * over (one answer repeated one of five names fourteen times) until it runs into its
     * output limit and the JSON is cut off; the prompt asking for brevity does not stop
     * that, a grammar that has to close the list does.
     */
    static final int MAX_REQUIREMENTS = 12;

    private static final String PROMPT = """
            You compare a CV with a job posting, for the person who owns the CV. Be brief.
            List the requirements the posting states - skills, languages, experience, education, \
            certificates, soft skills - at most %d, the most important first, required ones before \
            optional ones, each one only once. Merge closely related requirements into one. Skip \
            duties and benefits.
            For each requirement:
            - 'skill': a short name for it, at most five words.
            - 'importance': REQUIRED, unless the posting marks it optional ("nice to have", \
            "a plus", "wünschenswert", "von Vorteil") - then NICE_TO_HAVE.
            - 'status': MET if the CV clearly shows it, PARTIAL if the CV shows something related \
            or weaker, MISSING if the CV does not show it.
            - 'postingQuote': the posting's own words for it, copied exactly - at most 15 words.
            - 'cvEvidence': the CV's own words that show it, copied exactly, at most 15 words - \
            or null if there are none.
            Never paraphrase inside a quote, never translate one, never invent one.
            - 'reasoning': one short sentence on why the status is what it is.
            - 'suggestion': for MISSING and PARTIAL, one short sentence on how to close the gap; null for MET.
            Then 'level': STRONG if every required requirement is met, PARTIAL if most are, WEAK otherwise. \
            'summary': two sentences on the overall fit.
            'postingTitle' and 'company' as the posting states them, else null. \
            'postingLanguage' and 'cvLanguage': the ISO 639-1 code of each text, for example "de".
            Write 'summary', 'reasoning' and 'suggestion' in %s. The quotes stay in the language \
            they were written in.""";

    private CvMatchSpec() {}

    /**
     * The spec for one answer language: the prompt names it, so the spec is built per request.
     *
     * @param bounded puts {@code maxItems} on the requirement list and {@code maxLength} on
     *                every text. Ollama compiles them into its grammar, so the answer has a
     *                hard ceiling below the output limit and cannot be cut off mid-JSON however
     *                a small model rambles - one ran a summary on for 7,000 characters. Azure's
     *                strict schema mode does not accept the keywords on every API version, and
     *                a rejected schema would fail every request, so there the prompt's limits
     *                have to do.
     */
    static LlmExtractionSpec<RawMatch> forLanguage(String languageTag, boolean bounded) {
        String language = Locale.forLanguageTag(languageTag).getDisplayLanguage(Locale.ENGLISH);
        return new LlmExtractionSpec<>(
                "cv_match",
                PROMPT.formatted(MAX_REQUIREMENTS, language.isBlank() ? "English" : language),
                properties(bounded),
                RawMatch.class,
                MAX_FIELD_LENGTH);
    }

    /** The posting and the CV, fenced so the model cannot mistake one for the other. */
    static String userContent(String postingText, String cvText) {
        return "JOB POSTING:\n<<<\n" + postingText + "\n>>>\n\nCV:\n<<<\n" + cvText + "\n>>>";
    }

    /**
     * The longest each text may run where the grammar enforces it. Twelve requirements at
     * these lengths stay near 10,000 characters - well inside the default 4,000 output
     * tokens - and each is still more than the prompt asks for, so a model that follows
     * the prompt never meets them.
     */
    private static final int NAME_CHARS = 60;
    private static final int QUOTE_CHARS = 160;
    private static final int SENTENCE_CHARS = 240;
    private static final int SUMMARY_CHARS = 500;
    private static final int TITLE_CHARS = 150;
    private static final int LANGUAGE_CHARS = 5;

    private static Map<String, Object> properties(boolean bounded) {
        Map<String, Object> skill = new LinkedHashMap<>();
        skill.put("skill", text(false, NAME_CHARS, bounded));
        skill.put("category", requiredEnum(SkillCategory.values()));
        skill.put("importance", requiredEnum(SkillImportance.values()));
        skill.put("status", requiredEnum(SkillStatus.values()));
        skill.put("postingQuote", text(true, QUOTE_CHARS, bounded));
        skill.put("cvEvidence", text(true, QUOTE_CHARS, bounded));
        skill.put("reasoning", text(false, SENTENCE_CHARS, bounded));
        skill.put("suggestion", text(true, SENTENCE_CHARS, bounded));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("postingTitle", text(true, TITLE_CHARS, bounded));
        properties.put("company", text(true, TITLE_CHARS, bounded));
        properties.put("postingLanguage", text(true, LANGUAGE_CHARS, bounded));
        properties.put("cvLanguage", text(true, LANGUAGE_CHARS, bounded));
        // additionalProperties and required in the nested object too: Azure's strict
        // json_schema mode refuses a schema that leaves either out at any level.
        Map<String, Object> skills = new LinkedHashMap<>();
        skills.put("type", "array");
        skills.put("items", Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", skill,
                "required", List.copyOf(skill.keySet())));
        if (bounded) {
            skills.put("maxItems", MAX_REQUIREMENTS);
        }
        properties.put("skills", skills);
        properties.put("level", nullableEnum(names(MatchLevel.values())));
        properties.put("summary", text(true, SUMMARY_CHARS, bounded));
        return properties;
    }

    private static Map<String, Object> text(boolean nullable, int maxLength, boolean bounded) {
        Map<String, Object> fragment = new LinkedHashMap<>(nullable ? nullableString() : Map.of("type", "string"));
        if (bounded) {
            fragment.put("maxLength", maxLength);
        }
        return fragment;
    }

    private static Map<String, Object> requiredEnum(Enum<?>[] values) {
        return Map.of("type", "string", "enum", List.of(names(values)));
    }

    private static String[] names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toArray(String[]::new);
    }

    /** The model's answer as written - strings only, see the class comment. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawMatch(String postingTitle, String company, String postingLanguage, String cvLanguage,
                    List<RawSkill> skills, String level, String summary) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawSkill(String skill, String category, String importance, String status, String postingQuote,
                    String cvEvidence, String reasoning, String suggestion) {}
}
