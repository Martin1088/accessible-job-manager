import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';

import { CvMatchComponent } from './cv-match.component';
import { CvMatchResult, SkillAssessment } from '../../model/cv-match';
import { DefaultDocument, Document } from '../../model/document';
import { DEMO_MODE } from '../../demo/demo-mode';
import { expectNoAxeViolations } from '../../../testing/a11y';

const cv = (over: Partial<Document> = {}): Document => ({
  id: 'cv-1', label: 'Lebenslauf', filename: 'cv.pdf', mimeType: 'application/pdf',
  type: 'CV', language: 'GERMAN', createdAt: '2026-01-01T10:00:00', ...over,
});

const skill = (over: Partial<SkillAssessment> = {}): SkillAssessment => ({
  skill: 'Angular', category: 'TECHNICAL', importance: 'REQUIRED', status: 'MET',
  postingQuote: 'Angular', cvEvidence: 'Angular', reasoning: 'Shown.', suggestion: null, ...over,
});

const result = (over: Partial<CvMatchResult> = {}): CvMatchResult => ({
  level: 'PARTIAL', summary: 'Mostly there.', cvDocumentId: 'cv-1',
  postingTitle: 'Frontend Engineer', company: 'Acme', language: 'en',
  postingLanguage: 'de', cvLanguage: 'en', generatedAt: '2026-10-12T09:30:00',
  skills: [
    skill(),
    skill({ skill: 'Fintech', importance: 'NICE_TO_HAVE', status: 'MISSING', cvEvidence: null, suggestion: 'Read up.' }),
    skill({ skill: 'React', status: 'MISSING', cvEvidence: null, suggestion: 'Build something.' }),
    skill({ skill: 'VoiceOver', status: 'PARTIAL' }),
  ],
  ...over,
});

