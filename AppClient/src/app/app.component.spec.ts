import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { AppComponent } from './app.component';
import { expectNoAxeViolations } from '../testing/a11y';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTranslateService({ fallbackLang: 'en' })
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('has no axe-detectable accessibility violations', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    await expectNoAxeViolations(fixture);
  });

  it('links to the display settings from the header once signed in', async () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('/api/me')
      .flush({ sub: 's', name: 'Test User', email: 't@example.org', roles: ['USER'] });
    fixture.detectChanges();

    const link: HTMLAnchorElement | null =
      fixture.nativeElement.querySelector('header a.a11y-trigger');
    expect(link).not.toBeNull();
    expect(link!.getAttribute('href')).toBe('/preferences');
    expect(link!.getAttribute('aria-label')).toBeTruthy();
    expect(link!.querySelector('svg')!.getAttribute('aria-hidden')).toBe('true');
    await expectNoAxeViolations(fixture);
  });
});
