import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { CvMatchResult, PostingSource } from '../model/cv-match';

/**
 * Holds one of the caller's CVs against a job posting, through the AI model the
 * server is configured with. The two paths mirror the posting import's
 * `overview` / `overview-text`. Which model answers is the server's
 * `cv-matching` configuration; the demo answers with a fixed example.
 */
@Injectable({ providedIn: 'root' })
export class CvMatchService {

  private readonly apiUrl = '/api/matching';
  private readonly http = inject(HttpClient);

  /** `lang` asks the model to answer in the interface language. */
  match(posting: PostingSource, cvDocumentId: string, lang: string): Observable<CvMatchResult> {
    if (posting.kind === 'url') {
      const params = new HttpParams()
        .set('url', posting.url)
        .set('cvDocumentId', cvDocumentId)
        .set('lang', lang);
      return this.http.post<CvMatchResult>(this.apiUrl, null, { params });
    }
    return this.http.post<CvMatchResult>(`${this.apiUrl}/text`, { text: posting.text, cvDocumentId, lang });
  }
}
