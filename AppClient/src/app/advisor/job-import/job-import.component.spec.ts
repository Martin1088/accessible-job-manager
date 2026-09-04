import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { JobImportComponent } from './job-import.component';
import { JobPostingImportStore } from '../../services/job-posting-import.store';
import { ImportedPosting } from '../../shared/job-posting-import/job-posting-import.component';
import { expectNoAxeViolations } from '../../../testing/a11y';

describe('JobImportComponent', () => {
  let fixture: ComponentFixture<JobImportComponent>;
  let component: JobImportComponent;
  let http: HttpTestingController;
  let store: JobPostingImportStore;

  const POSTING: ImportedPosting = {
    company: {
      name: 'MetalBear',
      locations: [{ street: '', city: 'London' }],
      positions: [{ title: 'Backend Engineer' }],
    },
    sourceJobUrl: 'https://www.comeet.com/jobs/metalbear/8A.002/x/1C.176',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [JobImportComponent],
      providers: [
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTranslateService({ fallbackLang: 'en' }),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(JobImportComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    store = TestBed.inject(JobPostingImportStore);
    fixture.detectChanges();
    http.expectOne('/api/advisor/my-users').flush([
      { userId: 'sub-1', name: 'Anna Weber', email: 'anna@example.org' },
    ]);
  });

  afterEach(() => http.verify());

  /** Company -> position id -> suggestion: each call needs the one before it. */
  function completeSave(): void {
    component.onImported(POSTING);
    component.targetUserId = 'sub-1';
    component.saveAndSuggest();

    http.expectOne({ method: 'POST', url: '/api/companies' }).flush({
      id: 7, name: 'MetalBear',
      locations: [{ street: '', city: 'London' }],
      positions: [{ id: 42, title: 'Backend Engineer' }],
    });
  }

  it('cannot send before a posting has been imported', () => {
    component.targetUserId = 'sub-1';
    expect(component.canSend).toBeFalse();
  });

  it('cannot send before a user has been chosen', () => {
    component.onImported(POSTING);
    expect(component.canSend).toBeFalse();
  });

  it('files the suggestion against the position the company create returned', () => {
    completeSave();

    // Snapshot is fire-and-forget alongside the suggestion.
    http.expectOne(r => r.url === '/api/posting/snapshot').flush({});
    const suggestion = http.expectOne({ method: 'POST', url: '/api/advisor/suggestions' });
    expect(suggestion.request.body).toEqual({
      targetUserId: 'sub-1', companyPositionId: 42, message: '',
    });
    suggestion.flush({ id: 1 });

    expect(component.savedPositionTitle).toBe('Backend Engineer');
    expect(component.savedUserName).toBe('Anna Weber');
    expect(component.pending).toBeNull();
  });

  /**
   * The snapshot is the archived copy of the posting, not the point of the
   * page. A Gotenberg that is down must not mean the advisor cannot suggest
   * anything at all.
   */
  it('still sends the suggestion when the snapshot fails', () => {
    completeSave();

    http.expectOne(r => r.url === '/api/posting/snapshot')
      .flush('down', { status: 502, statusText: 'Bad Gateway' });
    http.expectOne({ method: 'POST', url: '/api/advisor/suggestions' }).flush({ id: 1 });

    expect(component.savedPositionTitle).toBe('Backend Engineer');
    expect(component.saveFailure).toBeNull();
  });

  it('uploads the held PDF instead of re-rendering the URL when the import came from a file', () => {
    store.hold(new File(['x'], 'posting.pdf', { type: 'application/pdf' }));
    completeSave();

    const upload = http.expectOne({ method: 'POST', url: '/api/posting/snapshot/upload' });
    expect((upload.request.body as FormData).get('companyPositionId')).toBe('42');
    upload.flush({});
    http.expectOne({ method: 'POST', url: '/api/advisor/suggestions' }).flush({ id: 1 });
  });

  it('reports a failed save and keeps the posting so it can be retried', () => {
    component.onImported(POSTING);
    component.targetUserId = 'sub-1';
    component.saveAndSuggest();

    http.expectOne({ method: 'POST', url: '/api/companies' })
      .flush({ message: 'Name must not be empty' }, { status: 400, statusText: 'Bad Request' });

    expect(component.saveFailure?.kind).toBe('reported');
    expect(component.saveFailure?.message).toBe('Name must not be empty');
    expect(component.pending).not.toBeNull();
    expect(component.saving).toBeFalse();
  });

  it('has no axe-detectable accessibility violations with the recipient form open', async () => {
    component.onImported(POSTING);
    fixture.detectChanges();
    await expectNoAxeViolations(fixture);
  });
});
