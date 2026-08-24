package de.samply.manager.exception;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.context.MessageSource;
import java.util.Locale;

import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Central mapping of exceptions to HTTP responses, kept in the same
 * {status, error, message} shape CustomErrorController already returns for
 * container-level errors, so the frontend has one response format to parse.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException ex) {
        return body(ex.getStatus(), ex.getMessage() != null ? ex.getMessage() : ex.getStatus().getReasonPhrase());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return body(status, ex.getReason() != null ? ex.getReason() : status.getReasonPhrase());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(EntityNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * A denied {@code @PreAuthorize} check. Method security throws inside the
     * controller invocation, so the exception leaves the DispatcherServlet and is
     * offered to this advice before Spring Security's ExceptionTranslationFilter
     * ever sees it - and {@link #handleUnexpected} would answer 500 for what is a
     * 403. The filter only gets its turn when nothing here claims the exception.
     * <p>
     * 403 rather than a challenge to log in: the filter chain already requires
     * authentication for every {@code /api/**} path, so a request that reaches a
     * controller at all carries an authenticated principal.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return body(HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN.getReasonPhrase());
    }

    /**
     * A request parameter that will not convert - an unknown enum name, a
     * non-numeric page - is the caller's mistake, not a server fault; without
     * this it would fall through to the catch-all below and answer 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return body(HttpStatus.BAD_REQUEST,
                messageSource.getMessage("error.request.parameterInvalid", new Object[]{ex.getName()}, Locale.ROOT));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR,
                messageSource.getMessage("error.request.unexpected", null, Locale.ROOT));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + " " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return body(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * The content type is set explicitly rather than left to content negotiation. A
     * request that accepts only a file - the export endpoints are fetched with
     * {@code Accept: text/csv} or the spreadsheet type - excludes JSON, so negotiating
     * this body would fail with {@code HttpMediaTypeNotAcceptableException}, the
     * original exception would be rethrown, and the caller would get a bodyless 500
     * instead of the error contract. Presetting a concrete type makes Spring write the
     * body as that type and skip the Accept and {@code produces} checks altogether.
     */
    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "status", status.value(),
                        "error", status.getReasonPhrase(),
                        "message", message != null ? message : ""
                ));
    }
}
