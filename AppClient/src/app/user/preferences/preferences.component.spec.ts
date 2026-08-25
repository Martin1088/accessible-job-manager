import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { PreferencesComponent } from './preferences.component';
import { PreferencesService } from '../../services/preferences.service';
import { DEFAULT_PREFERENCES, UserPreferences } from '../../model/user-preferences';

describe('PreferencesComponent', () => {
  let http: HttpTestingController;
  let preferences: PreferencesService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PreferencesComponent],
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

  it('should create', () => {
    const fixture = TestBed.createComponent(PreferencesComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('reads from the already-warmed PreferencesService cache, without its own GET', () => {
    const stored: UserPreferences = { ...DEFAULT_PREFERENCES, contrastMode: 'DARK', lineHeight: 1.8 };
    preferences.seed(stored);

    const fixture = TestBed.createComponent(PreferencesComponent);
    fixture.detectChanges();

    const value = fixture.componentInstance.form.getRawValue();
    expect(value.contrastMode).toBe('DARK');
    expect(value.lineHeight).toBe(1.8);
    expect(fixture.componentInstance.form.pristine).toBeTrue();
  });

  it('rejects an out-of-range line height without calling the API', () => {
    const fixture = TestBed.createComponent(PreferencesComponent);
    fixture.detectChanges();

    fixture.componentInstance.form.controls.lineHeight.setValue(5);
    fixture.componentInstance.form.markAsDirty();
    fixture.componentInstance.save();

    expect(fixture.componentInstance.invalid('lineHeight')).toBeTrue();
    http.expectNone('/api/profile/preferences');
  });

  it('save() PATCHes the changed values and clears dirty state', () => {
    const fixture = TestBed.createComponent(PreferencesComponent);
    fixture.detectChanges();

    fixture.componentInstance.form.controls.fontFamily.setValue('DYSLEXIA_FRIENDLY');
    fixture.componentInstance.form.markAsDirty();
    fixture.componentInstance.save();

    const req = http.expectOne('/api/profile/preferences');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body.fontFamily).toBe('DYSLEXIA_FRIENDLY');
    req.flush({
      userId: 'u1', name: null, email: null, street: null, postalCode: null, city: null,
      phone: null, roles: ['USER'],
      preferences: { ...DEFAULT_PREFERENCES, fontFamily: 'DYSLEXIA_FRIENDLY' },
    });

    expect(fixture.componentInstance.saving).toBeFalse();
    expect(fixture.componentInstance.form.pristine).toBeTrue();
  });

  it('reset() saves every field back to SYSTEM/defaults', () => {
    preferences.seed({ ...DEFAULT_PREFERENCES, contrastMode: 'HIGH', fontScale: 1.5 });
    const fixture = TestBed.createComponent(PreferencesComponent);
    fixture.detectChanges();

    fixture.componentInstance.reset();

    const req = http.expectOne('/api/profile/preferences');
    expect(req.request.body).toEqual({
      contrastMode: 'SYSTEM', reduceMotion: null, hideImages: null,
      fontFamily: 'SYSTEM', fontScale: 1, lineHeight: 1.5,
    });
    req.flush({
      userId: 'u1', name: null, email: null, street: null, postalCode: null, city: null,
      phone: null, roles: ['USER'], preferences: DEFAULT_PREFERENCES,
    });
  });

  it('Ctrl+S saves the form only when it is dirty', () => {
    const fixture = TestBed.createComponent(PreferencesComponent);
    fixture.detectChanges();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 's', ctrlKey: true }));
    http.expectNone('/api/profile/preferences');

    fixture.componentInstance.form.markAsDirty();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 's', ctrlKey: true }));
    const req = http.expectOne('/api/profile/preferences');
    expect(req.request.method).toBe('PATCH');
    req.flush({
      userId: 'u1', name: null, email: null, street: null, postalCode: null, city: null,
      phone: null, roles: ['USER'], preferences: DEFAULT_PREFERENCES,
    });
  });
});
