import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CoverLetterSender, CoverLetterTemplate } from '../model/cover-letter';
import { DocumentLanguage } from '../model/document';

/**
 * The HTML cover letter provider. Every rendering goes through the server: the text
 * preview and the PDF come from the same assembled letter, so what the user proofreads
 * is what gets printed.
 */
@Injectable({ providedIn: 'root' })
export class CoverLetterService {

  private readonly apiUrl = '/api/html/cover-letter';
  private readonly http = inject(HttpClient);

  /** Skeleton blocks and the localized closing formula to start editing from. */
  defaultTemplate(sender: CoverLetterSender, language: DocumentLanguage): Observable<CoverLetterTemplate> {
    return this.http.post<CoverLetterTemplate>(`${this.apiUrl}/template/default`, sender, {
      params: new HttpParams().set('language', language),
    });
  }

  /** The linearized letter, for reading front to back before printing. */
  renderText(applicationId: number, template: CoverLetterTemplate): Observable<string> {
    return this.http.post(`${this.apiUrl}/${applicationId}/render`, template, {
      params: new HttpParams().set('format', 'text'),
      responseType: 'text',
    });
  }

  renderPdf(applicationId: number, template: CoverLetterTemplate): Observable<HttpResponse<Blob>> {
    return this.http.post(`${this.apiUrl}/${applicationId}/render`, template, {
      params: new HttpParams().set('format', 'pdf'),
      responseType: 'blob',
      observe: 'response',
    });
  }
}
