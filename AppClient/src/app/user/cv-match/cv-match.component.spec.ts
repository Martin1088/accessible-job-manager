import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';

import { CvMatchComponent } from './cv-match.component';
import { CvMatchResult, SkillAssessment } from '../../model/cv-match';
import { Document } from '../../model/document';
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
  function create(cvs: Document[] = [cv()]) {
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(TranslateService).use('en');
    const fixture = TestBed.createComponent(CvMatchComponent);
    fixture.detectChanges();
    http.expectOne(r => r.url === '/api/documents' && r.params.get('type') === 'CV').flush(cvs);
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

  it('preselects the newest CV', () => {
    const fixture = create([cv({ id: 'old', createdAt: '2025-01-01' }), cv({ id: 'new', createdAt: '2026-06-01' })]);
    expect(fixture.componentInstance.cvId()).toBe('new');
  });

  it('links to the documents page when there is no CV, and cannot run', () => {
    const fixture = create([]);
    fixture.componentInstance.postingUrl.set('https://jobs.example/1');
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('a[href="/documents"]')).not.toBeNull();
    expect(fixture.componentInstance.canRun()).toBeFalse();
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
