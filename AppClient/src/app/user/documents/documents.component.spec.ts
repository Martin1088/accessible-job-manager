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
      ['getAll', 'getSharedWithMe', 'getDefaults', 'makeDefault', 'clearDefault']);
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
});
