import { HttpErrorResponse } from '@angular/common/http';

import { describeHttpFailure } from './http-error';

/**
 * The distinction these tests protect is the one that cost a debugging session:
 * a backend that is not running and a backend that answered with a reason both
 * used to reduce to an empty message, and so to the same "check the URL and try
 * again" sentence - which is wrong advice for the first and redundant for the
 * second.
 */
describe('describeHttpFailure', () => {

  function error(init: { status: number; error?: unknown }): HttpErrorResponse {
    return new HttpErrorResponse({
      status: init.status,
      statusText: 'x',
      error: init.error,
      url: '/api/posting/overview',
    });
  }

  it('classifies a request that never reached the server as offline', () => {
    // Angular reports a refused/dropped connection as status 0 with a
    // ProgressEvent body - there is no server reason to show.
    const failure = describeHttpFailure(error({ status: 0, error: new ProgressEvent('error') }));

    expect(failure.kind).toBe('offline');
    expect(failure.status).toBe(0);
    expect(failure.message).toBe('');
  });

  it('passes through the reason from this app\'s error contract', () => {
    const failure = describeHttpFailure(error({
      status: 502,
      error: { status: 502, error: 'Bad Gateway', message: 'This site does not allow automated access (403).' },
    }));

    expect(failure.kind).toBe('reported');
    expect(failure.message).toBe('This site does not allow automated access (403).');
    expect(failure.status).toBe(502);
  });

  it('reports a server error that carried no reason, keeping the status', () => {
    // Spring's own error path, reached when an exception escapes
    // GlobalExceptionHandler, has no `message` field.
    const failure = describeHttpFailure(error({
      status: 500,
      error: { timestamp: '2026-09-04T00:00:00Z', status: 500, error: 'Internal Server Error' },
    }));

    expect(failure.kind).toBe('server');
    expect(failure.message).toBe('');
    expect(failure.status).toBe(500);
  });

  it('treats an expired login as its own case rather than an endpoint failure', () => {
    const failure = describeHttpFailure(error({ status: 401, error: { message: 'Unauthorized' } }));

    expect(failure.kind).toBe('unauthenticated');
  });

  it('ignores an HTML body rather than showing a page as a sentence', () => {
    const failure = describeHttpFailure(error({ status: 504, error: '<html><body>Gateway Timeout</body></html>' }));

    expect(failure.kind).toBe('server');
    expect(failure.message).toBe('');
  });

  it('accepts a short plain-text body as the reason', () => {
    const failure = describeHttpFailure(error({ status: 413, error: 'Request entity too large' }));

    expect(failure.kind).toBe('reported');
    expect(failure.message).toBe('Request entity too large');
  });

  it('takes the caller-supplied reason for a blob response body', () => {
    // A responseType:'blob' request cannot be read synchronously, so the caller
    // reads it and hands the sentence in.
    const failure = describeHttpFailure(error({ status: 502, error: new Blob() }), 'Gotenberg unavailable');

    expect(failure.kind).toBe('reported');
    expect(failure.message).toBe('Gotenberg unavailable');
  });

  it('stays offline even when a blob reason was supplied', () => {
    // Reading an empty error blob yields '' - but status 0 is decided by the
    // absence of an exchange, not by the body.
    const failure = describeHttpFailure(error({ status: 0, error: new Blob() }), '');

    expect(failure.kind).toBe('offline');
  });
});
