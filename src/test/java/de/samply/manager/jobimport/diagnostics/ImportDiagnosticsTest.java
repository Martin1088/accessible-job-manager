package de.samply.manager.jobimport.diagnostics;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The line format is the contract: these lines are read with
 * {@code cut -d'|' -f1,2 | sort | uniq -c}, so field order and separator are
 * not cosmetic - a reordered field silently changes what every existing
 * aggregation command reports.
 */
class ImportDiagnosticsTest {

    private final ImportDiagnostics diagnostics = new ImportDiagnostics();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private ch.qos.logback.classic.Logger logger;
    private Level originalLevel;
    private List<Appender<ILoggingEvent>> originalAppenders;

    /**
     * The real appenders are detached for the duration: another test class in
     * the same JVM may have loaded logback-spring.xml, and turning this logger
     * on with the file appender still attached would leave a logs/ directory
     * behind on every test run.
     */
    @BeforeEach
    void captureDiagnosticsLog() {
        logger = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("ajm.diagnostics");
        originalLevel = logger.getLevel();
        originalAppenders = new ArrayList<>();
        logger.iteratorForAppenders().forEachRemaining(originalAppenders::add);
        originalAppenders.forEach(logger::detachAppender);

        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void restore() {
        logger.detachAppender(appender);
        originalAppenders.forEach(logger::addAppender);
        logger.setLevel(originalLevel);
    }

    @Test
    void writesHostCategoryStatusMissingFieldsAndUrlInThatOrder() {
        diagnostics.record("https://jobs.example.com/posting/42",
                FailureCategory.FIELDS_MISSING, null, List.of("title", "location"));

        assertThat(line()).isEqualTo(
                "jobs.example.com|FIELDS_MISSING||title,location|https://jobs.example.com/posting/42");
    }

    @Test
    void leavesTheStatusFieldEmptyForNonHttpFailures() {
        diagnostics.record("https://jobs.example.com/x", FailureCategory.TIMEOUT, null);

        assertThat(line().split("\\|", -1)[2]).isEmpty();
    }

    @Test
    void keepsTheUpstreamStatusWhereTheReportExpectsIt() {
        diagnostics.record("https://jobs.example.com/x", FailureCategory.BOT_BLOCKED, 403);

        assertThat(line().split("\\|", -1)[2]).isEqualTo("403");
    }

    /**
     * A diagnostics line describes a request; it must never be able to break
     * one. An unparseable URL therefore yields an empty host rather than an
     * exception.
     */
    @Test
    void anUnparseableUrlStillProducesALine() {
        diagnostics.record("not a url", FailureCategory.INVALID_URL, null);

        assertThat(line()).startsWith("|INVALID_URL||");
    }

    @Test
    void writesNothingWhileTheLoggerIsOff() {
        logger.setLevel(Level.OFF);

        diagnostics.record("https://jobs.example.com/x", FailureCategory.OK, null);

        assertThat(appender.list).isEmpty();
    }

    private String line() {
        assertThat(appender.list).hasSize(1);
        return appender.list.getFirst().getFormattedMessage();
    }
}
