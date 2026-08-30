package de.samply.manager.jobimport.diagnostics;

import de.samply.manager.exception.ApiException;

import java.net.http.HttpTimeoutException;

/**
 * Why an import attempt did not produce a usable posting.
 *
 * <p>The categories are cut by <em>how the problem gets fixed</em>, not by
 * what it looks like: {@link #BOT_BLOCKED} and {@link #JS_REQUIRED} present
 * completely differently (a 403 versus a page that loads but is empty) and
 * are one entry apart here because both are answered by fetching through
 * Chromium. Grouping by symptom instead would produce a list nobody can act
 * on.
 *
 * <p>These values are aggregated out of the {@code ajm.diagnostics} log
 * ({@code cut -d'|' -f1,2 | sort | uniq -c}) to decide which host needs an
 * adapter next, so their names are effectively the report's column values -
 * renaming one breaks comparison against older log files.
 */
public enum FailureCategory {

    /** Extraction succeeded with every required field present. Counted for the success rate. */
    OK,

    /** 403, Cloudflare, DataDome. Fix: fetch through Chromium. */
    BOT_BLOCKED,

    /** Fetch succeeded but the text body is near-empty. Fix: fetch through Chromium. */
    JS_REQUIRED,

    /** No JSON-LD, no microdata, no adapter matched. Fix: write an adapter. */
    NO_STRUCTURED_DATA,

    /** The pipeline ran, but required fields stayed empty. Fix: heuristic or LLM prompt. */
    FIELDS_MISSING,

    /** The posting sits behind a login. No fix - WONTFIX. */
    LOGIN_REQUIRED,

    /** 429. Fix: back off. */
    RATE_LIMITED,

    /** Gotenberg could not render the page to PDF. Fix: depends on the upstream status logged alongside. */
    PDF_FETCH_FAILED,

    /**
     * Gotenberg itself was unreachable. Kept apart from {@link #PDF_FETCH_FAILED}
     * because this says nothing about the host - it is our own infrastructure,
     * and mixing it in would put innocent hosts on the work list.
     */
    PDF_SERVICE_UNAVAILABLE,

    /** The URL never passed validation (malformed, wrong scheme, private address). Noise. */
    INVALID_URL,

    /** Read timeout. Noise, no fix. */
    TIMEOUT,

    /** 404/410 - the posting is gone. Noise, no fix. */
    NOT_FOUND,

    /** Host did not answer at all (DNS, connection refused, redirect loop). Noise. */
    UNREACHABLE,

    /** Host answered with some other error status. */
    UPSTREAM_ERROR,

    /** Anything that escaped classification - a non-empty count here means this enum needs a new case. */
    UNKNOWN;

    /**
     * Derives the category from a failed fetch.
     *
     * <p>Reads {@link ApiException#getUpstreamStatus()} and the exception cause
     * rather than the message, because messages are localized and are written
     * for the user - matching on them would make the report break the next time
     * someone rewords {@code messages.properties}.
     */
    public static FailureCategory of(RuntimeException e) {
        if (!(e instanceof ApiException api)) {
            return UNKNOWN;
        }
        Integer upstream = api.getUpstreamStatus();
        if (upstream != null) {
            return switch (upstream) {
                case 401 -> LOGIN_REQUIRED;
                case 403 -> BOT_BLOCKED;
                case 429 -> RATE_LIMITED;
                case 404, 410 -> NOT_FOUND;
                default -> UPSTREAM_ERROR;
            };
        }
        if (hasTimeoutCause(api)) {
            return TIMEOUT;
        }
        if (api instanceof ApiException.BadRequest) {
            return INVALID_URL;
        }
        if (api instanceof ApiException.BadGateway) {
            return UNREACHABLE;
        }
        return UNKNOWN;
    }

    private static boolean hasTimeoutCause(Throwable e) {
        for (Throwable t = e.getCause(); t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
