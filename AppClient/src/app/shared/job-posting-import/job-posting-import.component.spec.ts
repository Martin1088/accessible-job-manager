import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { ImportedPosting, JobPostingImportComponent } from './job-posting-import.component';
import { expectNoAxeViolations } from '../../../testing/a11y';

describe('JobPostingImportComponent', () => {
  let fixture: ComponentFixture<JobPostingImportComponent>;
  let component: JobPostingImportComponent;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JobPostingImportComponent],
      providers: [
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
        provideTranslateService({ fallbackLang: 'en' }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(JobPostingImportComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  function search(url = 'https://jobs.example.com/42'): void {
    component.jobUrl = url;
    component.searchJobPosting();
  }

  it('runs both extractions in parallel for one URL', () => {
    search();

    http.expectOne(r => r.url === '/api/posting/overview').flush(
      { title: 'Backend Engineer', company: 'MetalBear', location: 'London', employmentType: 'Full-time' });
    http.expectOne(r => r.url === '/api/posting/full-chain').flush(
      { company: { name: 'MetalBear', locations: [{ city: 'London', street: null }], positions: [{ title: 'Backend Engineer' }] },
        sourceJobId: null, postedAt: null, deadline: null, employmentType: 'Full-time' });

    expect(component.hasAnyResult).toBeTrue();
  });

  /** The whole reason the component is shared: the host decides what happens next. */
  it('emits the merged posting rather than acting on it itself', () => {
    let emitted: ImportedPosting | undefined;
    component.imported.subscribe(p => emitted = p);

    search();
    http.expectOne(r => r.url === '/api/posting/overview').flush(
      { title: null, company: null, location: null, employmentType: null });
    http.expectOne(r => r.url === '/api/posting/full-chain').flush(
      { company: { name: 'MetalBear', locations: [{ city: 'London', street: 'Hauptstr. 1' }], positions: [{ title: 'Backend Engineer', email: 'jobs@metalbear.co' }] },
        sourceJobId: null, postedAt: null, deadline: null, employmentType: null });

    component.submit();

    expect(emitted?.company.name).toBe('MetalBear');
    expect(emitted?.company.positions[0].title).toBe('Backend Engineer');
    expect(emitted?.company.locations[0].city).toBe('London');
    expect(emitted?.sourceJobUrl).toBe('https://jobs.example.com/42');
  });

  it('emits nothing when there is no result to submit', () => {
    let emitted = false;
    component.imported.subscribe(() => emitted = true);

    component.submit();

    expect(emitted).toBeFalse();
  });

  it('classifies a blocked host rather than blaming the URL', () => {
    search();
    http.expectOne(r => r.url === '/api/posting/overview').flush(
      { message: 'This site does not allow automated access (403).' },
      { status: 502, statusText: 'Bad Gateway' });
    http.expectOne(r => r.url === '/api/posting/full-chain').flush({}, { status: 502, statusText: 'Bad Gateway' });

    expect(component.jobSearchFailure?.kind).toBe('reported');
    expect(component.jobSearchFailure?.message).toContain('automated access');
  });

  /**
   * The text path cannot fill the full-chain column - that extraction follows
   * links out of a fetched page, and there is no page here.
   */
  it('drops the full-chain result on the pasted-text path', () => {
    component.postingText = 'Wir suchen eine Fachkraft.'.repeat(10);
    component.searchFromText();

    http.expectOne('/api/posting/overview-text').flush(
      { title: 'Fachkraft', company: null, location: null, employmentType: null });

    expect(component.fullChainResult).toBeNull();
    expect(component.selectedSource.name).toBe('overview');
  });

  it('has no axe-detectable accessibility violations', async () => {
    await expectNoAxeViolations(fixture);
  });
});
