package de.samply.manager.jobimport.llm;

import de.samply.manager.exception.ApiException;
import org.springframework.context.MessageSource;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpTimeoutException;
import java.util.Locale;

/**
 * Turns a failed model call into an exception that says which failure it was.
 *
 * <p>Both providers previously collapsed every {@link RestClientException} into
 * one {@code error.llm.unavailable} and dropped the cause. That single sentence
 * covered a refused connection, a connect timeout, a read timeout and the
 * service answering 4xx/5xx - four problems with four different fixes, reported
 * identically and with nothing left to tell them apart. A read timeout against a
 * local model is the common one and the least like "unavailable": the service is
 * up and working, just slower than the client is willing to wait.
 *
 * <p>The cause is chained in every branch, which is not decoration.
 * {@code FailureCategory.of} decides {@code TIMEOUT} by walking the cause chain
 * for {@link HttpTimeoutException}, so an exception thrown without its cause
 * cannot be classified correctly however carefully it is worded. The same
 * reasoning is written out at {@code JobPostingParserService}'s own fetch
 * failure.
 */
final class LlmFailures {

    private LlmFailures() {}

    /**
     * @param readTimeoutSeconds only used to word the timeout message, so the
     *                           reader learns what limit was hit rather than
     *                           just that one was
     */
    static ApiException translate(RestClientException e, MessageSource messages, int readTimeoutSeconds) {
        if (e instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            return new ApiException.BadGateway(
                    message(messages, "error.llm.upstreamError", status), status, e);
        }
        if (hasTimeoutCause(e)) {
            return new ApiException.BadGateway(
                    message(messages, "error.llm.timeout", readTimeoutSeconds), e);
        }
        return new ApiException.BadGateway(message(messages, "error.llm.unavailable"), e);
    }

    /**
     * Spring wraps the transport failure, so the timeout is never the exception
     * handed to the caller - it is somewhere below it.
     */
    private static boolean hasTimeoutCause(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof HttpTimeoutException || t instanceof java.net.SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static String message(MessageSource messages, String key, Object... args) {
        return messages.getMessage(key, args, Locale.ROOT);
    }
}
