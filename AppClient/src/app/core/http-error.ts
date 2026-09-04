import { HttpErrorResponse } from '@angular/common/http';

/**
 * Why a request failed, at the granularity the user can actually act on.
 *
 * `reported` and `server` are both "the server answered with an error"; they
 * differ only in whether it said why. `offline` is the one that used to hide:
 * a request that never reached the server arrives as status 0 with no body,
 * and `err.error?.message ?? ''` turned that into an empty string - the same
 * empty string a server error without a reason produces. Both then fell
 * through to a per-endpoint fallback that told the user to check a URL which
 * was never the problem, so a stopped backend and a bad link read identically.
 */
export type HttpFailureKind = 'offline' | 'unauthenticated' | 'reported' | 'server';

export interface HttpFailure {
  kind: HttpFailureKind;
  /** The server's own sentence, when it sent one; empty otherwise. */
  message: string;
  /** The HTTP status, or 0 when the request never reached the server. */
  status: number;
}

/**
 * Classifies a failed HttpClient call.
 *
 * @param serverMessage overrides the message read from `err.error`, for the
 *        callers whose error body arrives as a Blob and so can only be read
 *        asynchronously (a `responseType: 'blob'` request).
 */
export function describeHttpFailure(err: HttpErrorResponse, serverMessage?: string): HttpFailure {
  // Status 0 is the browser saying the exchange never happened: the backend is
  // down, the connection was refused or dropped, DNS failed. There is no body
  // to read and no status to report, and no amount of retrying the URL helps.
  if (err.status === 0) {
    return { kind: 'offline', message: '', status: 0 };
  }

  const message = (serverMessage ?? messageOf(err.error)).trim();

  // A 401 means the login expired mid-session. Whatever the endpoint was doing
  // is beside the point - the answer is always to sign in again.
  if (err.status === 401) {
    return { kind: 'unauthenticated', message, status: err.status };
  }

  return { kind: message ? 'reported' : 'server', message, status: err.status };
}

/**
 * Reads the `message` out of this application's `{status, error, message}`
 * error body.
 *
 * Anything that is not that shape yields nothing rather than a stringified
 * object: a `ProgressEvent` from a network failure, Spring's own error page
 * when an exception escapes `GlobalExceptionHandler`, an HTML body from a
 * proxy. A whole HTML page is not a sentence to show a user, so a body that
 * opens a tag - or runs longer than a sentence plausibly would - is dropped
 * and the caller's own wording is used instead.
 */
function messageOf(body: unknown): string {
  if (typeof body === 'string') {
    return body.length <= 300 && !body.trimStart().startsWith('<') ? body : '';
  }
  if (body && typeof body === 'object' && 'message' in body) {
    const message = (body as { message: unknown }).message;
    return typeof message === 'string' ? message : '';
  }
  return '';
}
