import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { JobSearchComponent } from './job-search.component';

describe('JobSearchComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JobSearchComponent],
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
    const fixture = TestBed.createComponent(JobSearchComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('should create and show the not-configured notice when the source is off', () => {
    const fixture = create();
    http.expectOne('/api/advisor/job-search/status')
      .flush({ configured: false, source: 'adzuna', country: 'de', attribution: 'Jobs by Adzuna' });
    fixture.detectChanges();

    expect(fixture.componentInstance.status?.configured).toBe(false);
    expect(fixture.componentInstance.statusLoading).toBe(false);
  });

  it('loads categories once the source reports itself configured', () => {
    const fixture = create();
    http.expectOne('/api/advisor/job-search/status')
      .flush({ configured: true, source: 'adzuna', country: 'de', attribution: 'Jobs by Adzuna' });

    http.expectOne('/api/advisor/job-search/categories?country=de')
      .flush([{ tag: 'it-jobs', label: 'IT Jobs' }]);

    expect(fixture.componentInstance.categories).toEqual([{ tag: 'it-jobs', label: 'IT Jobs' }]);
  });

  it('refuses to search with no keywords, location or category', () => {
    const fixture = create();
    http.expectOne('/api/advisor/job-search/status')
      .flush({ configured: true, source: 'adzuna', country: 'de', attribution: 'Jobs by Adzuna' });
    http.expectOne('/api/advisor/job-search/categories?country=de').flush([]);

    fixture.componentInstance.search();

    expect(fixture.componentInstance.searchError).toBeTruthy();
    // No request is issued for a search with no criteria; afterEach's http.verify() confirms it.
  });

  it('searches and stores the results page', () => {
    const fixture = create();
    http.expectOne('/api/advisor/job-search/status')
      .flush({ configured: true, source: 'adzuna', country: 'de', attribution: 'Jobs by Adzuna' });
    http.expectOne('/api/advisor/job-search/categories?country=de').flush([]);

    fixture.componentInstance.form.what = 'java';
    fixture.componentInstance.search();

    const req = http.expectOne(r => r.url === '/api/advisor/job-search' && r.params.get('what') === 'java');
    req.flush({
      source: 'adzuna', totalCount: 1, page: 1, resultsPerPage: 20,
      hits: [{
        id: '1', title: 'Java Developer', company: 'Acme', location: 'Berlin',
        url: 'https://example.com/1', summary: null, created: '2026-08-01T00:00:00Z',
        salaryMin: null, salaryMax: null, salaryPredicted: false,
        contractType: null, contractTime: null, category: null, source: 'adzuna'
      }],
      attribution: 'Jobs by Adzuna'
    });

    expect(fixture.componentInstance.results?.totalCount).toBe(1);
    expect(fixture.componentInstance.hasSearched).toBe(true);
  });

  // Angular's number value accessor puts a number in a touched numeric field and
  // null in a cleared one. Reading those as strings threw inside search(), so no
  // request was ever sent - the form looked filled in and nothing happened.
  it('searches when the numeric filters hold a number or a cleared null', () => {
    const fixture = create();
    http.expectOne('/api/advisor/job-search/status')
      .flush({ configured: true, source: 'adzuna', country: 'de', attribution: 'Jobs by Adzuna' });
    http.expectOne('/api/advisor/job-search/categories?country=de').flush([]);

    const component = fixture.componentInstance;
    component.form.what = 'java';
    component.form.distanceKm = 25;      // touched: a number, not a string
    component.form.salaryMin = 50000;
    component.form.maxDaysOld = null;    // cleared: null, not ''

    component.search();

    const req = http.expectOne(r => r.url === '/api/advisor/job-search');
    expect(req.request.params.get('distanceKm')).toBe('25');
    expect(req.request.params.get('salaryMin')).toBe('50000');
    expect(req.request.params.get('maxDaysOld')).toBeNull();
    req.flush({
      source: 'adzuna', totalCount: 0, page: 1, resultsPerPage: 20,
      hits: [], attribution: 'Jobs by Adzuna'
    });

    expect(component.searchError).toBe('');
  });
});