describe('CvMatchComponent', () => {
  let http: HttpTestingController;
  let announcer: jasmine.SpyObj<LiveAnnouncer>;

  beforeEach(async () => {
    announcer = jasmine.createSpyObj<LiveAnnouncer>('LiveAnnouncer', ['announce']);
    await TestBed.configureTestingModule({
      imports: [CvMatchComponent],
      providers: [
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTranslateService({ fallbackLang: 'en' }),
        { provide: LiveAnnouncer, useValue: announcer },
      ],
    }).compileComponents();
  });

  afterEach(() => http?.verify());

  // Injecting instantiates the test module, so it waits until a test has had
  // its chance to override a provider (the demo case below).
  const defaultCv = (document: Document): DefaultDocument =>
    ({ type: 'CV', language: document.language, document });

  /** The interface runs in English, so this is the CV that is compared. */
  const ENGLISH_CV = cv({ id: 'cv-1', label: 'CV', language: 'ENGLISH' });

  function create(embedded = false, defaults: DefaultDocument[] | 'error' = [defaultCv(ENGLISH_CV)]) {
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(TranslateService).use('en');
    const fixture = TestBed.createComponent(CvMatchComponent);
    fixture.componentRef.setInput('embedded', embedded);
    fixture.detectChanges();
    const defaultsRequest = http.expectOne('/api/documents/defaults');
    if (defaults === 'error') {
      defaultsRequest.flush(null, { status: 500, statusText: 'Server Error' });
    } else {
      defaultsRequest.flush(defaults);
    }
    fixture.detectChanges();
    return fixture;
  }

  function runWithUrl(fixture: ReturnType<typeof create>, body: CvMatchResult = result()) {
    fixture.componentInstance.postingUrl.set('https://jobs.example/1');
    fixture.componentInstance.run();
    const req = http.expectOne(r => r.url === '/api/matching');
    req.flush(body);
    fixture.detectChanges();
    return req;
  }

  describe('the CV that is compared', () => {
    const german = cv({ id: 'cv-de', label: 'Lebenslauf', language: 'GERMAN' });

    it('is the default CV in the interface language, named on the page', () => {
      const fixture = create(false, [defaultCv(german), defaultCv(ENGLISH_CV)]);
      expect(fixture.componentInstance.cv()?.id).toBe('cv-1');
      const text = (fixture.nativeElement as HTMLElement).querySelector('.cv-default')!.textContent!;
      expect(text).toContain('CV');
      expect(text).toContain('LANGUAGE.EN');
    });

    it('follows a switch of the interface language', () => {
      const fixture = create(false, [defaultCv(german), defaultCv(ENGLISH_CV)]);
      TestBed.inject(TranslateService).use('de');
      fixture.detectChanges();
      expect(fixture.componentInstance.cv()?.id).toBe('cv-de');
    });

    it('is not taken from another language: the page asks for a default and cannot run', () => {
      const fixture = create(false, [defaultCv(german)]);
      fixture.componentInstance.postingUrl.set('https://jobs.example/1');
      fixture.detectChanges();
      const el: HTMLElement = fixture.nativeElement;
      expect(fixture.componentInstance.cv()).toBeNull();
      expect(el.textContent).toContain('MATCHING.NO_DEFAULT_CV');
      expect(el.querySelector('a[href="/documents"]')).not.toBeNull();
      expect(fixture.componentInstance.canRun()).toBeFalse();
    });

    it('reports when the defaults cannot be read', () => {
      const fixture = create(false, 'error');
      expect(fixture.componentInstance.cvsError()).toBeTrue();
      expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')).not.toBeNull();
      expect(fixture.componentInstance.canRun()).toBeFalse();
    });
  });

  describe('with the posting handed in by the page', () => {
    const PRESET = { kind: 'url', url: 'https://jobs.example/preset' } as const;

    function createWithPreset(focusOnOpen = false) {
      http = TestBed.inject(HttpTestingController);
      TestBed.inject(TranslateService).use('en');
      const fixture = TestBed.createComponent(CvMatchComponent);
      fixture.componentRef.setInput('embedded', true);
      fixture.componentRef.setInput('presetPosting', PRESET);
      fixture.componentRef.setInput('focusOnOpen', focusOnOpen);
      fixture.detectChanges();
      http.expectOne('/api/documents/defaults').flush([defaultCv(ENGLISH_CV)]);
      fixture.detectChanges();
      return fixture;
    }

    it('names it instead of asking for it again', () => {
      const el: HTMLElement = createWithPreset().nativeElement;
      expect(el.querySelector('#match-url')).toBeNull();
      expect(el.querySelector('input[name="match-source"]')).toBeNull();
      expect(el.querySelector('.posting-preset')!.textContent).toContain('https://jobs.example/preset');
    });

    it('compares it', () => {
      const fixture = createWithPreset();
      expect(fixture.componentInstance.canRun()).toBeTrue();
      fixture.componentInstance.run();
      const req = http.expectOne(r => r.url === '/api/matching');
      expect(req.request.params.get('url')).toBe('https://jobs.example/preset');
      req.flush(result());
    });

    it('drops the result of the previous posting when another one is handed in', () => {
      const fixture = createWithPreset();
      fixture.componentInstance.run();
      http.expectOne(r => r.url === '/api/matching').flush(result());
      fixture.detectChanges();
      expect(fixture.componentInstance.result()).not.toBeNull();

      fixture.componentRef.setInput('presetPosting', { kind: 'text', text: 'Another posting' });
      fixture.detectChanges();

      expect(fixture.componentInstance.result()).toBeNull();
      expect((fixture.nativeElement as HTMLElement).querySelector('.posting-preset')!.textContent)
        .toContain('MATCHING.POSTING_PRESET_TEXT');
    });

    it('takes focus to its heading when the page has just opened it', async () => {
      const fixture = createWithPreset(true);
      await fixture.whenStable();
      expect(document.activeElement?.id).toBe('match-heading');
    });

    it('has no axe-detectable accessibility violations', async () => {
      await expectNoAxeViolations(createWithPreset());
    });
  });

  it('sends the URL, the chosen CV and the interface language', () => {
    const fixture = create();
    const req = runWithUrl(fixture);
    expect(req.request.method).toBe('POST');
    expect(req.request.params.get('url')).toBe('https://jobs.example/1');
    expect(req.request.params.get('cvDocumentId')).toBe('cv-1');
    expect(req.request.params.get('lang')).toBe('en');
  });

  it('sends pasted text to the text endpoint', () => {
    const fixture = create();
    fixture.componentInstance.sourceKind.set('text');
    fixture.componentInstance.postingText.set('We are hiring');
    fixture.componentInstance.run();
    const req = http.expectOne('/api/matching/text');
    expect(req.request.body).toEqual({ text: 'We are hiring', cvDocumentId: 'cv-1', lang: 'en' });
    req.flush(result());
  });

  it('groups missing first, then partly met, then met - requirements before nice-to-haves', () => {
    const fixture = create();
    runWithUrl(fixture);
    const groups = fixture.componentInstance.groups();
    expect(groups.map(g => g.status)).toEqual(['MISSING', 'PARTIAL', 'MET']);
    expect(groups[0].skills.map(s => s.skill)).toEqual(['React', 'Fintech']);

    const headings = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('h3'))
      .map(h => h.id);
    expect(headings).toEqual(['match-group-MISSING', 'match-group-PARTIAL', 'match-group-MET']);
  });

  it('announces the outcome and moves focus to the result heading', async () => {
    const fixture = create();
    runWithUrl(fixture);
    await fixture.whenStable();
    expect(announcer.announce).toHaveBeenCalledWith(jasmine.any(String), 'polite');
    expect(document.activeElement?.id).toBe('match-result-heading');
  });

  it('marks quotes in another language with their own lang', () => {
    const fixture = create();
    runWithUrl(fixture);
    const el: HTMLElement = fixture.nativeElement;
    const quotes = Array.from(el.querySelectorAll('blockquote'));
    expect(quotes.some(q => q.getAttribute('lang') === 'de')).toBeTrue();
    // The CV quotes are in the result's own language and need no attribute.
    expect(quotes.some(q => !q.hasAttribute('lang'))).toBeTrue();
  });

  it('puts lang on the result when the model answered in another language', () => {
    const fixture = create();
    runWithUrl(fixture, result({ language: 'de' }));
    const section = (fixture.nativeElement as HTMLElement).querySelector('section.result');
    expect(section?.getAttribute('lang')).toBe('de');
  });

  it('reports a failure as an alert', () => {
    const fixture = create();
    fixture.componentInstance.postingUrl.set('https://jobs.example/1');
    fixture.componentInstance.run();
    http.expectOne(r => r.url === '/api/matching')
      .flush({ message: 'Model unavailable' }, { status: 502, statusText: 'Bad Gateway' });
    fixture.detectChanges();
    const alert = (fixture.nativeElement as HTMLElement).querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('Model unavailable');
    expect(fixture.componentInstance.busy()).toBeFalse();
  });

  it('cancels a running check', () => {
    const fixture = create();
    fixture.componentInstance.postingUrl.set('https://jobs.example/1');
    fixture.componentInstance.run();
    const req = http.expectOne(r => r.url === '/api/matching');
    fixture.componentInstance.cancel();
    expect(req.cancelled).toBeTrue();
    expect(fixture.componentInstance.busy()).toBeFalse();
  });

  it('labels the result as an example in the demo', () => {
    TestBed.overrideProvider(DEMO_MODE, { useValue: true });
    const fixture = create();
    runWithUrl(fixture);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('MATCHING.DEMO_SAMPLE');
  });

  describe('embedded on the home page', () => {
    const headings = (el: HTMLElement, selector: string) =>
      Array.from(el.querySelectorAll(selector)).map(h => h.id);

    it('is a section under the page\'s own h1, not a second main', () => {
      const el: HTMLElement = create(true).nativeElement;
      expect(el.querySelector('main')).toBeNull();
      expect(el.querySelector('h1')).toBeNull();
      const section = el.querySelector('section.match-sheet');
      expect(section?.getAttribute('aria-labelledby')).toBe('match-heading');
      expect(el.querySelector('h2#match-heading')).not.toBeNull();
    });

    it('puts every heading one level deeper', () => {
      const fixture = create(true);
      runWithUrl(fixture);
      const el: HTMLElement = fixture.nativeElement;
      expect(headings(el, 'h2')).toEqual(['match-heading']);
      expect(headings(el, 'h3')).toEqual(['match-posting-heading', 'match-cv-heading', 'match-result-heading']);
      expect(headings(el, 'h4')).toEqual(['match-group-MISSING', 'match-group-PARTIAL', 'match-group-MET']);
    });

    it('still moves focus to the result heading', async () => {
      const fixture = create(true);
      runWithUrl(fixture);
      await fixture.whenStable();
      expect(document.activeElement?.id).toBe('match-result-heading');
      expect(document.activeElement?.tagName).toBe('H3');
    });

    it('has no axe-detectable accessibility violations with a result', async () => {
      const fixture = create(true);
      runWithUrl(fixture);
      await expectNoAxeViolations(fixture);
    });
  });

  it('has no axe-detectable accessibility violations before a check', async () => {
    const fixture = create();
    await expectNoAxeViolations(fixture);
  });

  it('has no axe-detectable accessibility violations with a result', async () => {
    const fixture = create();
    runWithUrl(fixture);
    await expectNoAxeViolations(fixture);
  });
});
