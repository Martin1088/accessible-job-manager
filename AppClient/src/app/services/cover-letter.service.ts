import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CoverLetterEmail,
  CoverLetterRenderRequest,
  HtmlLetterTemplate,
  HtmlLetterTemplateRequest,
  LetterBlock,
} from '../model/cover-letter';
import { DocumentLanguage } from '../model/document';

/**
 * The HTML cover letter provider. Templates are stored server-side; rendering one for
 * an application produces a letter that is deliberately never persisted, because the
 * template plus the application plus the render request reproduce it at any time.
 *
 * Every rendering goes through the server: the text preview and the PDF come from the
 * same assembled letter, so what the user proofreads is what gets printed.
 */
@Injectable({ providedIn: 'root' })
export class CoverLetterService {

  private readonly apiUrl = '/api/html/cover-letter';
  private readonly http = inject(HttpClient);

  /** The caller's stored templates. */
  listTemplates(): Observable<HtmlLetterTemplate[]> {
    return this.http.get<HtmlLetterTemplate[]>(`${this.apiUrl}/template`);
  }

  getTemplate(id: string): Observable<HtmlLetterTemplate> {
    return this.http.get<HtmlLetterTemplate>(`${this.apiUrl}/template/${id}`);
  }

  /**
   * The starting text offered for a language. Read-only, so the editor can ask
   * for it whenever the letter language changes without creating a template.
   */
  suggestions(language: DocumentLanguage): Observable<LetterBlock[]> {
    return this.http.get<LetterBlock[]>(`${this.apiUrl}/template/suggestions`, {
      params: new HttpParams().set('language', language),
    });
  }

  /**
   * Stores a template. Passing no blocks stores the localized skeleton instead, so the
   * starting blocks and the closing formula come from the backend message bundle rather
   * than being duplicated in a second language file here.
   */
  createTemplate(request: Partial<HtmlLetterTemplateRequest>, language: DocumentLanguage): Observable<HtmlLetterTemplate> {
    return this.http.post<HtmlLetterTemplate>(`${this.apiUrl}/template`, request, {
      params: new HttpParams().set('language', language),
    });
  }

  updateTemplate(id: string, request: HtmlLetterTemplateRequest, language: DocumentLanguage): Observable<HtmlLetterTemplate> {
    return this.http.put<HtmlLetterTemplate>(`${this.apiUrl}/template/${id}`, request, {
      params: new HttpParams().set('language', language),
    });
  }

  deleteTemplate(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/template/${id}`);
  }

  /**
   * The template rendered against the sample recipient, for proofreading it before any
   * application exists to send it to. Same pipeline as a real render, stand-in recipient.
   */
  previewText(templateId: string, request: CoverLetterRenderRequest, language: DocumentLanguage): Observable<string> {
    return this.http.post(`${this.apiUrl}/template/${templateId}/preview`, request, {
      params: new HttpParams().set('format', 'text').set('language', language),
      responseType: 'text',
    });
  }

  previewPdf(templateId: string, request: CoverLetterRenderRequest, language: DocumentLanguage): Observable<HttpResponse<Blob>> {
    return this.http.post(`${this.apiUrl}/template/${templateId}/preview`, request, {
      params: new HttpParams().set('format', 'pdf').set('language', language),
      responseType: 'blob',
      observe: 'response',
    });
  }

  /** The linearized letter, for reading front to back before printing. */
  renderText(applicationId: number, templateId: string, request: CoverLetterRenderRequest): Observable<string> {
    return this.http.post(`${this.apiUrl}/${applicationId}/render/${templateId}`, request, {
      params: new HttpParams().set('format', 'text'),
      responseType: 'text',
    });
  }

  renderPdf(applicationId: number, templateId: string, request: CoverLetterRenderRequest): Observable<HttpResponse<Blob>> {
    return this.http.post(`${this.apiUrl}/${applicationId}/render/${templateId}`, request, {
      params: new HttpParams().set('format', 'pdf'),
      responseType: 'blob',
      observe: 'response',
    });
  }

  /**
   * The letter prepared for the user's own mail client. Nothing is sent from the
   * server; the caller turns this into a mailto: link.
   */
  renderEmail(applicationId: number, templateId: string, request: CoverLetterRenderRequest): Observable<CoverLetterEmail> {
    return this.http.post<CoverLetterEmail>(`${this.apiUrl}/${applicationId}/email/${templateId}`, request);
  }
}
