package de.samply.manager.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for API error signals that carry their own HTTP status, so
 * controllers/services can throw a named exception instead of constructing
 * a {@link org.springframework.web.server.ResponseStatusException} inline.
 * Handled centrally by {@link GlobalExceptionHandler}.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Integer upstreamStatus;

    protected ApiException(HttpStatus status, String message) {
        this(status, message, null, null);
    }

    protected ApiException(HttpStatus status, String message, Integer upstreamStatus, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.upstreamStatus = upstreamStatus;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /**
     * The status the <em>remote</em> host answered with, where that is a
     * different thing from the status we answer our own caller with: a job
     * posting site replying 403 becomes a 502 for our API, and only this
     * field still says which. Null when the failure was not an HTTP one.
     *
     * <p>Carried so import diagnostics can tell a bot block from a rate limit
     * from a removed posting without re-parsing the user-facing message.
     */
    public Integer getUpstreamStatus() {
        return upstreamStatus;
    }

    public static final class NotFound extends ApiException {
        public NotFound() {
            super(HttpStatus.NOT_FOUND, null);
        }

        public NotFound(String message) {
            super(HttpStatus.NOT_FOUND, message);
        }
    }

    public static final class Forbidden extends ApiException {
        public Forbidden() {
            super(HttpStatus.FORBIDDEN, null);
        }

        public Forbidden(String message) {
            super(HttpStatus.FORBIDDEN, message);
        }
    }

    public static final class Conflict extends ApiException {
        public Conflict(String message) {
            super(HttpStatus.CONFLICT, message);
        }
    }

    public static final class BadRequest extends ApiException {
        public BadRequest(String message) {
            super(HttpStatus.BAD_REQUEST, message);
        }

        public BadRequest(String message, Integer upstreamStatus) {
            super(HttpStatus.BAD_REQUEST, message, upstreamStatus, null);
        }

        /**
         * Mirrors {@link BadGateway#BadGateway(String, Throwable)}: a rejection
         * whose user-facing sentence is the same for several underlying causes
         * keeps the cause, so a log still distinguishes them. A truncated upload
         * and a file that is not a PDF at all both read as "could not be read".
         */
        public BadRequest(String message, Throwable cause) {
            super(HttpStatus.BAD_REQUEST, message, null, cause);
        }
    }

    public static final class UnsupportedMediaType extends ApiException {
        public UnsupportedMediaType(String message) {
            super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
        }
    }

    public static final class Unauthorized extends ApiException {
        public Unauthorized(String message) {
            super(HttpStatus.UNAUTHORIZED, message);
        }
    }

    public static final class TooManyRequests extends ApiException {
        public TooManyRequests(String message) {
            super(HttpStatus.TOO_MANY_REQUESTS, message);
        }
    }

    public static final class BadGateway extends ApiException {
        public BadGateway(String message) {
            super(HttpStatus.BAD_GATEWAY, message);
        }

        public BadGateway(String message, Integer upstreamStatus) {
            super(HttpStatus.BAD_GATEWAY, message, upstreamStatus, null);
        }

        public BadGateway(String message, Throwable cause) {
            super(HttpStatus.BAD_GATEWAY, message, null, cause);
        }
    }

    public static final class ServiceUnavailable extends ApiException {
        public ServiceUnavailable(String message) {
            super(HttpStatus.SERVICE_UNAVAILABLE, message);
        }
    }

    public static final class InternalServerError extends ApiException {
        public InternalServerError(String message) {
            super(HttpStatus.INTERNAL_SERVER_ERROR, message);
        }
    }
}
