package de.samply.manager.jobimport.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.List;

/**
 * Writes one line per job-import attempt to the {@code ajm.diagnostics} logger,
 * so a demo run leaves behind a work list: which host needs an adapter next.
 *
 * <p>Deliberately a log and not a table. The data is disposable, arises only in
 * demo systems, and carries no personal data - so there is no entity, no
 * migration and no deletion concept to maintain. Status tracking needs no
 * storage either: fixed means it stops appearing, a regression means it appears
 * again. {@code docs/import-issues.md} holds the notes and WONTFIX decisions by
 * hand.
 *
 * <p>The logger name is its own, not the class logger, because it is switched
 * and routed independently of everything else this package logs - see
 * {@code logback-spring.xml}, where it goes to a separate appender with
 * {@code additivity="false"} so these lines do not dilute the normal log.
 *
 * <p>Line format, pipe-separated, fixed field order:
 * <pre>host|category|httpStatus|missingFields|url</pre>
 * No timestamp and no level in the pattern - both get in the way of
 * {@code cut | sort | uniq -c}, which is how this is read.
 */
@Component
public class ImportDiagnostics {

    private static final Logger DIAG = LoggerFactory.getLogger("ajm.diagnostics");

    /**
     * @param url            the attempted URL; its host becomes the first field
     * @param category       how the attempt is to be fixed, not what it looked like
     * @param httpStatus     the status the remote host answered with, or null for non-HTTP failures
     * @param missingFields  required fields that stayed empty; empty on anything but FIELDS_MISSING
     */
    public void record(String url, FailureCategory category, Integer httpStatus, Collection<String> missingFields) {
        if (!DIAG.isInfoEnabled()) {
            return;
        }
        DIAG.info("{}|{}|{}|{}|{}",
                host(url),
                category,
                httpStatus == null ? "" : httpStatus,
                String.join(",", missingFields),
                clean(url));
    }

    public void record(String url, FailureCategory category, Integer httpStatus) {
        record(url, category, httpStatus, List.of());
    }

    /**
     * The host is the field the report groups by, so an unparseable URL still
     * has to yield something rather than throwing: a diagnostics line must
     * never be able to break the request it is describing.
     */
    private String host(String url) {
        if (url == null) {
            return "";
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? "" : host;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** Keeps the separator unambiguous even if a URL ever carries a literal pipe. */
    private String clean(String value) {
        return value == null ? "" : value.replace('|', '_').replace('\n', ' ').replace('\r', ' ');
    }
}
