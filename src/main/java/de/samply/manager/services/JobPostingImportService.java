package de.samply.manager.services;

import de.samply.manager.dto.CompanyDto;
import de.samply.manager.dto.CompanyPositionDto;
import de.samply.manager.jobimport.diagnostics.FailureCategory;
import de.samply.manager.jobimport.diagnostics.ImportDiagnostics;
import de.samply.manager.jobimport.extractor.ExtractionDebugReport;
import de.samply.manager.jobimport.extractor.JobPosting;
import de.samply.manager.jobimport.extractor.JobPostingExtractionPipeline;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Fetch -> parse -> extraction pipeline, in one place, with every attempt
 * classified for {@link ImportDiagnostics}.
 *
 * <p>The production path and the debug path used to run these three steps
 * separately in {@link de.samply.manager.controller.JobPostingParserController},
 * which meant the failure of a fetch left no trace at all: the exception went
 * straight past the controller to the client, so nothing recorded which hosts
 * block us and which pages load fine but extract to nothing. That is exactly
 * the list adapter work needs to be prioritised from, so the classification
 * belongs here, at the one point both paths share, rather than being repeated
 * per endpoint.
 */
@Service
public class JobPostingImportService {

    private final JobPostingParserService parserService;
    private final JobPostingExtractionPipeline extractionPipeline;
    private final ImportDiagnostics diagnostics;

    /**
     * Below this many characters of visible text, a 200 response is treated as
     * a page that renders its content with JavaScript rather than as a page we
     * extracted badly - a different fix (Chromium fetch, not a better adapter),
     * hence a different category.
     *
     * <p>Provisional: the right value has to come from real numbers, which is
     * what the measurement phase is for. Configurable so it can be moved
     * without a rebuild.
     */
    private final int jsRequiredThreshold;

    public JobPostingImportService(
            JobPostingParserService parserService,
            JobPostingExtractionPipeline extractionPipeline,
            ImportDiagnostics diagnostics,
            @Value("${job-posting.parser.diagnostics.js-required-threshold:400}") int jsRequiredThreshold) {
        this.parserService = parserService;
        this.extractionPipeline = extractionPipeline;
        this.diagnostics = diagnostics;
        this.jsRequiredThreshold = jsRequiredThreshold;
    }

    /** Production path: the merged result only, pipeline stops early once complete. */
    public JobPosting extract(String rawUrl, String boardHint) {
        return attempt(rawUrl,
                (page) -> extractionPipeline.run(page.document(), page.plainText(), page.url(), boardHint),
                Function.identity());
    }

    /** Debug path: every tier's raw output plus the merged result. */
    public ExtractionDebugReport extractDebug(String rawUrl, String boardHint) {
        return attempt(rawUrl,
                (page) -> extractionPipeline.runDebug(page.document(), page.plainText(), page.url(), boardHint),
                ExtractionDebugReport::merged);
    }

    /**
     * Runs one extraction and records how it went, whichever way it went.
     *
     * <p>The failure line is written before the exception is rethrown, so the
     * caller's error handling is unchanged: diagnostics observe the import,
     * they never alter it.
     */
    private <T> T attempt(String rawUrl, Function<Page, T> step, Function<T, JobPosting> mergedOf) {
        Page page;
        try {
            JobPostingParserService.FetchedPage fetched = parserService.fetchPage(rawUrl);
            Document document = Jsoup.parse(fetched.html(), fetched.url().toString());
            page = new Page(fetched.url().toString(), document, document.text());
        } catch (RuntimeException e) {
            diagnostics.record(rawUrl, FailureCategory.of(e), upstreamStatusOf(e));
            throw e;
        }

        T result = step.apply(page);
        JobPosting merged = mergedOf.apply(result);
        List<String> missing = missingRequiredFields(merged);
        diagnostics.record(page.url(), classify(page, missing), null, missing);
        return result;
    }

    /**
     * A complete result is OK even from a near-empty page: a posting whose text
     * is rendered by JavaScript but whose JSON-LD is in the served HTML extracts
     * fine, and calling that JS_REQUIRED would put a working host on the work
     * list. So completeness is asked first, and the thin-page test only decides
     * <em>why</em> an incomplete attempt failed.
     */
    private FailureCategory classify(Page page, List<String> missing) {
        if (missing.isEmpty()) {
            return FailureCategory.OK;
        }
        if (page.plainText().length() < jsRequiredThreshold) {
            return FailureCategory.JS_REQUIRED;
        }
        return hasStructuredData(page.document())
                ? FailureCategory.FIELDS_MISSING
                : FailureCategory.NO_STRUCTURED_DATA;
    }

    /**
     * Splits the two "fields are missing" cases by their fix. Markup present but
     * fields empty is a heuristic/prompt problem; no markup at all is an adapter
     * problem, and putting both in one bucket makes the work list useless.
     */
    private boolean hasStructuredData(Document document) {
        return !document.select("script[type=application/ld+json]").isEmpty()
                || !document.select("[itemtype*=JobPosting]").isEmpty();
    }

    /**
     * The same three fields {@code ConfidenceMergedPosting.isComplete()} requires
     * for a usable import. Contact is left out on purpose: it is structurally
     * often absent, so counting it would mark almost every attempt as incomplete
     * and drown the cases that can actually be fixed.
     */
    private List<String> missingRequiredFields(JobPosting posting) {
        List<String> missing = new ArrayList<>();
        CompanyDto company = posting == null ? null : posting.company();
        if (company == null) {
            return List.of("title", "companyName", "location");
        }
        if (blank(company.getName())) {
            missing.add("companyName");
        }
        if (blank(title(company))) {
            missing.add("title");
        }
        if (company.getLocations() == null || company.getLocations().isEmpty()) {
            missing.add("location");
        }
        return missing;
    }

    private String title(CompanyDto company) {
        List<CompanyPositionDto> positions = company.getPositions();
        if (positions == null || positions.isEmpty() || positions.getFirst() == null) {
            return null;
        }
        return positions.getFirst().getTitle();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private Integer upstreamStatusOf(RuntimeException e) {
        return e instanceof de.samply.manager.exception.ApiException api ? api.getUpstreamStatus() : null;
    }

    /** The fetched page in the shape all three steps downstream need it. */
    private record Page(String url, Document document, String plainText) {}
}
