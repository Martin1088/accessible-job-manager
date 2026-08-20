import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EMPTY, of } from 'rxjs';
import { provideTranslateService } from '@ngx-translate/core';

import { ApplicationListComponent } from './application-list.component';
import { ApplicationService } from '../../services/application.service';
import { CoverLetterService } from '../../services/cover-letter.service';
import { Application } from '../../model/application';
import { Document } from '../../model/document';
import { HtmlLetterTemplate } from '../../model/cover-letter';

const APPLICATION: Application = {
  id: 7,
  companyPositionId: 3,
  companyName: 'Acme GmbH',
  positionTitle: 'Developer',
  status: 'DRAFT',
  appliedDate: '2026-01-02',
  notes: '',
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

  beforeEach(async () => {
    applicationServiceSpy = jasmine.createSpyObj('ApplicationService', ['getAll', 'create', 'update', 'delete']);
    applicationServiceSpy.getAll.and.returnValue(of([APPLICATION]));
    applicationServiceSpy.update.and.returnValue(of(APPLICATION));

    coverLetterServiceSpy = jasmine.createSpyObj('CoverLetterService',
      ['listTemplates', 'renderPdf', 'renderText', 'renderEmail']);
    coverLetterServiceSpy.listTemplates.and.returnValue(of([HTML_TEMPLATE]));

    await TestBed.configureTestingModule({
      imports: [ApplicationListComponent],
      providers: [
        { provide: ApplicationService, useValue: applicationServiceSpy },
        { provide: CoverLetterService, useValue: coverLetterServiceSpy },
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
});
