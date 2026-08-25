import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AccessibilityService } from './accessibility.service';
import { DEFAULT_PREFERENCES, UserPreferences } from '../model/user-preferences';
import { UserProfile } from '../model/user-profile';

describe('AccessibilityService', () => {
  let service: AccessibilityService;
  let http: HttpTestingController;
  let html: HTMLElement;

  const baseProfile: Omit<UserProfile, 'preferences'> = {
    userId: 'u1', name: 'Alice', email: 'a@b.com', street: null, postalCode: null,
    city: null, phone: null, roles: ['USER']
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withXhr()), provideHttpClientTesting()]
    });
    service = TestBed.inject(AccessibilityService);
    http = TestBed.inject(HttpTestingController);
    html = document.documentElement;
  });

  afterEach(() => {
    http.verify();
    ['data-contrast', 'data-reduce-motion', 'data-hide-images', 'data-font-family'].forEach(a => html.removeAttribute(a));
    html.style.removeProperty('--font-scale');
    html.style.removeProperty('--line-height');
  });

  function initWith(preferences: UserPreferences): void {
    service.init().subscribe();
    http.expectOne('/api/profile').flush({ ...baseProfile, preferences });
  }

  it('applies a HIGH contrast override to <html>', () => {
    initWith({ ...DEFAULT_PREFERENCES, contrastMode: 'HIGH' });
    expect(html.getAttribute('data-contrast')).toBe('high');
  });

  it('applies a DARK contrast override to <html>', () => {
    initWith({ ...DEFAULT_PREFERENCES, contrastMode: 'DARK' });
    expect(html.getAttribute('data-contrast')).toBe('dark');
  });

  it('falls back to system contrast when no override is set', () => {
    initWith(DEFAULT_PREFERENCES);
    expect(html.getAttribute('data-contrast')).toBe('system');
  });

  it('forces reduced motion on when the user overrides it', () => {
    initWith({ ...DEFAULT_PREFERENCES, reduceMotion: true });
    expect(html.getAttribute('data-reduce-motion')).toBe('true');
  });

  it('defaults hideImages to false when unset', () => {
    initWith(DEFAULT_PREFERENCES);
    expect(html.getAttribute('data-hide-images')).toBe('false');
  });

  it('maps DYSLEXIA_FRIENDLY to the dyslexia-friendly data attribute', () => {
    initWith({ ...DEFAULT_PREFERENCES, fontFamily: 'DYSLEXIA_FRIENDLY' });
    expect(html.getAttribute('data-font-family')).toBe('dyslexia-friendly');
  });

  it('sets --font-scale and --line-height from stored values', () => {
    initWith({ ...DEFAULT_PREFERENCES, fontScale: 1.3, lineHeight: 1.8 });
    expect(html.style.getPropertyValue('--font-scale')).toBe('1.3');
    expect(html.style.getPropertyValue('--line-height')).toBe('1.8');
  });

  it('defaults --font-scale to 1 and --line-height to 1.5 when unset', () => {
    initWith(DEFAULT_PREFERENCES);
    expect(html.style.getPropertyValue('--font-scale')).toBe('1');
    expect(html.style.getPropertyValue('--line-height')).toBe('1.5');
  });
});
