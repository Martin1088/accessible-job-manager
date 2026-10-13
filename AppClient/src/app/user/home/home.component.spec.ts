import { TestBed, DeferBlockBehavior, DeferBlockState } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';

import { HomeComponent } from './home.component';
import { AuthService, UserMe } from '../../core/auth.service';
import { DEMO_MODE } from '../../demo/demo-mode';
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
      // The CV matching sits in a @defer block; the demo case renders it by hand.
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

  it('links to the CV matching page outside the demo', () => {
    const fixture = create();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('a[href="/matching"]')).not.toBeNull();
    expect(el.querySelector('app-cv-match')).toBeNull();
  });

  describe('in the demo', () => {
    beforeEach(() => TestBed.overrideProvider(DEMO_MODE, { useValue: true }));

    async function createWithMatching() {
      const fixture = create();
      const [matching] = await fixture.getDeferBlocks();
      await matching.render(DeferBlockState.Complete);
      // The embedded matching asks for the user's CVs and their defaults.
      const http = TestBed.inject(HttpTestingController);
      http.expectOne(r => r.url === '/api/documents' && r.params.get('type') === 'CV').flush([]);
      http.expectOne('/api/documents/defaults').flush([]);
      fixture.detectChanges();
      return fixture;
    }

    it('shows the CV matching on the page instead of the link', async () => {
      const el: HTMLElement = (await createWithMatching()).nativeElement;
      expect(el.querySelector('app-cv-match section.match-sheet')).not.toBeNull();
      expect(el.querySelector('a[href="/matching"]')).toBeNull();
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
