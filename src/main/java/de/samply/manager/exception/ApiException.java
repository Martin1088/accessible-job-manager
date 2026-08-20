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

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
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
