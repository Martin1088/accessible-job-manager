import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';

import { ApplicationMailService, PreparedMail, enclosureName } from './application-mail.service';
import { Document } from '../../../model/document';
import { TemplateOption } from '../application-list.component';

const HTML: TemplateOption = { id: 'tpl-1', label: 'DIN 5008', provider: 'HTML' };
const WORD: TemplateOption = { id: 'doc-1', label: 'Standard .docx', provider: 'WORD' };

const DRAFT = { to: 'jobs@acme.example', subject: 'Bewerbung als Entwicklerin', body: 'Langtext', shortBody: 'Kurz' };

function doc(id: string, label: string, filename = 'upload.pdf'): Document {
  return { id, label, filename, mimeType: 'application/pdf', type: 'CV', language: 'GERMAN' } as Document;
}

describe('ApplicationMailService', () => {
  let service: ApplicationMailService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withXhr()), provideHttpClientTesting()],
    });
    service = TestBed.inject(ApplicationMailService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('gathers draft, letter and every enclosure through the html provider', async () => {
    const result = firstValueFrom(service.prepare(7, HTML, [doc('d1', 'Lebenslauf 2026'), doc('d2', 'Zeugnis: Müller')]));

    http.expectOne(r => r.method === 'POST' && r.url === '/api/html/cover-letter/7/email/tpl-1').flush(DRAFT);
    const letter = http.expectOne(r => r.url === '/api/html/cover-letter/7/render/tpl-1');
    expect(letter.request.params.get('format')).toBe('pdf');
    letter.flush(new Blob(['%PDF-letter'], { type: 'application/pdf' }),
      { headers: { 'Content-Disposition': 'attachment; filename="Anschreiben_Acme.pdf"' } });
    http.expectOne('/api/documents/d1/download').flush(new Blob(['%PDF-cv'], { type: 'application/pdf' }));
    http.expectOne('/api/documents/d2/download').flush(new Blob(['%PDF-z'], { type: 'application/pdf' }));

    const mail = await result;
    expect(mail.to).toBe('jobs@acme.example');
    expect(mail.shortBody).toBe('Kurz');
    expect(mail.attachments.map(a => a.file.name))
      .toEqual(['Anschreiben_Acme.pdf', 'Lebenslauf_2026.pdf', 'Zeugnis_Müller.pdf']);
    expect(mail.totalBytes).toBe('%PDF-letter'.length + '%PDF-cv'.length + '%PDF-z'.length);
    expect(new TextDecoder().decode(mail.attachments[1].bytes)).toBe('%PDF-cv');
  });

  it('asks the word provider when a .docx template is picked', async () => {
    const result = firstValueFrom(service.prepare(7, WORD, []));

    http.expectOne('/api/word/cover-letter/7/fill/doc-1/email').flush(DRAFT);
    http.expectOne('/api/word/cover-letter/7/fill/doc-1').flush(new Blob(['%PDF']));

    const mail = await result;
    expect(mail.attachments.map(a => a.file.name)).toEqual(['cover-letter.pdf']);
  });

  it('gives a second document with the same label its own name', async () => {
    const result = firstValueFrom(service.prepare(7, WORD, [doc('d1', 'Zeugnis'), doc('d2', 'Zeugnis')]));

    http.expectOne('/api/word/cover-letter/7/fill/doc-1/email').flush(DRAFT);
    http.expectOne('/api/word/cover-letter/7/fill/doc-1').flush(new Blob(['%PDF']));
    http.expectOne('/api/documents/d1/download').flush(new Blob(['a']));
    http.expectOne('/api/documents/d2/download').flush(new Blob(['b']));

    expect((await result).attachments.map(a => a.file.name))
      .toEqual(['cover-letter.pdf', 'Zeugnis.pdf', 'Zeugnis-2.pdf']);
  });

  it('falls back to the stored filename for a document without a label', () => {
    expect(enclosureName(doc('d1', '  ', 'scan.pdf'))).toBe('scan.pdf');
  });

  describe('canShareFiles', () => {
    const mail = { attachments: [{ file: new File(['x'], 'a.pdf'), bytes: new Uint8Array(1) }] } as PreparedMail;

    /** `canShare` is a method on Navigator.prototype; an own property shadows it for one test. */
    function stubCanShare(value: unknown): void {
      Object.defineProperty(navigator, 'canShare', { value, configurable: true, writable: true });
    }

    afterEach(() => delete (navigator as any).canShare);

    it('is false where the browser has no canShare', () => {
      stubCanShare(undefined);
      expect(service.canShareFiles(mail)).toBeFalse();
    });

    it('follows what canShare answers for the files', () => {
      const canShare = jasmine.createSpy('canShare').and.returnValue(true);
      stubCanShare(canShare);
      expect(service.canShareFiles(mail)).toBeTrue();
      expect(canShare).toHaveBeenCalledWith({ files: [mail.attachments[0].file] });
    });

    it('treats a throwing canShare as no support', () => {
      stubCanShare(() => { throw new TypeError('nope'); });
      expect(service.canShareFiles(mail)).toBeFalse();
    });
  });

  it('builds the .eml with the letter body and every attachment', async () => {
    const mail: PreparedMail = {
      ...DRAFT,
      attachments: [{ file: new File(['%PDF'], 'Anschreiben.pdf', { type: 'application/pdf' }), bytes: new TextEncoder().encode('%PDF') }],
      totalBytes: 4,
    };
    const text = await service.eml(mail, 'de').text();
    expect(text).toContain('To: jobs@acme.example');
    expect(text).toContain('Content-Type: text/html; charset=UTF-8');
    expect(text).toContain(" filename*=UTF-8''Anschreiben.pdf");
  });
});
