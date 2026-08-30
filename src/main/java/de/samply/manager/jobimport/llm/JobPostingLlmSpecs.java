package de.samply.manager.jobimport.llm;

import de.samply.manager.dto.ApplicationMethodSuggestion;
import de.samply.manager.dto.CompanySuggestion;
import de.samply.manager.dto.JobPostingExtraction;
import de.samply.manager.dto.LocationSuggestion;
import de.samply.manager.dto.PositionSuggestion;

import java.util.Map;

import static de.samply.manager.jobimport.llm.LlmSchema.nullableEnum;
import static de.samply.manager.jobimport.llm.LlmSchema.nullableString;

/**
 * The extraction contracts this application asks for.
 *
 * <p>Each suggestion spec covers one group of form fields rather than the
 * whole form, because the company form requests them section by section - and
 * a narrower prompt also keeps a small local model (the default is a 3B one)
 * on topic.
 */
public final class JobPostingLlmSpecs {

    /**
     * Shared preamble. The "never guess" instruction is the load-bearing part:
     * these values land in a form the user is about to accept, so a confident
     * invention costs more than an empty field.
     */
    private static final String COMMON =
            "You extract facts from a job posting. Only fill a field if the posting states it. " +
            "Use null for anything the posting does not state - never guess and never infer. " +
            "Keep the source language. Each field is a short value, never a sentence or paragraph.";

    /**
     * The pre-existing whole-posting overview behind /api/posting/overview.
     * Its prompt is kept verbatim so extending the client does not quietly
     * change what that endpoint returns.
     */
    public static final LlmExtractionSpec<JobPostingExtraction> OVERVIEW = new LlmExtractionSpec<>(
            "job_posting_extraction",
            JobPostingLlmPrompt.SYSTEM_PROMPT,
            Map.of("title", nullableString(),
                   "company", nullableString(),
                   "location", nullableString(),
                   "employmentType", nullableString()),
            JobPostingExtraction.class);

    public static final LlmExtractionSpec<CompanySuggestion> COMPANY = new LlmExtractionSpec<>(
            "company_suggestion",
            COMMON + " Extract the employer itself. 'name' is the employer's name as it would be "
            + "written on a letter - not a slogan, department, address, or job title. "
            + "'website' is the employer's own site. The job board hosting this posting and the "
            + "application form are not the employer's website; use null rather than either.",
            Map.of("name", nullableString(),
                   "website", nullableString()),
            CompanySuggestion.class);

    public static final LlmExtractionSpec<LocationSuggestion> LOCATION = new LlmExtractionSpec<>(
            "location_suggestion",
            COMMON + " Extract the work location of this job - the site the person would actually "
            + "report to, which is not always the employer's headquarters. Split the address into "
            + "its parts. If a part is not stated, use null for it rather than repeating the whole "
            + "address or copying another part into it. 'street' includes the house number.",
            Map.of("street", nullableString(),
                   "city", nullableString(),
                   "postcode", nullableString(),
                   "country", nullableString()),
            LocationSuggestion.class);

    public static final LlmExtractionSpec<PositionSuggestion> POSITION = new LlmExtractionSpec<>(
            "position_suggestion",
            COMMON + " Extract the role and the person applications should be addressed to. "
            + "'title' is the job title. 'contactLastName' is a person's surname only - never a "
            + "department, team, or company name, and null when the posting names no person. "
            + "'contactTitle' is an academic or honorific title written before the surname, such as "
            + "'Dr.' or 'Prof.'; null if there is none. 'contactGender' comes from the salutation "
            + "used for that person: German 'Frau' or English 'Ms.' means FEMALE, German 'Herr' or "
            + "English 'Mr.' means MALE; use null when no such salutation is used. "
            + "'email' is the address applications should be sent to.",
            Map.of("title", nullableString(),
                   "employmentType", nullableString(),
                   "contactGender", nullableEnum("FEMALE", "MALE", "DIVERSE"),
                   "contactTitle", nullableString(),
                   "contactLastName", nullableString(),
                   "email", nullableString()),
            PositionSuggestion.class);

    /**
     * Answers "which way do I go to apply?".
     *
     * <p>This is the one spec whose input is
     * {@code JobPostingParserService.postingTextWithLinks} rather than plain
     * visible text: an apply button's destination lives in an href, which the
     * page's text does not contain. Handing over only the text would leave the
     * model to invent a URL, so it is given the page's links and told to copy
     * one verbatim.
     *
     * <p>The longer field cap exists because a real application URL routinely
     * carries tracking parameters that push it past the default 200 chars, and
     * a truncated URL is worse than none at all.
     */
    public static final LlmExtractionSpec<ApplicationMethodSuggestion> APPLICATION_METHOD = new LlmExtractionSpec<>(
            "application_method_suggestion",
            "Decide how an applicant is meant to send their application for this job posting. "
            + "Answer EMAIL if the posting asks for the application by email, WEB_FORM if it points "
            + "to an application form or an apply button or link, and UNKNOWN if the posting does "
            + "not say. Follow what the posting instructs, not merely what appears on the page: an "
            + "address given for questions is not an application address, and a generic 'jobs' or "
            + "'careers' page is not this posting's application form. "
            + "For EMAIL fill 'email' and leave 'applicationUrl' null. For WEB_FORM fill "
            + "'applicationUrl' and leave 'email' null. For UNKNOWN leave both null. "
            + "'applicationUrl' must be copied character for character from the links listed in the "
            + "text; never invent, shorten, or complete a URL.",
            Map.of("method", nullableEnum("EMAIL", "WEB_FORM", "UNKNOWN"),
                   "email", nullableString(),
                   "applicationUrl", nullableString()),
            ApplicationMethodSuggestion.class,
            2000);

    private JobPostingLlmSpecs() {
    }
}
