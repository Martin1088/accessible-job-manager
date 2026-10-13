import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EMPTY, of, throwError } from 'rxjs';
import { provideTranslateService } from '@ngx-translate/core';

import { LiveAnnouncer } from '@angular/cdk/a11y';

import { ApplicationListComponent } from './application-list.component';
import { ApplicationService } from '../../services/application.service';
import { CoverLetterService } from '../../services/cover-letter.service';
import { CompanyService } from '../../services/company.service';
import { Company } from '../../model/company';
import { Application } from '../../model/application';
import { DefaultDocument, Document } from '../../model/document';
import { HtmlLetterTemplate } from '../../model/cover-letter';
import { expectNoAxeViolations } from '../../../testing/a11y';

const APPLICATION: Application = {
  id: 7,
  companyPositionId: 3,
  companyName: 'Acme GmbH',
  positionTitle: 'Developer',
  status: 'DRAFT',
  appliedDate: '2026-01-02',
  notes: '',
};

const COMPANY: Company = {
  id: 1,
  name: 'Acme GmbH',
  locations: [],
  positions: [{ id: 3, title: 'Developer', email: 'jobs@acme.example' }],
};

const WORD_TEMPLATE = { id: 'word-1', label: 'Standard .docx' } as Document;

const HTML_TEMPLATE = { id: 'html-1', name: 'DIN 5008 letter' } as HtmlLetterTemplate;

