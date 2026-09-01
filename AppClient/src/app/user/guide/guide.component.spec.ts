import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { GuideComponent } from './guide.component';
import { AppRole } from '../../core/auth.service';
import { expectNoAxeViolations } from '../../../testing/a11y';

/**
 * The guide is navigated by heading, not by links on the page. Two attempts at
 * an in-page register were removed before these specs were written, and both
 * failure modes are worth remembering because they are invisible to a sighted
 * click-through:
 *
 * 1. `href="#section"` resolves against `<base href="/">`, not against the
 *    current URL, so every entry navigated to `/` and dropped the reader on the
 *    dashboard.
 * 2. `routerLink` + `fragment` with `anchorScrolling` fixed that, but the router
 *    only moves the viewport. A screen reader follows focus, so the page
 *    scrolled while the reader's cursor stayed where it was.
 *
 * What replaces them is structure: one `h1`, one `h2` per section in document
 * order, `h3` only inside a section. That is what a screen reader's heading list
 * and the `h` key walk, and it cannot drift out of sync with the content the way
 * a hand-maintained register can.
 */
describe('GuideComponent', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<GuideComponent>;

  /**
   * `AuthService.me$` is `shareReplay(1)` on a root-provided service, so the
   * roles are fixed for the lifetime of an injector. A spec that renders the
   * guide as more than one role therefore needs a fresh TestBed per role.
   */
  async function renderAs(roles: AppRole[]): Promise<HTMLElement> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
        provideTranslateService({ fallbackLang: 'en' })
      ]
    }).compileComponents();

    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(GuideComponent);

    // The request is made when the template's first `| async` subscribes.
    fixture.detectChanges();
    http.expectOne('/api/me').flush({ sub: 'sub-1', name: 'Test Person', email: 't@example.org', roles });
    fixture.detectChanges();

    return fixture.nativeElement as HTMLElement;
  }

  const sections = (el: HTMLElement) =>
    Array.from(el.querySelectorAll<HTMLElement>('.guide-sections section'));

  /** Every heading on the page, in document order, as its level. */
  const headingLevels = (el: HTMLElement) =>
    Array.from(el.querySelectorAll<HTMLElement>('h1, h2, h3, h4, h5, h6'))
      .map(h => Number(h.tagName[1]));

  afterEach(() => http.verify());

  const ROLE_SETS: AppRole[][] = [['USER'], ['ADVISOR'], ['REVIEWER'], ['USER', 'ADVISOR', 'REVIEWER']];

  it('should create', async () => {
    const el = await renderAs(['USER']);
    expect(el.querySelector('h1')).toBeTruthy();
  });

  // --- Heading navigation ------------------------------------------------

  for (const roles of ROLE_SETS) {
    it(`gives every section exactly one h2 to be found by, as ${roles.join('+')}`, async () => {
      const el = await renderAs(roles);
      const found = sections(el);

      expect(found.length).toBeGreaterThan(0);
      for (const section of found) {
        expect(section.querySelectorAll(':scope > h2').length)
          .withContext(`section #${section.id}`)
          .toBe(1);
      }
    });

    it(`keeps the heading order walkable, as ${roles.join('+')}`, async () => {
      const levels = headingLevels(await renderAs(roles));

      // A reader moving by heading relies on the ranks alone to tell them where
      // they are, so the page must open at h1 and never skip a level down.
      expect(levels[0]).withContext('first heading on the page').toBe(1);
      expect(levels.filter(l => l === 1).length).withContext('h1 count').toBe(1);

      levels.slice(1).forEach((level, i) => {
        expect(level - levels[i])
          .withContext(`h${levels[i]} -> h${level} at heading ${i + 2}`)
          .toBeLessThanOrEqual(1);
      });
    });

    it(`names every section by its own heading, as ${roles.join('+')}`, async () => {
      const el = await renderAs(roles);

      for (const section of sections(el)) {
        // The accessible name of the region has to resolve to a heading that is
        // actually present, or the section is announced as an unnamed region.
        const labelledBy = section.getAttribute('aria-labelledby');
        expect(labelledBy).withContext(`section #${section.id}`).toBeTruthy();
        expect(el.querySelector(`#${labelledBy}`))
          .withContext(`#${labelledBy} referenced by section #${section.id}`)
          .toBeTruthy();
      }
    });
  }

  it('carries no in-page links to go stale', async () => {
    const el = await renderAs(['USER', 'ADVISOR', 'REVIEWER']);

    // Guards the decision above rather than the absence of anchors for its own
    // sake: a fragment link reintroduced here would scroll for a sighted reader
    // and silently do nothing for a screen reader.
    const fragmentLinks = Array.from(el.querySelectorAll('a'))
      .filter(a => (a.getAttribute('href') ?? '').includes('#'));

    expect(fragmentLinks.map(a => a.getAttribute('href'))).toEqual([]);
  });

  // --- Role gating -------------------------------------------------------

  it('shows the user-only sections to a user and withholds them from an advisor', async () => {
    const asUser = await renderAs(['USER']);
    expect(asUser.querySelector('#writing-a-letter')).toBeTruthy();
    expect(asUser.querySelector('#your-profile')).toBeTruthy();
    expect(asUser.querySelector('#advisor-guide')).toBeNull();

    const asAdvisor = await renderAs(['ADVISOR']);
    expect(asAdvisor.querySelector('#writing-a-letter')).toBeNull();
    expect(asAdvisor.querySelector('#your-profile')).toBeNull();
    expect(asAdvisor.querySelector('#advisor-guide')).toBeTruthy();
    // Display & reading is not role-gated: it describes the account menu, which
    // everyone who can reach this page has.
    expect(asAdvisor.querySelector('#display-preferences')).toBeTruthy();
  });

  it('gives the reviewer section only to a reviewer', async () => {
    expect((await renderAs(['REVIEWER'])).querySelector('#reviewer-guide')).toBeTruthy();
    expect((await renderAs(['USER'])).querySelector('#reviewer-guide')).toBeNull();
  });

  it('has no accessibility violations', async () => {
    await renderAs(['USER', 'ADVISOR', 'REVIEWER']);
    await expectNoAxeViolations(fixture);
  });
});
