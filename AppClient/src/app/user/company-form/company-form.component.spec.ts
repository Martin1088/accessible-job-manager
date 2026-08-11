import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';

import { CompanyFormComponent } from './company-form.component';
import { CompanyService } from '../../services/company.service';

describe('CompanyFormComponent', () => {
  let component: CompanyFormComponent;
  let fixture: ComponentFixture<CompanyFormComponent>;
  let companyServiceSpy: jasmine.SpyObj<CompanyService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    companyServiceSpy = jasmine.createSpyObj('CompanyService', ['getAll', 'create', 'update']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [CompanyFormComponent],
      providers: [
        { provide: CompanyService, useValue: companyServiceSpy },
        { provide: Router, useValue: routerSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => null } } }
        },
        provideTranslateService({ fallbackLang: 'en' }),
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
      ]
    }).compileComponents();

    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', { COMPANIES: { JSON_INVALID: 'Invalid JSON file.' } });
    translate.use('en');

    fixture = TestBed.createComponent(CompanyFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should start in manual mode with empty company', () => {
    expect(component.importMode).toBeFalse();
    expect(component.company.name).toBe('');
    expect(component.company.locations).toEqual([]);
    expect(component.company.positions).toEqual([]);
  });

  // ── location / position helpers ───────────────────────────────────────────

  it('addLocation should append an empty location', () => {
    component.addLocation();
    expect(component.company.locations.length).toBe(1);
    expect(component.company.locations[0].street).toBe('');
  });

  it('removeLocation should remove by index', () => {
    component.addLocation();
    component.addLocation();
    component.removeLocation(0);
    expect(component.company.locations.length).toBe(1);
  });

  it('addPosition should append an empty position', () => {
    component.addPosition();
    expect(component.company.positions.length).toBe(1);
    expect(component.company.positions[0].title).toBe('');
  });

  it('removePosition should remove by index', () => {
    component.addPosition();
    component.addPosition();
    component.removePosition(1);
    expect(component.company.positions.length).toBe(1);
  });

  // ── cancel ────────────────────────────────────────────────────────────────

  it('cancel should navigate to /companies', () => {
    component.cancel();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/companies']);
  });

  // ── JSON import ───────────────────────────────────────────────────────────

  it('onJsonFileSelected parses valid JSON and exits import mode', () => {
    const payload = JSON.stringify({ name: 'Acme GmbH', locations: [{ street: 'Main St', city: 'Berlin' }], positions: [] });

    const mockReader: Partial<FileReader> = {
      readAsText: function () {
        Object.defineProperty(this, 'result', { value: payload, writable: true });
        (this as any).onload!({} as ProgressEvent);
      }
    } as any;
    spyOn(window as any, 'FileReader').and.returnValue(mockReader);

    const file = new File([payload], 'company.json');
    const event = { target: { files: [file] } } as unknown as Event;

    component.onJsonFileSelected(event);

    expect(component.company.name).toBe('Acme GmbH');
    expect(component.company.locations.length).toBe(1);
    expect(component.importMode).toBeFalse();
    expect(component.jsonError).toBe('');
  });

  it('onJsonFileSelected sets jsonError on invalid JSON', () => {
    const mockReader: Partial<FileReader> = {
      readAsText: function () {
        Object.defineProperty(this, 'result', { value: 'not valid json', writable: true });
        (this as any).onload!({} as ProgressEvent);
      }
    } as any;
    spyOn(window as any, 'FileReader').and.returnValue(mockReader);

    const file = new File(['not valid json'], 'bad.json');
    const event = { target: { files: [file] } } as unknown as Event;

    component.onJsonFileSelected(event);

    expect(component.jsonError).toBe('Invalid JSON file.');
    expect(component.company.name).toBe('');
  });

  it('onJsonFileSelected handles missing locations/positions gracefully', () => {
    const payload = JSON.stringify({ name: 'Solo Corp' });

    const mockReader: Partial<FileReader> = {
      readAsText: function () {
        Object.defineProperty(this, 'result', { value: payload, writable: true });
        (this as any).onload!({} as ProgressEvent);
      }
    } as any;
    spyOn(window as any, 'FileReader').and.returnValue(mockReader);

    const file = new File([payload], 'partial.json');
    component.onJsonFileSelected({ target: { files: [file] } } as unknown as Event);

    expect(component.company.name).toBe('Solo Corp');
    expect(component.company.locations).toEqual([]);
    expect(component.company.positions).toEqual([]);
  });

  it('onJsonFileSelected does nothing when no file is selected', () => {
    const event = { target: { files: [] } } as unknown as Event;
    component.onJsonFileSelected(event);
    expect(component.jsonError).toBe('');
    expect(component.company.name).toBe('');
  });

  // ── save (create mode) ────────────────────────────────────────────────────

  it('save calls create and navigates on success', () => {
    companyServiceSpy.create.and.returnValue(of({ id: 1, name: 'Acme', locations: [], positions: [] }));
    component.company.name = 'Acme';

    component.save();

    expect(companyServiceSpy.create).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/companies']);
  });
});
