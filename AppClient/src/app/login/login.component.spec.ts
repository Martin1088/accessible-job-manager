import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { signal } from '@angular/core';

import { LoginComponent } from './login.component';
import { DEMO_CONTROLS, DEMO_MODE, DemoControls, DemoRole } from '../demo/demo-mode';
import { expectNoAxeViolations } from '../../testing/a11y';
import { LegalDocument } from '../legal/legal-document.model';

/** What a deployment that publishes the notice returns. */
const DEMO_NOTICE: LegalDocument = {
  slug: 'demo-hinweis',
  requestedLanguage: 'en',
  language: 'en',
  title: 'Demo version – please read',
  intro: ['This is a non-public test version for invited participants.'],
  sections: [{
    heading: 'Please do not enter real data',
    blocks: [{ type: 'paragraph', text: 'Use sample data only.' }]
  }]
};

/** The live request for the notice, ignoring ones httpResource already cancelled. */
function noticeRequest(http: HttpTestingController) {
  return http.match(r => r.url.split('?')[0] === '/api/legal/demo-hinweis')
    .filter(r => !r.cancelled);
}

describe('LoginComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTranslateService({ fallbackLang: 'en' })
      ]
    }).compileComponents();

    // app.config.ts runs LanguageService.init() as an APP_INITIALIZER, so by the time any
    // component renders a language is set. Without this the demo-notice resource stays
    // idle by design - its URL is undefined until a language is known.
    TestBed.inject(TranslateService).use('en');
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the OAuth sign-in, not the demo persona buttons', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.sign-in .btn-primary')).toBeTruthy();
    expect(el.querySelector('.persona-btn')).toBeNull();
    // "Other roles" carries the two remaining sign-in buttons.
    expect(el.querySelectorAll('.other-roles .btn-secondary').length).toBe(2);
  });

  it('shows the three introduction topics, each with a screenshot', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const topics = el.querySelectorAll('.intro .topic');
    expect(topics.length).toBe(3);
    topics.forEach(topic => {
      expect(topic.querySelector('h3')).toBeTruthy();
      const img = topic.querySelector<HTMLImageElement>('img.topic__shot');
      expect(img).toBeTruthy();
      expect(img!.getAttribute('alt')?.length).toBeGreaterThan(0);
    });
  });

  it('shows nothing when this deployment publishes no demo notice', async () => {
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();

    // 404 is the normal answer here - there is no bundled default for this slug - so it
    // must stay silent rather than surfacing as an error on a production login page.
    noticeRequest(http)[0].flush({ message: 'not found' }, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.demo-notice')).toBeNull();
    expect(el.querySelector('[role="alert"]')).toBeNull();
  });

  it('shows the notice above the sign-in sheet when the deployment publishes one', async () => {
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();

    noticeRequest(http)[0].flush(DEMO_NOTICE);
    await fixture.whenStable();
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const notice = el.querySelector('.demo-notice');
    expect(notice).not.toBeNull();
    expect(notice!.querySelector('h2')?.id).toBe('demo-notice-title');
    expect(notice!.getAttribute('aria-labelledby')).toBe('demo-notice-title');
    expect(notice!.querySelector('h1')).toBeNull();
    expect(notice!.querySelector('h3')?.textContent).toContain('do not enter real data');
    expect(notice!.querySelector('a[href="/datenschutz"]')).not.toBeNull();

    // A warning about what not to type has to precede the button that starts the typing.
    const sections = Array.from(el.querySelectorAll('section'));
    expect(sections.indexOf(notice as HTMLElement))
      .toBeLessThan(sections.indexOf(el.querySelector('.sign-in') as HTMLElement));
  });

  it('keeps exactly one h1 on the page while the notice is shown', async () => {
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    noticeRequest(http)[0].flush(DEMO_NOTICE);
    await fixture.whenStable();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelectorAll('h1').length).toBe(1);
  });

  it('has no axe-detectable accessibility violations', async () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    await expectNoAxeViolations(fixture);
  });

  it('has no axe-detectable accessibility violations with the notice shown', async () => {
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    noticeRequest(http)[0].flush(DEMO_NOTICE);
    await fixture.whenStable();
    fixture.detectChanges();

    await expectNoAxeViolations(fixture);
  });
});

describe('LoginComponent (demo build)', () => {
  const switched: DemoRole[] = [];

  const controls: DemoControls = {
    role: signal<DemoRole | null>(null),
    people: {
      USER: { name: 'Sabine Vogt' },
      ADVISOR: { name: 'Jonas Reinhardt' },
      REVIEWER: { name: 'Amira Sayed' },
    },
    switchTo: role => switched.push(role),
    reset: () => undefined,
  };

  beforeEach(async () => {
    switched.length = 0;
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTranslateService({ fallbackLang: 'en' }),
        { provide: DEMO_MODE, useValue: true },
        { provide: DEMO_CONTROLS, useValue: controls },
      ]
    }).compileComponents();

    // app.config.ts runs LanguageService.init() as an APP_INITIALIZER, so by the time any
    // component renders a language is set. Without this the demo-notice resource stays
    // idle by design - its URL is undefined until a language is known.
    TestBed.inject(TranslateService).use('en');
  });

  it('offers the applicant persona first, then the other two roles', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const primary = el.querySelector<HTMLButtonElement>('.sign-in .persona-btn');
    expect(primary).toBeTruthy();
    expect(primary!.textContent).toContain('Sabine Vogt');
    const others = el.querySelectorAll<HTMLButtonElement>('.other-roles .persona-btn');
    expect(others.length).toBe(2);
    expect(others[0].textContent).toContain('Jonas Reinhardt');
    expect(others[1].textContent).toContain('Amira Sayed');
  });

  it('hands the picked role to the demo controls', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    el.querySelector<HTMLButtonElement>('.sign-in .persona-btn')!.click();
    el.querySelectorAll<HTMLButtonElement>('.other-roles .persona-btn')[1].click();
    expect(switched).toEqual(['USER', 'REVIEWER']);
  });

  it('has no axe-detectable accessibility violations', async () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    await expectNoAxeViolations(fixture);
  });
});
