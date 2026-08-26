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
    city: 'Berlin', phone: null, roles: ['USER'],
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
    http.match('/api/me').forEach(req => req.flush({ sub: 'u1', name: 'Alice', email: 'a@b.com', roles: ['USER'] }));
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

  describe('data export', () => {
    /**
     * Stops the anchor click from navigating the Karma runner, and records the name it
     * would have saved under. Spying on the prototype rather than on
     * `document.createElement`: the latter is what Angular renders through, and
     * stubbing it breaks every component in the fixture.
     */
    function stubDownload(): string[] {
      spyOn(URL, 'createObjectURL').and.returnValue('blob:stub');
      spyOn(URL, 'revokeObjectURL');
      const saved: string[] = [];
      spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(function (this: HTMLAnchorElement) {
        saved.push(this.download);
      });
      return saved;
    }

    function exportRequest() {
      return http.expectOne(r => r.url === '/api/export/companies');
    }

    it('requests the workbook and hands the downloaded file its server-given name', () => {
      const saved = stubDownload();
      const fixture = create();
      http.expectOne('/api/profile').flush(baseProfile);

      fixture.componentInstance.exportData('XLSX');
      expect(fixture.componentInstance.exporting).toBe('XLSX');

      const req = exportRequest();
      expect(req.request.headers.get('Accept'))
        .toBe('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
      req.flush(new Blob([]), {
        headers: { 'Content-Disposition': 'attachment; filename=companies-export-2026-08-23.xlsx' },
      });

      expect(saved).toEqual(['companies-export-2026-08-23.xlsx']);
      expect(fixture.componentInstance.exporting).toBeNull();
      expect(fixture.componentInstance.exportError).toBeFalse();
    });

    it('requests CSV when the CSV button is used', () => {
      stubDownload();
      const fixture = create();
      http.expectOne('/api/profile').flush(baseProfile);

      fixture.componentInstance.exportData('CSV');

      const req = exportRequest();
      expect(req.request.headers.get('Accept')).toBe('text/csv');
      req.flush(new Blob(['a,b']));
    });

    it('sends the language the UI is being read in', () => {
      stubDownload();
      const fixture = create();
      http.expectOne('/api/profile').flush(baseProfile);

      fixture.componentInstance.exportData('CSV');

      // provideTranslateService above bootstraps with fallbackLang 'en'.
      expect(exportRequest().request.params.get('language')).toBe('ENGLISH');
      http.expectNone(r => r.url === '/api/export/companies');
    });

    it('ignores a second press while an export is still running', () => {
      stubDownload();
      const fixture = create();
      http.expectOne('/api/profile').flush(baseProfile);

      fixture.componentInstance.exportData('CSV');
      fixture.componentInstance.exportData('XLSX');

      // One in-flight request, still the CSV one: the second press was dropped.
      const req = exportRequest();
      expect(req.request.headers.get('Accept')).toBe('text/csv');
      req.flush(new Blob(['a,b']));
    });

    it('surfaces a failed export and releases the buttons', () => {
      const saved = stubDownload();
      const fixture = create();
      http.expectOne('/api/profile').flush(baseProfile);

      fixture.componentInstance.exportData('CSV');
      exportRequest().flush(new Blob([]), { status: 500, statusText: 'Server Error' });

      expect(fixture.componentInstance.exportError).toBeTrue();
      expect(fixture.componentInstance.exporting).toBeNull();
      expect(saved).toEqual([]);
    });
  });
});
