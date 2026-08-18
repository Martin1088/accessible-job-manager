import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { CompanyListComponent } from './company-list.component';
import { Company } from '../../model/company';

describe('CompanyListComponent', () => {
  let http: HttpTestingController;

  const company: Company = {
    id: 1,
    name: 'Acme GmbH',
    locations: [{ street: 'Main St 1', city: 'Berlin', postcode: '10115', country: 'Germany' }],
    positions: [{ id: 5, title: 'Developer', contactLastName: 'Schmidt', email: 'jobs@acme.example' }],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CompanyListComponent],
      providers: [
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTranslateService({ fallbackLang: 'en' })
      ]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function create() {
    const fixture = TestBed.createComponent(CompanyListComponent);
    fixture.detectChanges();
    http.expectOne('/api/companies').flush([company]);
    return fixture;
  }

  it('should create', () => {
    const fixture = create();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('viewJobPosting() defaults to the structure view and shows it even before the snapshot lookup resolves', () => {
    const fixture = create();
    const row = { companyId: 1, positionId: 5, name: 'Acme GmbH', positionTitle: 'Developer' };

    fixture.componentInstance.viewJobPosting(row);

    expect(fixture.componentInstance.viewMode).toBe('structure');
    expect(fixture.componentInstance.viewingCompany?.name).toBe('Acme GmbH');
    expect(fixture.componentInstance.viewingPosition?.title).toBe('Developer');

    http.expectOne('/api/posting/snapshot?companyPositionId=5').flush([]);
  });

  it('sets snapshotState to "none" when no snapshot exists for the position', () => {
    const fixture = create();
    fixture.componentInstance.viewJobPosting({ companyId: 1, positionId: 5, name: 'Acme GmbH', positionTitle: 'Developer' });

    http.expectOne('/api/posting/snapshot?companyPositionId=5').flush([]);

    expect(fixture.componentInstance.snapshotState).toBe('none');
  });

  it('sets snapshotState to "ready" and builds the snapshot URL when one exists', () => {
    const fixture = create();
    fixture.componentInstance.viewJobPosting({ companyId: 1, positionId: 5, name: 'Acme GmbH', positionTitle: 'Developer' });

    http.expectOne('/api/posting/snapshot?companyPositionId=5').flush([{ id: 'doc-1' }]);

    expect(fixture.componentInstance.snapshotState).toBe('ready');
    expect(fixture.componentInstance.snapshotUrl).toBe('/api/posting/snapshot/doc-1');
  });

  it('setViewMode() switches to the original view', () => {
    const fixture = create();
    fixture.componentInstance.viewJobPosting({ companyId: 1, positionId: 5, name: 'Acme GmbH', positionTitle: 'Developer' });
    http.expectOne('/api/posting/snapshot?companyPositionId=5').flush([]);

    fixture.componentInstance.setViewMode('original');

    expect(fixture.componentInstance.viewMode).toBe('original');
  });

  it('closeJobPostingView() clears the viewing row', () => {
    const fixture = create();
    fixture.componentInstance.viewJobPosting({ companyId: 1, positionId: 5, name: 'Acme GmbH', positionTitle: 'Developer' });
    http.expectOne('/api/posting/snapshot?companyPositionId=5').flush([]);

    fixture.componentInstance.closeJobPostingView();

    expect(fixture.componentInstance.viewingRow).toBeNull();
  });

  it('formatLocation() joins only the present address parts', () => {
    const fixture = create();
    const formatted = fixture.componentInstance.formatLocation({ street: 'Main St 1', city: 'Berlin', country: undefined, postcode: undefined });
    expect(formatted).toBe('Main St 1, Berlin');
  });

  it('formatContact() joins title and last name, tolerating a missing title', () => {
    const fixture = create();
    expect(fixture.componentInstance.formatContact({ id: 5, title: 'Developer', contactLastName: 'Schmidt' })).toBe('Schmidt');
  });
});
