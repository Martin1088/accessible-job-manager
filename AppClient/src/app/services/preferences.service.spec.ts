import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { PreferencesService } from './preferences.service';
import { DEFAULT_PREFERENCES, UserPreferences } from '../model/user-preferences';
import { UserProfile } from '../model/user-profile';

describe('PreferencesService', () => {
  let service: PreferencesService;
  let http: HttpTestingController;

  const baseProfile: Omit<UserProfile, 'preferences'> = {
    userId: 'u1', name: 'Alice', email: 'a@b.com', street: null, postalCode: null,
    city: null, phone: null, role: 'USER', advisors: [], reviewers: []
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withXhr()), provideHttpClientTesting()]
    });
    service = TestBed.inject(PreferencesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('starts with DEFAULT_PREFERENCES before load() resolves', (done) => {
    service.preferences$.subscribe(prefs => {
      expect(prefs).toEqual(DEFAULT_PREFERENCES);
      done();
    });
  });

  it('load() fetches /api/profile and seeds preferences$ with the stored preferences', (done) => {
    const stored: UserPreferences = { ...DEFAULT_PREFERENCES, contrastMode: 'HIGH', fontScale: 1.2 };
    service.load().subscribe(prefs => {
      expect(prefs).toEqual(stored);
      service.preferences$.subscribe(cached => {
        expect(cached).toEqual(stored);
        done();
      });
    });
    http.expectOne('/api/profile').flush({ ...baseProfile, preferences: stored });
  });

  it('load() falls back to DEFAULT_PREFERENCES on API error', (done) => {
    service.load().subscribe(prefs => {
      expect(prefs).toEqual(DEFAULT_PREFERENCES);
      done();
    });
    http.expectOne('/api/profile').error(new ProgressEvent('error'));
  });

  it('update() PATCHes /api/profile/preferences and updates the cached stream', (done) => {
    const updated: UserPreferences = { ...DEFAULT_PREFERENCES, reduceMotion: true };
    service.update(updated).subscribe(prefs => {
      expect(prefs).toEqual(updated);
      service.preferences$.subscribe(cached => {
        expect(cached).toEqual(updated);
        done();
      });
    });
    const req = http.expectOne('/api/profile/preferences');
    expect(req.request.method).toBe('PATCH');
    req.flush({ ...baseProfile, preferences: updated });
  });

  it('seed() updates the cached stream without any HTTP request', (done) => {
    const seeded: UserPreferences = { ...DEFAULT_PREFERENCES, hideImages: true };
    service.seed(seeded);
    service.preferences$.subscribe(cached => {
      expect(cached).toEqual(seeded);
      done();
    });
  });
});
