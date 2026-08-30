import { HttpBackend, HttpErrorResponse, HttpEvent, HttpRequest, HttpResponse, HttpXhrBackend } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, delay, of, throwError } from 'rxjs';

import { DemoAsset, DemoRequest, OUT_OF_SCOPE, resolve } from './demo-api';
import { DemoDb } from './demo-db';

/** Long enough that loading states and `aria-busy` are actually shown once. */
const LATENCY_MS = 120;

/**
 * Answers every `/api/*` request from the seed data, so the demo build makes no
 * backend call at all.
 *
 * This replaces `HttpBackend`, the single token every `HttpClient` call ends up
 * in - services, the seven components that call `HttpClient` directly, the route
 * guards and the two bootstrap initializers alike. Nothing is intercepted and
 * nothing is stubbed per call site: for `/api/*` no request is ever made.
 *
 * Requests that are not `/api/*` are handed to the real XHR backend. On GitHub
 * Pages the translation files under `public/i18n/` and the sample PDF are served
 * as ordinary static assets, which is what keeps the language switch working
 * here without a second implementation of the loader.
 *
 * The rule from the plan holds and is the reason this file stays short: hand out
 * data, apply trivial mutations, decide nothing. No validation, no permission
 * checks. Those live in the backend, and a second copy of them here would drift.
 */
@Injectable()
export class DemoBackend implements HttpBackend {

  private readonly assets = inject(HttpXhrBackend);
  private readonly db = inject(DemoDb);

  handle(request: HttpRequest<unknown>): Observable<HttpEvent<unknown>> {
    if (!request.url.startsWith('/api/')) return this.assets.handle(request);

    // `url` omits HttpParams; `urlWithParams` is what the server would receive.
    const [path, search] = request.urlWithParams.split('?');
    const match = resolve(request.method, path);

    if (!match) {
      return this.fail(request, 404,
        `Demo: ${request.method} ${path} ist nicht abgebildet. Das ist ein Fehler in der Demo, keine fehlende Funktion.`);
    }

    const demoRequest: DemoRequest = {
      method: request.method,
      path,
      params: match.params,
      query: new URLSearchParams(search ?? ''),
      body: request.body,
    };

    const result = match.handle(demoRequest, this.db);

    if (result === OUT_OF_SCOPE) {
      return this.fail(request, 501, 'Diese Funktion ist in der Demo nicht enthalten.');
    }

    // A handler returning `undefined` looked something up and did not find it;
    // `null` is a deliberate empty body, as DELETE returns.
    if (result === undefined) {
      return this.fail(request, 404, 'Demo: nicht gefunden.');
    }

    if (result instanceof DemoAsset) {
      return this.assets.handle(request.clone({ url: result.path, method: 'GET', body: null }));
    }

    return of(new HttpResponse({ status: 200, url: request.url, body: result })).pipe(delay(LATENCY_MS));
  }

  private fail(request: HttpRequest<unknown>, status: number, message: string): Observable<never> {
    return throwError(() => new HttpErrorResponse({
      status,
      statusText: statusText(status),
      url: request.url,
      error: { status, error: statusText(status), message },
    }));
  }
}

function statusText(status: number): string {
  return status === 404 ? 'Not Found' : 'Not Implemented';
}
