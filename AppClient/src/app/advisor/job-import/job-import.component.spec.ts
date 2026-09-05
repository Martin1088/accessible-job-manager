import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { JobImportComponent } from './job-import.component';
import { ImportedPosting } from '../../shared/job-posting-import/job-posting-import.component';
import { expectNoAxeViolations } from '../../../testing/a11y';

describe('JobImportComponent', () => {
  let fixture: ComponentFixture<JobImportComponent>;
  let component: JobImportComponent;
  let routerSpy: jasmine.SpyObj<Router>;

  const POSTING: ImportedPosting = {
    company: {
      name: 'MetalBear',
      locations: [{ street: '', city: 'London' }],
      positions: [{ title: 'Backend Engineer' }],
    },
    sourceJobUrl: 'https://www.comeet.com/jobs/metalbear/8A.002/x/1C.176',
  };

  beforeEach(async () => {
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [JobImportComponent],
      providers: [
        { provide: Router, useValue: routerSpy },
        provideTranslateService({ fallbackLang: 'en' }),
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(JobImportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  /**
   * Extraction only - the same handoff the user side's home page makes to
   * /companies/new, but to the advisor's own catalogue route. Reviewing,
   * editing and saving happen on the full company form, not here.
   */
  it('hands an imported posting straight to the advisor company form', () => {
    component.onImported(POSTING);

    expect(routerSpy.navigate).toHaveBeenCalledWith(['/advisor/companies/new'], {
      state: { company: POSTING.company, sourceJobUrl: POSTING.sourceJobUrl },
    });
  });

  it('has no axe-detectable accessibility violations', async () => {
    await expectNoAxeViolations(fixture);
  });
});
