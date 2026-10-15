import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { DocumentsComponent } from './documents.component';
import { DefaultDocument, Document } from '../../model/document';
import { DocumentService } from '../../services/document.service';
import { CoverLetterService } from '../../services/cover-letter.service';
import { RelationshipService } from '../../services/relationship.service';
import { expectNoAxeViolations } from '../../../testing/a11y';

const doc = (over: Partial<Document>): Document => ({
  id: 'x', label: 'X', filename: 'x.pdf', mimeType: 'application/pdf',
  type: 'CV', language: 'GERMAN', createdAt: '2026-01-01T10:00:00', ...over,
});

const CV_DE = doc({ id: 'cv-de', label: 'Lebenslauf' });
const CV_DE_2 = doc({ id: 'cv-de-2', label: 'Lebenslauf alt', createdAt: '2025-01-01T10:00:00' });
const OTHER = doc({ id: 'other', label: 'Sonstiges', type: 'OTHER' });
const LETTER = doc({
  id: 'letter', label: 'Anschreiben', filename: 'a.docx', type: 'COVER_LETTER_TEMPLATE',
  mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
});

const slot = (document: Document): DefaultDocument =>
  ({ type: document.type, language: document.language, document });

describe('DocumentsComponent', () => {
  let fixture: ComponentFixture<DocumentsComponent>;
  let documents: jasmine.SpyObj<DocumentService>;
  let announcer: jasmine.SpyObj<LiveAnnouncer>;

  beforeEach(async () => {
    documents = jasmine.createSpyObj<DocumentService>('DocumentService',
      ['getAll', 'getSharedWithMe', 'getDefaults', 'makeDefault', 'clearDefault', 'upload', 'update']);
    documents.getAll.and.returnValue(of([CV_DE, CV_DE_2, OTHER, LETTER]));
    documents.getSharedWithMe.and.returnValue(of([]));
    documents.getDefaults.and.returnValue(of([slot(CV_DE)]));
    announcer = jasmine.createSpyObj<LiveAnnouncer>('LiveAnnouncer', ['announce']);

    await TestBed.configureTestingModule({
      imports: [DocumentsComponent],
      providers: [
        provideRouter([]),
        provideTranslateService({ fallbackLang: 'en' }),
        { provide: DocumentService, useValue: documents },
        { provide: CoverLetterService, useValue: { listTemplates: () => of([]) } },
        { provide: RelationshipService, useValue: { mine: () => of([]) } },
        { provide: LiveAnnouncer, useValue: announcer },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DocumentsComponent);
    fixture.detectChanges();
  });

  const el = () => fixture.nativeElement as HTMLElement;

  function row(label: string): HTMLTableRowElement {
    return Array.from(el().querySelectorAll('tbody tr'))
      .find(tr => tr.querySelector('td')?.textContent?.trim() === label) as HTMLTableRowElement;
  }

  function buttons(label: string): string[] {
    return Array.from(row(label).querySelectorAll('button')).map(b => b.textContent!.trim());
  }

  function click(label: string, action: string): void {
    Array.from(row(label).querySelectorAll('button'))
      .find(b => b.textContent!.trim() === action)!.click();
    fixture.detectChanges();
  }

  const defaultCell = (label: string) =>
    row(label).querySelector('td[data-label="DOCUMENTS.COL_DEFAULT"]')!.textContent!.trim();

  it('marks the default in its own column, in words', () => {
    expect(defaultCell('Lebenslauf')).toBe('DOCUMENTS.DEFAULT_MARK');
    expect(defaultCell('Lebenslauf alt')).toBe('');
  });

  it('offers "make default" only where a default can exist, and "remove" only on the default', () => {
    expect(buttons('Lebenslauf')).toContain('DOCUMENTS.ACTION_CLEAR_DEFAULT');
    expect(buttons('Lebenslauf')).not.toContain('DOCUMENTS.ACTION_MAKE_DEFAULT');
    expect(buttons('Lebenslauf alt')).toContain('DOCUMENTS.ACTION_MAKE_DEFAULT');
    expect(buttons('Anschreiben')).toContain('DOCUMENTS.ACTION_MAKE_DEFAULT');
    // "Other" PDFs have no default slot on the server.
    expect(buttons('Sonstiges')).not.toContain('DOCUMENTS.ACTION_MAKE_DEFAULT');
  });

  it('makes a document the default, re-reads the slots and announces it', () => {
    documents.makeDefault.and.returnValue(of(slot(CV_DE_2)));
    // The server took the slot from the previous default.
    documents.getDefaults.and.returnValue(of([slot(CV_DE_2)]));

    click('Lebenslauf alt', 'DOCUMENTS.ACTION_MAKE_DEFAULT');

    expect(documents.makeDefault).toHaveBeenCalledWith('cv-de-2');
    expect(defaultCell('Lebenslauf alt')).toBe('DOCUMENTS.DEFAULT_MARK');
    expect(defaultCell('Lebenslauf')).toBe('');
    expect(announcer.announce).toHaveBeenCalledWith('DOCUMENTS.DEFAULT_SET', 'polite');
  });

  it('removes a default', () => {
    documents.clearDefault.and.returnValue(of(undefined));
    documents.getDefaults.and.returnValue(of([]));

    click('Lebenslauf', 'DOCUMENTS.ACTION_CLEAR_DEFAULT');

    expect(documents.clearDefault).toHaveBeenCalledWith('cv-de');
    expect(defaultCell('Lebenslauf')).toBe('');
    expect(announcer.announce).toHaveBeenCalledWith('DOCUMENTS.DEFAULT_CLEARED', 'polite');
  });

  it('reports a failed change as an alert and leaves the marks as they were', () => {
    documents.makeDefault.and.returnValue(throwError(() => new Error('boom')));

    click('Lebenslauf alt', 'DOCUMENTS.ACTION_MAKE_DEFAULT');

    expect(el().querySelector('[role="alert"]')?.textContent).toContain('DOCUMENTS.ERROR_DEFAULT');
    expect(defaultCell('Lebenslauf')).toBe('DOCUMENTS.DEFAULT_MARK');
    expect(announcer.announce).not.toHaveBeenCalled();
  });

  it('still lists the documents when the defaults cannot be read', () => {
    documents.getDefaults.and.returnValue(throwError(() => new Error('boom')));
    fixture = TestBed.createComponent(DocumentsComponent);
    fixture.detectChanges();

    expect(row('Lebenslauf')).toBeTruthy();
    expect(defaultCell('Lebenslauf')).toBe('');
  });

  it('has no axe-detectable accessibility violations', async () => {
    await expectNoAxeViolations(fixture);
  });
  describe('the upload form', () => {
    const pdfButton = () => Array.from(el().querySelectorAll<HTMLButtonElement>('button.btn-upload'))
      .find(b => b.textContent!.includes('DOCUMENTS.UPLOAD_PDF'))!;

    /** What the hidden file input does once the dialog closes: the button it was opened from keeps focus. */
    async function choosePdf(): Promise<void> {
      pdfButton().focus();
      const input = { files: [new File(['%PDF'], 'Lebenslauf 2026.pdf', { type: 'application/pdf' })], value: 'x' };
      fixture.componentInstance.onFileSelected({ target: input } as unknown as Event, 'DOCUMENT');
      fixture.detectChanges();
      await fixture.whenStable();
    }

    it('takes focus to the form when a PDF has been chosen, named as a document', async () => {
      await choosePdf();

      const heading = el().querySelector<HTMLElement>('#upload-heading')!;
      expect(document.activeElement).toBe(heading);
      expect(heading.textContent!.trim()).toBe('DOCUMENTS.NEW_DOCUMENT_HEADING');
      expect(el().querySelector<HTMLInputElement>('#doc-label')!.value).toBe('Lebenslauf 2026');
    });

    it('hands focus back to the upload button when cancelled', async () => {
      await choosePdf();
      fixture.componentInstance.cancelUpload();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(el().querySelector('#upload-heading')).toBeNull();
      expect(document.activeElement).toBe(pdfButton());
    });

    it('hands focus back to the upload button once the document is stored', async () => {
      documents.upload.and.returnValue(of(CV_DE));
      await choosePdf();

      fixture.componentInstance.confirmUpload();
      fixture.detectChanges();
      await fixture.whenStable();

      expect(documents.upload).toHaveBeenCalledWith(jasmine.any(File), 'Lebenslauf 2026', 'CV', 'ENGLISH');
      expect(document.activeElement).toBe(pdfButton());
    });

    it('takes focus to the form for an edit too, named as a document', async () => {
      click('Lebenslauf', 'DOCUMENTS.ACTION_EDIT');
      await fixture.whenStable();

      const heading = el().querySelector<HTMLElement>('#upload-heading')!;
      expect(document.activeElement).toBe(heading);
      expect(heading.textContent!.trim()).toBe('DOCUMENTS.EDIT_DOCUMENT_HEADING');
    });

    it('has no axe-detectable accessibility violations while open', async () => {
      await choosePdf();
      await expectNoAxeViolations(fixture);
    });
  });
});
