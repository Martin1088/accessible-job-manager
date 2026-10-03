import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { PositionDetailsComponent, formatContact, formatLocation } from './position-details.component';
import { Company, CompanyPosition } from '../../model/company';
import { expectNoAxeViolations } from '../../../testing/a11y';

const COMPANY: Company = {
  id: 1,
  name: 'Acme GmbH',
  locations: [{ street: 'Main St 1', city: 'Berlin', postcode: '10115', country: 'Germany' }],
  positions: [],
};

const FULL: CompanyPosition = {
  id: 5,
  title: 'Developer',
  contactTitle: 'Dr.',
  contactLastName: 'Schmidt',
  email: 'jobs@acme.example',
  website: 'https://acme.example/jobs/5',
  applicationMethod: 'EMAIL',
  applyLanguage: 'GERMAN',
  createdAt: '2026-03-01T09:00:00Z',
  notes: 'Ask about remote work',
};

describe('PositionDetailsComponent', () => {
  let fixture: ComponentFixture<PositionDetailsComponent>;

  function render(company: Company, position: CompanyPosition): HTMLElement {
    fixture.componentRef.setInput('company', company);
    fixture.componentRef.setInput('position', position);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  function terms(el: HTMLElement): string[] {
    return Array.from(el.querySelectorAll('dt')).map(dt => dt.textContent!.trim());
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PositionDetailsComponent],
      providers: [provideTranslateService({ fallbackLang: 'en' })],
    }).compileComponents();
    fixture = TestBed.createComponent(PositionDetailsComponent);
  });

  it('shows every field a complete position has', () => {
    const el = render(COMPANY, FULL);
    expect(terms(el)).toEqual([
      'COMPANIES.COL_NAME', 'COMPANIES.COL_POSITION', 'COMPANIES.LOCATIONS_HEADING',
      'COMPANIES.CONTACT_LAST_NAME', 'COMPANIES.EMAIL', 'COMPANIES.WEBSITE',
      'COMPANIES.APPLY_METHOD', 'COMPANIES.APPLY_LANGUAGE', 'COMPANIES.COL_ADDED', 'COMPANIES.NOTES',
    ]);
    expect(el.querySelector('a[href="mailto:jobs@acme.example"]')).toBeTruthy();
  });

  it('leaves out the fields that are empty, and an unknown application method', () => {
    const el = render({ ...COMPANY, locations: [] }, { id: 6, title: 'Tester', applicationMethod: 'UNKNOWN' });
    expect(terms(el)).toEqual(['COMPANIES.COL_NAME', 'COMPANIES.COL_POSITION']);
  });

  it('formatLocation() joins only the present address parts', () => {
    expect(formatLocation({ street: 'Main St 1', city: 'Berlin' })).toBe('Main St 1, Berlin');
  });

  it('formatContact() joins title and last name, tolerating a missing title', () => {
    expect(formatContact({ title: 'x', contactTitle: 'Dr.', contactLastName: 'Schmidt' })).toBe('Dr. Schmidt');
    expect(formatContact({ title: 'x', contactLastName: 'Schmidt' })).toBe('Schmidt');
  });

  it('has no axe-detectable accessibility violations', async () => {
    render(COMPANY, FULL);
    await expectNoAxeViolations(fixture);
  });
});
