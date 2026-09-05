import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';

import { CompanyFormComponent } from './company-form.component';
import { Company } from '../../model/company';
import { CompanyService } from '../../services/company.service';
import { SuggestionService } from '../../services/suggestion.service';
import { expectNoAxeViolations } from '../../../testing/a11y';

describe('CompanyFormComponent', () => {
  let component: CompanyFormComponent;
  let fixture: ComponentFixture<CompanyFormComponent>;
  let companyServiceSpy: jasmine.SpyObj<CompanyService>;
  let suggestionServiceSpy: jasmine.SpyObj<SuggestionService>;
  let routerSpy: jasmine.SpyObj<Router>;
  // Read by the ActivatedRoute stub, so a test can re-create the component in
  // edit mode without reconfiguring the TestBed.
  let routeId: string | null;

  beforeEach(async () => {
    routeId = null;
    companyServiceSpy = jasmine.createSpyObj('CompanyService', ['getAll', 'create', 'update']);
    suggestionServiceSpy = jasmine.createSpyObj('SuggestionService',
      ['company', 'location', 'position', 'applicationMethod']);
    // createUrlTree/serializeUrl are what routerLink calls to build an href;
    // the links to already-saved companies need them to render at all.
    routerSpy = jasmine.createSpyObj('Router', ['navigate', 'createUrlTree', 'serializeUrl']);
    routerSpy.serializeUrl.and.returnValue('/companies/edit/7');

    await TestBed.configureTestingModule({
      imports: [CompanyFormComponent],
      providers: [
        { provide: CompanyService, useValue: companyServiceSpy },
        { provide: SuggestionService, useValue: suggestionServiceSpy },
        { provide: Router, useValue: routerSpy },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => routeId } } }
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
        DUPLICATE_HINT: 'You already have a company named "{{name}}".',
        DUPLICATE_OPEN: 'Open the existing one',
        DUPLICATE_HINT_AT: 'You already have "{{name}}" in {{cities}}.',
        SAME_NAME_ELSEWHERE: 'You have a company with this name at another location:',
        SIMILAR_HEADING: 'Companies you already have with a similar name:',
      }
    });
    translate.use('en');

    // Both modes load the user's companies now - create mode matches the typed
    // name against them. Tests that care override this before detectChanges().
    companyServiceSpy.getAll.and.returnValue(of([]));

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

  // ── companies the user already has ────────────────────────────────────────

  /** Re-creates the component with a given set of already-saved companies. */
  function withExistingCompanies(companies: Company[]): void {
    companyServiceSpy.getAll.and.returnValue(of(companies));
    fixture = TestBed.createComponent(CompanyFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  const acme: Company = { id: 7, name: 'Acme GmbH', locations: [], positions: [] };

  /** Same name, two branches - the pair the location tiers are about. */
  const muellerHamburg: Company = {
    id: 11, name: 'Müller GmbH', positions: [],
    locations: [{ street: 'Hafenstr. 1', city: 'Hamburg' }],
  };
  const muellerMunich: Company = {
    id: 12, name: 'Müller GmbH', positions: [],
    locations: [{ street: 'Sendlinger Str. 4', city: 'München' }],
  };

  /** Types a city into the form the way the template does. */
  function enterCity(city: string): void {
    component.addLocation();
    component.onCityChange(component.company.locations.length - 1, city);
  }

  it('flags a name the user already has, ignoring case and spacing', () => {
    withExistingCompanies([acme]);

    component.onNameChange('  acme   gmbh ');

    expect(component.duplicateOf()?.company.id).toBe(7);
    expect(component.similarCompanies()).toEqual([]);
  });

  it('renders the duplicate hint with a link to the existing company', () => {
    withExistingCompanies([acme]);

    component.onNameChange('Acme GmbH');
    fixture.detectChanges();

    const hint = fixture.nativeElement.querySelector('#existing-companies');
    expect(hint.getAttribute('role')).toBe('status');
    expect(hint.textContent).toContain('You already have a company named "Acme GmbH".');
    expect(hint.querySelector('a').textContent.trim()).toBe('Open the existing one');
    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/companies', 'edit', 7], jasmine.anything());
  });

  it('lists similar companies while the name is still being typed', () => {
    withExistingCompanies([acme, { id: 8, name: 'Acme Ltd', locations: [], positions: [] }]);

    component.onNameChange('acme');

    expect(component.duplicateOf()).toBeUndefined();
    expect(component.similarCompanies().map(m => m.company.id)).toEqual([7, 8]);
  });

  // ── same name, different site ─────────────────────────────────────────────

  it('does not call another branch of the same name a duplicate', () => {
    withExistingCompanies([muellerHamburg]);

    component.onNameChange('Müller GmbH');
    enterCity('München');

    expect(component.duplicateOf()).toBeUndefined();
    expect(component.sameNameElsewhere().map(m => m.company.id)).toEqual([11]);
    expect(component.sameNameElsewhere()[0].cities).toBe('Hamburg');
  });

  it('flags the same name at the same city as a duplicate', () => {
    withExistingCompanies([muellerHamburg, muellerMunich]);

    component.onNameChange('Mueller GmbH');
    enterCity('Muenchen');

    expect(component.duplicateOf()?.company.id).toBe(12);
    expect(component.duplicateOf()?.cities).toBe('München');
    expect(component.sameNameElsewhere()).toEqual([]);
  });

  it('falls back to the name alone until a city has been entered', () => {
    withExistingCompanies([muellerHamburg]);

    component.onNameChange('Müller GmbH');

    expect(component.duplicateOf()?.company.id).toBe(11);
  });

  it('flags a saved company that has no city recorded to contradict the form', () => {
    withExistingCompanies([acme]);

    component.onNameChange('Acme GmbH');
    enterCity('Berlin');

    expect(component.duplicateOf()?.company.id).toBe(7);
  });

  it('re-checks the site when a location is removed again', () => {
    withExistingCompanies([muellerHamburg]);

    component.onNameChange('Müller GmbH');
    enterCity('München');
    expect(component.duplicateOf()).toBeUndefined();

    component.removeLocation(0);

    expect(component.duplicateOf()?.company.id).toBe(11);
  });

  it('holds back the looser list while an exact name match is showing', () => {
    withExistingCompanies([muellerHamburg, { id: 13, name: 'Müller Söhne', locations: [], positions: [] }]);

    component.onNameChange('Müller GmbH');
    enterCity('München');

    expect(component.sameNameElsewhere().length).toBe(1);
    expect(component.similarCompanies()).toEqual([]);
  });

  it('ignores positions entirely - a new posting always brings a new one', () => {
    withExistingCompanies([{ ...acme, positions: [{ id: 3, title: 'Developer' }] }]);

    component.onNameChange('Acme GmbH');
    component.addPosition();
    component.company.positions[0].title = 'Something else';

    expect(component.duplicateOf()?.company.id).toBe(7);
  });

  it('stays quiet until at least two characters have been typed', () => {
    withExistingCompanies([acme]);

    component.onNameChange('a');

    expect(component.similarCompanies()).toEqual([]);
    expect(component.duplicateOf()).toBeUndefined();
  });

  it('does not flag the company being edited as a duplicate of itself', () => {
    routeId = '7';
    withExistingCompanies([acme]);

    expect(component.company.name).toBe('Acme GmbH');
    expect(component.duplicateOf()).toBeUndefined();
  });

  it('flags an edited company renamed onto another one the user has', () => {
    routeId = '7';
    withExistingCompanies([acme, { id: 8, name: 'Globex', locations: [], positions: [] }]);

    component.onNameChange('globex');

    expect(component.duplicateOf()?.company.id).toBe(8);
  });

  // ── save (create mode) ────────────────────────────────────────────────────

  it('save calls create and navigates on success', () => {
    companyServiceSpy.create.and.returnValue(of({ id: 1, name: 'Acme', locations: [], positions: [] }));
    component.company.name = 'Acme';

    component.save();

    expect(companyServiceSpy.create).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/companies']);
  });

  it('has no axe-detectable accessibility violations', async () => {
    fixture.detectChanges();
    await expectNoAxeViolations(fixture);
  });
});
