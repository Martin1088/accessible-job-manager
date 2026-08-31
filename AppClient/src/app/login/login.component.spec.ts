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

  it('renders the sign-in options, not the demo letter', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.login-options--primary')).toBeTruthy();
    expect(el.querySelector('.persona-list')).toBeNull();
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

  it('renders the letter with one button per seeded person', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const buttons = el.querySelectorAll<HTMLButtonElement>('.persona__button');
    expect(buttons.length).toBe(3);
    expect(buttons[0].textContent).toContain('Sabine Vogt');
    expect(el.querySelector('.login-options--primary')).toBeNull();
  });

  it('hands the picked role to the demo controls', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const buttons = el.querySelectorAll<HTMLButtonElement>('.persona__button');
    buttons[1].click();
    expect(switched).toEqual(['ADVISOR']);
  });

  it('has no axe-detectable accessibility violations', async () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    await expectNoAxeViolations(fixture);
  });
});
