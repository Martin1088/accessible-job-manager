import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';

import { ImpressumComponent } from './impressum.component';
import { LEGAL_FIXTURE } from '../legal-document.fixture';
import { expectNoAxeViolations } from '../../../testing/a11y';

describe('ImpressumComponent', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ImpressumComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ImpressumComponent],
      providers: [
        provideTranslateService({ fallbackLang: 'en' }),
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    }).compileComponents();

    TestBed.inject(TranslateService).use('en');
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ImpressumComponent);
  });

  // httpResource cancels an in-flight request whenever its URL recomputes, and the
  // language signal settles during startup - so a cancelled request here is expected
  // rather than a leak.
  afterEach(() => http.verify({ ignoreCancelled: true }));

  /**
   * The live request for this slug. `httpResource` takes a URL string, so the query is
   * part of `request.url` rather than of `request.params`.
   */
  function pending(language?: string): TestRequest {
    // `cancelled` lives on TestRequest, not on the HttpRequest the predicate sees, so
    // the match is by URL and the cancelled ones are dropped afterwards.
    const live = http.match(request => {
      const [path, query] = request.url.split('?');
      return path === '/api/legal/impressum'
        && (language === undefined || new URLSearchParams(query).get('lang') === language);
    }).filter(request => !request.cancelled);
    expect(live.length).withContext('a live request for impressum').toBeGreaterThan(0);
    return live[live.length - 1];
  }

  /** Async because the resource publishes its value a microtask after the flush. */
  async function load(): Promise<void> {
    fixture.detectChanges();
    pending().flush(LEGAL_FIXTURE);
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('should create', () => {
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('asks the backend for the document in the interface language', () => {
    fixture.detectChanges();

    pending('en').flush(LEGAL_FIXTURE);
  });

  it('refetches when the interface language changes', async () => {
    await load();

    TestBed.inject(TranslateService).use('de');
    fixture.detectChanges();

    // A route resolver would have left the page in English until the visitor navigated
    // away and back; this is the behaviour that rules one out.
    pending('de').flush({ ...LEGAL_FIXTURE, language: 'de', requestedLanguage: 'de' });
  });

  it('renders the document inside the page frame', async () => {
    await load();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelector('main#main-content')).not.toBeNull();
    expect(el.querySelector('.back-link')).not.toBeNull();
    expect(el.querySelector('h1')?.textContent?.trim()).toBe(LEGAL_FIXTURE.title);
  });

  it('has no axe-detectable accessibility violations', async () => {
    await load();
    await expectNoAxeViolations(fixture);
  });
});
