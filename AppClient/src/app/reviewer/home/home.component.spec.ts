import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { HomeComponent } from './home.component';
import { Relationship } from '../../model/relationship';
import { expectNoAxeViolations } from '../../../testing/a11y';

const link = (id: string, overrides: Partial<Relationship>): Relationship => ({
  id,
  applicantId: 'u1',
  applicantName: 'Anna Berg',
  counterpartId: 'reviewer-1',
  counterpartName: 'Amira Sayed',
  kind: 'REVIEWER',
  status: 'ACTIVE',
  createdAt: '2026-09-01T10:00:00',
  ...overrides,
});

const LINKS: Relationship[] = [
  link('rel-1', {}),
  link('rel-2', { applicantId: 'u2', applicantName: 'Ben Koch', status: 'REQUESTED' }),
  // An advisor request to the same person belongs on the advisor dashboard, not here.
  link('rel-3', { applicantId: 'u3', applicantName: 'Cleo Maas', kind: 'ADVISOR', status: 'REQUESTED' }),
];

const SHARED = [
  {
    userId: 'u1',
    name: 'anna Berg',
    email: 'anna.berg@example.org',
    documents: [
      { id: 'd1', label: 'CV', filename: 'cv.pdf', type: 'CV', grantedAt: '2026-09-01T10:05:00' },
      { id: 'd2', label: 'Cover letter', filename: 'letter.pdf', type: 'COVER_LETTER', grantedAt: '2026-09-02' },
    ],
  },
];

describe('HomeComponent (Reviewer)', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        provideHttpClient(withXhr()),
        provideHttpClientTesting(),
        provideRouter([]),
        provideTranslateService({ fallbackLang: 'en' })
      ]
    }).compileComponents();
  });

  function render() {
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/reviewer/users').flush(SHARED);
    http.expectOne('/api/relationships/incoming').flush(LINKS);
    fixture.detectChanges();
    return { fixture, http, el: fixture.nativeElement as HTMLElement };
  }

  const bodyRows = (table: Element) => Array.from(table.querySelectorAll('tbody tr'));

  it('should create', () => {
    const fixture = TestBed.createComponent(HomeComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('has no axe-detectable accessibility violations', async () => {
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    await expectNoAxeViolations(fixture);
  });

  it('splits reviewer links into My Users and requests, and counts them in the reference line', async () => {
    const { fixture, http, el } = render();

    const figures = Array.from(el.querySelectorAll('.reference-line dd')).map(dd => dd.textContent?.trim());
    expect(figures.slice(0, 3)).toEqual(['1', '1', '2']);

    const [myUsers, requests] = Array.from(el.querySelectorAll('app-data-table'));
    const userCells = Array.from(bodyRows(myUsers)[0].querySelectorAll('td')).map(td => td.textContent?.trim());
    expect(userCells).toEqual(['Anna Berg', '2']);
    expect(bodyRows(requests).length).toBe(1);
    expect(bodyRows(requests)[0].textContent).toContain('Ben Koch');

    expect(el.querySelector('.sender__monogram')?.textContent?.trim()).toBe('A');
    expect(bodyRows(el.querySelector('.doc-table')!).length).toBe(2);

    await expectNoAxeViolations(fixture);
    http.verify();
  });

  it('accepts a review request and reloads the links', () => {
    const { el, http } = render();

    const accept = Array.from(el.querySelectorAll<HTMLButtonElement>('app-data-table button'))
      .find(b => b.textContent?.trim() === 'REVIEWER.REQ_ACCEPT');
    accept!.click();

    const call = http.expectOne('/api/relationships/rel-2/accept');
    expect(call.request.method).toBe('POST');
    call.flush({ ...LINKS[1], status: 'ACTIVE' });

    http.expectOne('/api/relationships/incoming').flush([]);
    http.verify();
  });
});
