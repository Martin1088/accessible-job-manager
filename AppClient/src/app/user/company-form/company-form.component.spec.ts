import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';

import { CompanyFormComponent } from './company-form.component';
import { CompanyService } from '../../services/company.service';
import { SuggestionService } from '../../services/suggestion.service';

describe('CompanyFormComponent', () => {
  let component: CompanyFormComponent;
  let fixture: ComponentFixture<CompanyFormComponent>;
  let companyServiceSpy: jasmine.SpyObj<CompanyService>;
  let suggestionServiceSpy: jasmine.SpyObj<SuggestionService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    companyServiceSpy = jasmine.createSpyObj('CompanyService', ['getAll', 'create', 'update']);
    suggestionServiceSpy = jasmine.createSpyObj('SuggestionService',
      ['company', 'location', 'position', 'applicationMethod']);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [CompanyFormComponent],
      providers: [
        { provide: CompanyService, useValue: companyServiceSpy },
        { provide: SuggestionService, useValue: suggestionServiceSpy },
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
    translate.setTranslation('en', {
      COMPANIES: {
        JSON_INVALID: 'Invalid JSON file.',
        SUGGEST_URL_REQUIRED: 'Enter a job posting URL first.',
        SUGGEST_DONE: 'Suggestion applied to the empty fields.',
        SUGGEST_NOTHING: 'Nothing to apply.',
      }
    });
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

  // ── suggestion feedback ───────────────────────────────────────────────────

  it('reports a missing URL on the trigger that was pressed, not on the others', () => {
    component.addPosition();
    component.suggestionUrl = '';

    component.suggestApplicationMethod(0);

    expect(component.suggestionErrorFor('apply-0')).toBe('Enter a job posting URL first.');
    expect(component.suggestionErrorFor('position-0')).toBe('');
    expect(component.suggestionErrorFor('company')).toBe('');
    expect(component.isSuggesting('apply-0')).toBeFalse();
  });

  it('shows the applied status next to the position that was suggested', () => {
    component.addPosition();
    component.suggestionUrl = 'https://example.test/job';
    suggestionServiceSpy.position.and.returnValue(of({ title: 'Developer' }));

    component.suggestPosition(0);

    expect(component.company.positions[0].title).toBe('Developer');
    expect(component.suggestionStatusFor('position-0')).toBe('Suggestion applied to the empty fields.');
    expect(component.suggestionStatusFor('apply-0')).toBe('');
  });

  it('says nothing was applied when the suggestion changes no field', () => {
    component.addPosition();
    component.company.positions[0].title = 'Kept';
    component.suggestionUrl = 'https://example.test/job';
    suggestionServiceSpy.position.and.returnValue(of({ title: 'Developer' }));

    component.suggestPosition(0);

    expect(component.company.positions[0].title).toBe('Kept');
    expect(component.suggestionStatusFor('position-0')).toBe('Nothing to apply.');
  });

  it('keeps an application method the user picked and still fills the blank target field', () => {
    component.addPosition();
    component.company.positions[0].applicationMethod = 'EMAIL';
    component.suggestionUrl = 'https://example.test/job';
    suggestionServiceSpy.applicationMethod.and.returnValue(
      of({ method: 'WEB_FORM' as const, applicationUrl: 'https://example.test/apply' }));

    component.suggestApplicationMethod(0);

    expect(component.company.positions[0].applicationMethod).toBe('EMAIL');
    expect(component.company.positions[0].website).toBe('https://example.test/apply');
  });

  it('fills a blank application method from the suggestion', () => {
    component.addPosition();
    component.suggestionUrl = 'https://example.test/job';
    suggestionServiceSpy.applicationMethod.and.returnValue(
      of({ method: 'EMAIL' as const, email: 'jobs@example.test' }));

    component.suggestApplicationMethod(0);

    expect(component.company.positions[0].applicationMethod).toBe('EMAIL');
    expect(component.company.positions[0].email).toBe('jobs@example.test');
    expect(component.suggestionStatusFor('apply-0')).toBe('Suggestion applied to the empty fields.');
  });

  it('renders the feedback inside the position fieldset that was triggered', () => {
    component.addPosition();
    component.suggestionUrl = '';
    fixture.detectChanges();

    component.suggestApplicationMethod(0);
    fixture.detectChanges();

    const messages = fixture.nativeElement.querySelectorAll('fieldset .suggestion-feedback');
    expect(messages.length).toBe(1);
    expect(messages[0].textContent.trim()).toBe('Enter a job posting URL first.');
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
