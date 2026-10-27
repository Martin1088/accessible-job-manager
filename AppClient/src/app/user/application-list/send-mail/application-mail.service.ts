import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, forkJoin, from, map, of, switchMap } from 'rxjs';

import { CoverLetterService } from '../../../services/cover-letter.service';
import { DocumentService } from '../../../services/document.service';
import { CoverLetterEmail, CoverLetterRenderRequest } from '../../../model/cover-letter';
import { Document } from '../../../model/document';
import { filenameFrom } from '../../../core/file-download';
import { buildEml, textToHtml } from '../../../core/mail/mime-builder';
import { fittingMailto } from '../../../core/mail/mailto';
import type { TemplateOption } from '../application-list.component';

const PDF = 'application/pdf';
const NO_ATTACHMENTS: CoverLetterRenderRequest = { attachments: [] };

/** One attachment, held both as a `File` (for sharing) and as bytes (for the `.eml`). */
export interface MailAttachment {
  file: File;
  bytes: Uint8Array;
}

/** Everything one application's mail needs, in memory, ready to hand over. */
export interface PreparedMail {
  to: string;
  subject: string;
  body: string;
  shortBody: string;
  attachments: MailAttachment[];
  totalBytes: number;
}

/**
 * Assembles an application's email in the browser: the draft from either cover letter
 * provider, the letter printed as PDF, and every enclosed document - each fetched
 * through the backend proxy, since storage is encrypted per object and has no URL a
 * mail client could fetch.
 *
 * Nothing is sent from here. The prepared mail is handed to the user's own mail app
 * by one of three routes - Web Share, an `.eml` draft, or `mailto:` - and lives only
 * in memory until the panel holding it is closed.
 */
@Injectable({ providedIn: 'root' })
export class ApplicationMailService {

  private readonly http = inject(HttpClient);
  private readonly coverLetters = inject(CoverLetterService);
  private readonly documents = inject(DocumentService);

  prepare(applicationId: number, template: TemplateOption, enclosures: Document[]): Observable<PreparedMail> {
    // Enclosure names are known before anything loads; the letter's arrives with it and
    // yields to them, so every download can run at once.
    const names = new Set<string>();
    const enclosureNames = enclosures.map(doc => unique(enclosureName(doc), names));

    const letter$ = this.letter(applicationId, template).pipe(
      switchMap(response => attachment(response.body!, unique(
        filenameFrom(response.headers.get('Content-Disposition')) ?? 'cover-letter.pdf', names))));
    const documents$ = enclosures.length
      ? forkJoin(enclosures.map((doc, i) => this.documents.download(doc.id).pipe(
          switchMap(blob => attachment(blob, enclosureNames[i])))))
      : of([]);

    return forkJoin({
      draft: this.draft(applicationId, template),
      letter: letter$,
      documents: documents$,
    }).pipe(map(({ draft, letter, documents }) => {
      const attachments = [letter, ...documents];
      return {
        to: draft.to ?? '',
        subject: draft.subject,
        body: draft.body,
        shortBody: draft.shortBody,
        attachments,
        totalBytes: attachments.reduce((sum, a) => sum + a.bytes.length, 0),
      };
    }));
  }

  /** Feature-detected at runtime: Firefox desktop and Linux Chrome have no file sharing. */
  canShareFiles(mail: PreparedMail): boolean {
    try {
      return typeof navigator.canShare === 'function'
        && navigator.canShare({ files: mail.attachments.map(a => a.file) });
    } catch {
      return false;
    }
  }

  /**
   * Must be the first thing the click handler does: the share sheet needs the user
   * activation of that click, and any `await` before it can use it up.
   */
  share(mail: PreparedMail): Promise<void> {
    return navigator.share({
      files: mail.attachments.map(a => a.file),
      title: mail.subject,
      text: mail.body,
    });
  }

  eml(mail: PreparedMail, lang?: string): Blob {
    const content = buildEml({
      to: mail.to,
      subject: mail.subject,
      text: mail.body,
      html: textToHtml(mail.body, lang),
      attachments: mail.attachments.map(a => ({ name: a.file.name, type: a.file.type || PDF, bytes: a.bytes })),
    });
    return new Blob([content], { type: 'message/rfc822' });
  }

  mailto(mail: PreparedMail): { url: string; short: boolean } {
    return fittingMailto(mail.to, mail.subject, mail.body, mail.shortBody);
  }

  private draft(applicationId: number, template: TemplateOption): Observable<CoverLetterEmail> {
    return template.provider === 'HTML'
      ? this.coverLetters.renderEmail(applicationId, template.id, NO_ATTACHMENTS)
      : this.http.post<CoverLetterEmail>(`/api/word/cover-letter/${applicationId}/fill/${template.id}/email`, null);
  }

  private letter(applicationId: number, template: TemplateOption): Observable<HttpResponse<Blob>> {
    return template.provider === 'HTML'
      ? this.coverLetters.renderPdf(applicationId, template.id, NO_ATTACHMENTS)
      : this.http.post(`/api/word/cover-letter/${applicationId}/fill/${template.id}`, null,
          { responseType: 'blob', observe: 'response' });
  }
}

function attachment(blob: Blob, name: string): Observable<MailAttachment> {
  return from(blob.arrayBuffer()).pipe(map(buffer => {
    const bytes = new Uint8Array(buffer);
    return { file: new File([bytes], name, { type: blob.type || PDF }), bytes };
  }));
}

/**
 * An enclosure is named by its label - "Lebenslauf 2026" says more to the recipient
 * than whatever the upload happened to be called - with characters mail clients and
 * file systems reject removed.
 */
export function enclosureName(doc: Document): string {
  const base = (doc.label ?? '').trim()
    .replace(/[\\/:*?"<>|\u0000-\u001f]+/g, '')
    .replace(/\s+/g, '_');
  return base ? `${base}.pdf` : (doc.filename || 'document.pdf');
}

/** Two attachments with one name would overwrite each other once saved; the second gets "-2". */
function unique(name: string, taken: Set<string>): string {
  const dot = name.lastIndexOf('.');
  const stem = dot > 0 ? name.substring(0, dot) : name;
  const extension = dot > 0 ? name.substring(dot) : '';
  let candidate = name;
  for (let n = 2; taken.has(candidate.toLowerCase()); n++) {
    candidate = `${stem}-${n}${extension}`;
  }
  taken.add(candidate.toLowerCase());
  return candidate;
}
