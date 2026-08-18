import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { ProfileComponent } from './profile.component';
import { UserProfile } from '../../model/user-profile';
import { PreferencesService } from '../../services/preferences.service';
import { DEFAULT_PREFERENCES, UserPreferences } from '../../model/user-preferences';

describe('ProfileComponent', () => {
  let http: HttpTestingController;
  let preferences: PreferencesService;

  const baseProfile: UserProfile = {
    userId: 'u1', name: 'Alice', email: 'a@b.com', street: 'Main St 1', postalCode: '12345',
    city: 'Berlin', phone: null, role: 'USER', advisors: [], reviewers: [],
    preferences: DEFAULT_PREFERENCES,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProfileComponent],
      providers: [
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTranslateService({ fallbackLang: 'en' })
      ]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    preferences = TestBed.inject(PreferencesService);
  });

  afterEach(() => http.verify());

  function create() {
    const fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
    http.match('/api/me').forEach(req => req.flush({ sub: 'u1', name: 'Alice', email: 'a@b.com', groups: [] }));
    return fixture;
  }

  it('should create', () => {
    const fixture = create();
    http.expectOne('/api/profile').flush(baseProfile);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('populates the sender form from the loaded profile', () => {
    const fixture = create();
    http.expectOne('/api/profile').flush(baseProfile);

    expect(fixture.componentInstance.form.getRawValue().name).toBe('Alice');
    expect(fixture.componentInstance.form.getRawValue().city).toBe('Berlin');
  });

  it('seeds the shared PreferencesService cache from the loaded profile, without a second GET', () => {
    const fixture = create();
    const stored = { ...DEFAULT_PREFERENCES, contrastMode: 'HIGH' as const };
    http.expectOne('/api/profile').flush({ ...baseProfile, preferences: stored });

    let cached: UserPreferences | undefined;
    preferences.preferences$.subscribe(p => cached = p);
    expect(cached).toEqual(stored);
  });

  it('save() does not call the API when required fields are missing', () => {
    const fixture = create();
    http.expectOne('/api/profile').flush(baseProfile);

    fixture.componentInstance.form.controls.name.setValue('');
    fixture.componentInstance.save();

    expect(fixture.componentInstance.invalid('name')).toBeTrue();
    http.expectNone({ url: '/api/profile', method: 'PUT' });
  });

  it('save() PUTs the sender form and clears the dirty/submitted state on success', () => {
    const fixture = create();
    http.expectOne('/api/profile').flush(baseProfile);

    fixture.componentInstance.form.controls.city.setValue('Munich');
    fixture.componentInstance.save();

    const req = http.expectOne({ url: '/api/profile', method: 'PUT' });
    expect(req.request.body.city).toBe('Munich');
    req.flush({ ...baseProfile, city: 'Munich' });

    expect(fixture.componentInstance.saving).toBeFalse();
    expect(fixture.componentInstance.submitted).toBeFalse();
  });
});
