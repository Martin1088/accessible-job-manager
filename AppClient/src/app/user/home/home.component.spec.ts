import { TestBed, DeferBlockBehavior, DeferBlockState } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';

import { HomeComponent } from './home.component';
import { AuthService, UserMe } from '../../core/auth.service';
import { JobPostingImportComponent } from '../../shared/job-posting-import/job-posting-import.component';
import { expectNoAxeViolations } from '../../../testing/a11y';

const user: UserMe = { sub: 'u1', name: 'Sabine Vogt', email: 'sabine@example.org', roles: ['USER'] };

describe('HomeComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTranslateService({ fallbackLang: 'en' }),
        { provide: AuthService, useValue: { me$: of(user) } },
      ],
      // The CV matching sits in a @defer block; the tests render it by hand.
      deferBlockBehavior: DeferBlockBehavior.Manual,
    }).compileComponents();
  });

  function create() {
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('should create', () => {
    expect(create().componentInstance).toBeTruthy();
  });

  it('has no axe-detectable accessibility violations', async () => {
    await expectNoAxeViolations(create());
  });

  it('shows no CV matching until the import asks for it', () => {
    const el: HTMLElement = create().nativeElement;
    expect(el.querySelector('app-cv-match')).toBeNull();
    expect(el.querySelector('a[href="/matching"]')).toBeNull();
  });

  describe('once the import asks for the matching', () => {
    const POSTING = { kind: 'url', url: 'https://jobs.example/42' } as const;

    function importer(fixture: ReturnType<typeof create>): JobPostingImportComponent {
      return fixture.debugElement.query(By.directive(JobPostingImportComponent)).componentInstance;
    }

    async function createWithMatching() {
      const fixture = create();
      importer(fixture).matchRequested.emit(POSTING);
      fixture.detectChanges();
      const [matching] = await fixture.getDeferBlocks();
      await matching.render(DeferBlockState.Complete);
      // The embedded matching asks only for the default documents.
      const http = TestBed.inject(HttpTestingController);
      http.expectOne('/api/documents/defaults').flush([]);
      fixture.detectChanges();
      return fixture;
    }

    it('shows it on the page with the posting the import already has', async () => {
      const el: HTMLElement = (await createWithMatching()).nativeElement;
      expect(el.querySelector('app-cv-match section.match-sheet')).not.toBeNull();
      expect(el.querySelector('#match-url')).toBeNull();
      expect(el.querySelector('.posting-preset')!.textContent).toContain('https://jobs.example/42');
    });

    it('offers the import\'s own finish again under it', async () => {
      const fixture = await createWithMatching();
      const submit = spyOn(importer(fixture), 'submit');
      (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.match-next button')!.click();
      expect(submit).toHaveBeenCalled();
    });

    it('keeps a single main and a single h1', async () => {
      const el: HTMLElement = (await createWithMatching()).nativeElement;
      expect(el.querySelectorAll('main').length).toBe(1);
      expect(el.querySelectorAll('h1').length).toBe(1);
    });

    it('has no axe-detectable accessibility violations', async () => {
      await expectNoAxeViolations(await createWithMatching());
    });
  });
});