describe('ApplicationListComponent', () => {
  let component: ApplicationListComponent;
  let fixture: ComponentFixture<ApplicationListComponent>;
  let applicationServiceSpy: jasmine.SpyObj<ApplicationService>;
  let coverLetterServiceSpy: jasmine.SpyObj<CoverLetterService>;
  let httpMock: HttpTestingController;

  /** Keeps a blob download from reaching the browser's download machinery. */
  function stubAnchor(): { href: string; download: string; click: jasmine.Spy } {
    const anchor = { href: '', download: '', click: jasmine.createSpy('click') };
    const create = document.createElement.bind(document);
    spyOn(document, 'createElement').and.callFake(
      (tag: string) => (tag === 'a' ? anchor : create(tag)) as any);
    return anchor;
  }

  /** What the company list answers with; a test may swap it before creating its own instance. */
  let companies: Company[] = [COMPANY];

  beforeEach(async () => {
    companies = [COMPANY];
    applicationServiceSpy = jasmine.createSpyObj('ApplicationService',
      ['getAll', 'create', 'update', 'delete', 'getDocuments', 'setDocuments']);
    applicationServiceSpy.getAll.and.returnValue(of([APPLICATION]));
    applicationServiceSpy.getDocuments.and.returnValue(of([]));
    applicationServiceSpy.update.and.returnValue(of(APPLICATION));

    coverLetterServiceSpy = jasmine.createSpyObj('CoverLetterService',
      ['listTemplates', 'renderPdf', 'renderText', 'renderEmail']);
    coverLetterServiceSpy.listTemplates.and.returnValue(of([HTML_TEMPLATE]));

    await TestBed.configureTestingModule({
      imports: [ApplicationListComponent],
      providers: [
        { provide: ApplicationService, useValue: applicationServiceSpy },
        { provide: CoverLetterService, useValue: coverLetterServiceSpy },
        { provide: CompanyService, useValue: { getAll: () => of(companies) } },
        { provide: Router, useValue: jasmine.createSpyObj('Router', ['navigate']) },
        { provide: ActivatedRoute, useValue: { queryParams: of({}) } },
        provideTranslateService({ fallbackLang: 'en' }),
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
      ]
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);

    fixture = TestBed.createComponent(ApplicationListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    // The Word templates come from the documents endpoint, the letter templates
    // from the (spied) cover letter service.
    httpMock.expectOne(r => r.url === '/api/documents').flush([WORD_TEMPLATE]);
  });

  it('opens unfiltered, showing every application', () => {
    expect(component.filterYear).toBe('');
    expect(component.filterMonth).toBe('');
    expect(component.filterActive).toBeFalse();
  });

  it('lists the most recently updated application first, falling back to creation', () => {
    const rows = (component as any).toRows([
      { ...APPLICATION, id: 1, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-02-01T00:00:00Z' },
      { ...APPLICATION, id: 2, createdAt: '2026-03-01T00:00:00Z' },
      { ...APPLICATION, id: 3, createdAt: '2026-01-15T00:00:00Z' },
    ]);
    expect(rows.map((r: any) => r.id)).toEqual([2, 1, 3]);
  });

  it('the company name opens the details of its position, and Close clears them', () => {
    fixture.detectChanges();
    const name: HTMLButtonElement = fixture.nativeElement.querySelector('td button.cell-link');
    expect(name.textContent!.trim()).toBe('Acme GmbH');

    name.click();
    fixture.detectChanges();
    const panel: HTMLElement = fixture.nativeElement.querySelector('section.position-details');
    expect(panel.textContent).toContain('jobs@acme.example');

    component.closeDetails();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('section.position-details')).toBeNull();
  });

  it('leaves the company name as plain text when its position is unknown', () => {
    component.rows = (component as any).toRows([{ ...APPLICATION, companyPositionId: 99 }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('td button.cell-link')).toBeNull();
  });

  it('has no axe-detectable accessibility violations with the details open', async () => {
    component.openDetails({ companyPositionId: 3 });
    fixture.detectChanges();
    await expectNoAxeViolations(fixture);
  });

  it('offers the templates of both providers', () => {
    expect(component.wordTemplates).toEqual([{ id: 'word-1', label: 'Standard .docx', provider: 'WORD' }]);
    expect(component.htmlTemplates).toEqual([{ id: 'html-1', label: 'DIN 5008 letter', provider: 'HTML' }]);
    expect(component.hasTemplates).toBeTrue();
  });

  it('downloads a PDF from the word provider for a word template', () => {
    const anchor = stubAnchor();
    component.selectedTemplate[7] = component.wordTemplates[0];

    component.downloadCoverLetter(7);
    httpMock.expectOne('/api/word/cover-letter/7/fill/word-1').flush(new Blob(['pdf']));

    expect(coverLetterServiceSpy.renderPdf).not.toHaveBeenCalled();
    expect(anchor.download).toBe('Anschreiben_Acme_GmbH.pdf');
    expect(anchor.click).toHaveBeenCalled();
  });

  it('downloads a PDF from the html provider for a letter template', () => {
    const anchor = stubAnchor();
    coverLetterServiceSpy.renderPdf.and.returnValue(of({ body: new Blob(['pdf']) } as any));
    component.selectedTemplate[7] = component.htmlTemplates[0];

    component.downloadCoverLetter(7);

    expect(coverLetterServiceSpy.renderPdf).toHaveBeenCalledWith(7, 'html-1', { attachments: [] });
    expect(anchor.download).toBe('Anschreiben_Acme_GmbH.pdf');
    expect(anchor.click).toHaveBeenCalled();
    httpMock.expectNone(r => r.url.startsWith('/api/word/'));
  });

  it('emails a letter template through the html provider', () => {
    coverLetterServiceSpy.renderEmail.and.returnValue(EMPTY);
    component.selectedTemplate[7] = component.htmlTemplates[0];

    component.emailCoverLetter(7);

    expect(coverLetterServiceSpy.renderEmail).toHaveBeenCalledWith(7, 'html-1', { attachments: [] });
    httpMock.expectNone(r => r.url.startsWith('/api/word/'));
  });

  it('emails a word template through the word provider', () => {
    component.selectedTemplate[7] = component.wordTemplates[0];

    component.emailCoverLetter(7);

    // Left unflushed: answering it would send the browser to a mailto: URL.
    httpMock.expectOne('/api/word/cover-letter/7/fill/word-1/email');
    expect(coverLetterServiceSpy.renderEmail).not.toHaveBeenCalled();
  });

  it('previews a letter template through the html provider', () => {
    coverLetterServiceSpy.renderText.and.returnValue(of('Sehr geehrte Frau Muster'));
    component.selectedTemplate[7] = component.htmlTemplates[0];

    component.previewCoverLetter(7);

    expect(coverLetterServiceSpy.renderText).toHaveBeenCalledWith(7, 'html-1', { attachments: [] });
    expect(component.preview).toEqual({
      company: 'Acme GmbH',
      template: 'DIN 5008 letter',
      text: 'Sehr geehrte Frau Muster',
    });
    httpMock.expectNone(r => r.url.startsWith('/api/word/'));
  });

  it('previews a word template through the word provider', () => {
    component.selectedTemplate[7] = component.wordTemplates[0];

    component.previewCoverLetter(7);
    httpMock.expectOne('/api/word/cover-letter/7/fill/word-1/text').flush('Dear Ms Sample');

    expect(component.preview?.text).toBe('Dear Ms Sample');
    expect(coverLetterServiceSpy.renderText).not.toHaveBeenCalled();
  });

  it('a preview does not count as having applied', () => {
    coverLetterServiceSpy.renderText.and.returnValue(of('letter'));
    component.selectedTemplate[7] = component.htmlTemplates[0];

    component.previewCoverLetter(7);

    expect(applicationServiceSpy.update).not.toHaveBeenCalled();
  });

  it('closePreview clears the panel', () => {
    coverLetterServiceSpy.renderText.and.returnValue(of('letter'));
    component.selectedTemplate[7] = component.htmlTemplates[0];
    component.previewCoverLetter(7);

    component.closePreview();

    expect(component.preview).toBeNull();
  });

  it('offers the Word download only for a word template', () => {
    component.selectedTemplate[7] = component.htmlTemplates[0];
    expect(component.supportsWord(7)).toBeFalse();

    component.downloadCoverLetterWord(7);
    httpMock.expectNone(r => r.url.startsWith('/api/word/'));

    component.selectedTemplate[7] = component.wordTemplates[0];
    expect(component.supportsWord(7)).toBeTrue();
  });

  describe('preselecting the default Word template', () => {
    const OTHER_TEMPLATE = { id: 'word-2', label: 'Other .docx' } as Document;
    const germanDefault: DefaultDocument = {
      type: 'COVER_LETTER_TEMPLATE', language: 'GERMAN', document: WORD_TEMPLATE,
    };

    /** A fresh instance whose position is applied for in `language`. */
    function createFor(language: 'GERMAN' | 'ENGLISH' | undefined, defaults: DefaultDocument[]) {
      companies = [{ ...COMPANY, positions: [{ ...COMPANY.positions[0], applyLanguage: language }] }];
      const own = TestBed.createComponent(ApplicationListComponent);
      own.detectChanges();
      // The instance from the outer beforeEach asked too; answer the newest requests.
      httpMock.match(r => r.url === '/api/documents').at(-1)!.flush([OTHER_TEMPLATE, WORD_TEMPLATE]);
      httpMock.match('/api/documents/defaults').at(-1)!.flush(defaults);
      own.detectChanges();
      return own.componentInstance;
    }

    it('picks the default template in the language the position is applied in', () => {
      const list = createFor('GERMAN', [germanDefault]);
      expect(list.selectedTemplate[APPLICATION.id!]?.id).toBe('word-1');
    });

    it('guesses nothing for a position without a language, or a language without a default', () => {
      expect(createFor(undefined, [germanDefault]).selectedTemplate[APPLICATION.id!]).toBeUndefined();
      expect(createFor('ENGLISH', [germanDefault]).selectedTemplate[APPLICATION.id!]).toBeUndefined();
    });

    it('never replaces a template the user picked', () => {
      companies = [{ ...COMPANY, positions: [{ ...COMPANY.positions[0], applyLanguage: 'GERMAN' }] }];
      const own = TestBed.createComponent(ApplicationListComponent);
      own.detectChanges();
      httpMock.match(r => r.url === '/api/documents').at(-1)!.flush([OTHER_TEMPLATE, WORD_TEMPLATE]);
      const chosen = own.componentInstance.wordTemplates[0];
      own.componentInstance.selectedTemplate[APPLICATION.id!] = chosen;
      // The defaults arrive after the user has already chosen.
      httpMock.match('/api/documents/defaults').at(-1)!.flush([germanDefault]);
      expect(own.componentInstance.selectedTemplate[APPLICATION.id!]).toBe(chosen);
    });
  });

  describe('documents enclosed with an application', () => {
    const doc = (over: Partial<Document>): Document => ({
      id: 'x', label: 'X', filename: 'x.pdf', mimeType: 'application/pdf',
      type: 'CV', language: 'GERMAN', createdAt: '2026-01-01T10:00:00', ...over,
    });
    const CV = doc({ id: 'cv', label: 'Lebenslauf' });
    const CERT = doc({ id: 'cert', label: 'Zeugnis', type: 'CERTIFICATE' });
    const OLD_CERT = doc({ id: 'cert-old', label: 'Ausbildung', type: 'CERTIFICATE', createdAt: '2020-01-01T10:00:00' });
    const LETTER = doc({ id: 'letter', label: 'Vorlage', type: 'COVER_LETTER_TEMPLATE' });

    /** A fresh instance whose position is applied for in `language`, with these enclosures and defaults. */
    function createWith(options: {
      language?: 'GERMAN' | 'ENGLISH';
      enclosed?: Document[];
      defaults?: DefaultDocument[];
    }) {
      companies = [{ ...COMPANY, positions: [{ ...COMPANY.positions[0], applyLanguage: options.language }] }];
      applicationServiceSpy.getDocuments.and.returnValue(of(
        options.enclosed ? [{ applicationId: APPLICATION.id!, documents: options.enclosed }] : []));
      const own = TestBed.createComponent(ApplicationListComponent);
      own.detectChanges();
      httpMock.match(r => r.url === '/api/documents').at(-1)!.flush([WORD_TEMPLATE]);
      httpMock.match('/api/documents/defaults').at(-1)!.flush(options.defaults ?? []);
      own.detectChanges();
      return own;
    }

    function open(own: ComponentFixture<ApplicationListComponent>, documents: Document[] = [CV, CERT, OLD_CERT, LETTER]) {
      own.componentInstance.openDocuments(own.componentInstance.rows[0]);
      httpMock.expectOne(r => r.url === '/api/documents' && !r.params.has('type')).flush(documents);
      own.detectChanges();
      return own.nativeElement as HTMLElement;
    }

    const checked = (el: HTMLElement) => Array.from(el.querySelectorAll<HTMLInputElement>('.documents-panel input[type=checkbox]'))
      .filter(i => i.checked).map(i => i.closest('label')!.querySelector('.document-name')!.textContent!.trim());

    it('counts the enclosed documents in their own column', () => {
      const el: HTMLElement = createWith({ enclosed: [CV, CERT] }).nativeElement;
      expect(el.querySelector('td[data-label="APPLICATIONS.COL_DOCUMENTS"]')?.textContent?.trim())
        .toBe('APPLICATIONS.DOCUMENTS_COUNT_OTHER');
    });

    it('groups what can be enclosed by type, in reading order, and leaves templates out', () => {
      const el = open(createWith({ enclosed: [CV] }));
      expect(Array.from(el.querySelectorAll('.documents-panel legend')).map(l => l.textContent!.trim()))
        .toEqual(['APPLICATIONS.DOCUMENTS_GROUP_CV', 'APPLICATIONS.DOCUMENTS_GROUP_CERTIFICATE']);
      expect(Array.from(el.querySelectorAll('.document-option')).map(l => l.textContent!.replace(/\s+/g, ' ').trim()))
        .toEqual(['Lebenslauf (LANGUAGE.DE)', 'Ausbildung (LANGUAGE.DE)', 'Zeugnis (LANGUAGE.DE)']);
      expect(el.textContent).not.toContain('Vorlage');
      expect(checked(el)).toEqual(['Lebenslauf']);
    });

    it('suggests the defaults in the position\'s language when nothing is enclosed yet, without saving', () => {
      const own = createWith({
        language: 'GERMAN',
        defaults: [
          { type: 'CV', language: 'GERMAN', document: CV },
          { type: 'CERTIFICATE', language: 'GERMAN', document: CERT },
          { type: 'CV', language: 'ENGLISH', document: doc({ id: 'cv-en', language: 'ENGLISH' }) },
        ],
      });
      const el = open(own);
      expect(checked(el)).toEqual(['Lebenslauf', 'Zeugnis']);
      expect(el.querySelector('#documents-prefilled')).not.toBeNull();
      expect(applicationServiceSpy.setDocuments).not.toHaveBeenCalled();
    });

    it('suggests nothing once documents are enclosed', () => {
      const el = open(createWith({
        language: 'GERMAN', enclosed: [OLD_CERT],
        defaults: [{ type: 'CV', language: 'GERMAN', document: CV }],
      }));
      expect(checked(el)).toEqual(['Ausbildung']);
      expect(el.querySelector('#documents-prefilled')).toBeNull();
    });

    it('saves the chosen set, updates the count and announces it', () => {
      const announcer = TestBed.inject(LiveAnnouncer);
      spyOn(announcer, 'announce');
      const own = createWith({});
      open(own);
      applicationServiceSpy.setDocuments.and.returnValue(of({ applicationId: APPLICATION.id!, documents: [CV] }));

      own.componentInstance.toggleDocument('cv', true);
      own.componentInstance.saveDocuments();
      own.detectChanges();

      expect(applicationServiceSpy.setDocuments).toHaveBeenCalledWith(APPLICATION.id!, ['cv']);
      expect(own.componentInstance.documentsFor).toBeNull();
      expect(own.componentInstance.rows[0].documentsLabel).toBe('APPLICATIONS.DOCUMENTS_COUNT_ONE');
      expect(announcer.announce).toHaveBeenCalledWith('APPLICATIONS.DOCUMENTS_SAVED', 'polite');
    });

    it('moves focus to the panel heading, and back to the row\'s button after saving', async () => {
      const own = createWith({});
      document.body.appendChild(own.nativeElement);
      try {
        open(own);
        await own.whenStable();
        expect(document.activeElement?.id).toBe('documents-heading');

        applicationServiceSpy.setDocuments.and.returnValue(of({ applicationId: APPLICATION.id!, documents: [] }));
        own.componentInstance.saveDocuments();
        own.detectChanges();
        await own.whenStable();
        expect(document.activeElement?.id).toBe(`documents-${APPLICATION.id}`);
      } finally {
        own.nativeElement.remove();
      }
    });

    it('keeps the panel open with an alert when saving fails', () => {
      const own = createWith({});
      const el = open(own);
      applicationServiceSpy.setDocuments.and.returnValue(throwError(() => new Error('boom')));

      own.componentInstance.saveDocuments();
      own.detectChanges();

      expect(own.componentInstance.documentsFor).not.toBeNull();
      expect(el.querySelector('.documents-panel [role="alert"]')?.textContent).toContain('APPLICATIONS.ERROR_DOCUMENTS_SAVE');
    });

    it('has no axe-detectable accessibility violations with the panel open', async () => {
      const own = createWith({ language: 'GERMAN', defaults: [{ type: 'CV', language: 'GERMAN', document: CV }] });
      open(own);
      await expectNoAxeViolations(own);
    });
  });

  it('has no axe-detectable accessibility violations', async () => {
    fixture.detectChanges();
    await expectNoAxeViolations(fixture);
  });
});
