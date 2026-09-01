import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { signal } from '@angular/core';

import { LoginComponent } from './login.component';
import { DEMO_CONTROLS, DEMO_MODE, DemoControls, DemoRole } from '../demo/demo-mode';
import { expectNoAxeViolations } from '../../testing/a11y';

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

  it('has no axe-detectable accessibility violations', async () => {
    const fixture = TestBed.createComponent(LoginComponent);
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
