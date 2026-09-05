import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { SupportComponent } from './support.component';
import { expectNoAxeViolations } from '../../../testing/a11y';

describe('SupportComponent', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SupportComponent],
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

  const link = (over: Partial<Record<string, unknown>> = {}) => ({
    id: 'r1', applicantId: 'u1', applicantName: 'Alice',
    counterpartId: 'adv-1', counterpartName: 'Bob',
    kind: 'ADVISOR', status: 'ACTIVE', createdAt: '2026-08-20T09:00:00', ...over,
  });

  const suggestion = (over: Partial<Record<string, unknown>> = {}) => ({
    id: 1, advisorName: 'Bob', companyName: 'Acme GmbH', positionTitle: 'Developer',
    message: 'Good fit for you', status: 'PENDING', createdAt: '2026-08-20T09:00:00', ...over,
  });

  function create(data: {
    advisors?: unknown[]; reviewers?: unknown[]; relationships?: unknown[]; suggestions?: unknown[];
  } = {}) {
    const fixture = TestBed.createComponent(SupportComponent);
    fixture.detectChanges();
    http.expectOne('/api/directory/advisors').flush(data.advisors ?? []);
    http.expectOne('/api/directory/reviewers').flush(data.reviewers ?? []);
    http.expectOne('/api/relationships/mine').flush(data.relationships ?? []);
    http.expectOne('/api/my/suggestions').flush(data.suggestions ?? []);
    return fixture;
  }

  it('should create', () => {
    const fixture = create();
    expect(fixture.componentInstance).toBeTruthy();
  });

  describe('advisor and reviewer links', () => {
    it('lists advisors and reviewers, all requestable when no link exists', () => {
      const fixture = create({
        advisors: [{ userId: 'adv-1', name: 'Bob', email: 'bob@x.org' }],
        reviewers: [{ userId: 'rev-1', name: 'Cara', email: 'cara@x.org' }],
      });

      const rows = fixture.componentInstance.networkRows;
      expect(rows.map(r => r.name)).toEqual(['Bob', 'Cara']);
      expect(rows.map(r => r.kind)).toEqual(['ADVISOR', 'REVIEWER']);
      expect(rows.every(r => r.canRequest)).toBeTrue();
      expect(rows.some(r => r.canEnd)).toBeFalse();
    });

    it('POSTs a request and refreshes the row to "request sent"', () => {
      const fixture = create({ advisors: [{ userId: 'adv-1', name: 'Bob', email: 'bob@x.org' }] });

      fixture.componentInstance.requestLink(fixture.componentInstance.networkRows[0]);

      const req = http.expectOne({ url: '/api/relationships', method: 'POST' });
      expect(req.request.body).toEqual({ counterpartId: 'adv-1', kind: 'ADVISOR' });
      req.flush(link({ status: 'REQUESTED' }));

      http.expectOne('/api/relationships/mine').flush([link({ status: 'REQUESTED' })]);

      const row = fixture.componentInstance.networkRows[0];
      expect(row.canRequest).toBeFalse();
      expect(row.canEnd).toBeFalse();
      expect(fixture.componentInstance.networkBusy).toBeFalse();
    });

    it('offers End for an active link and calls the end endpoint', () => {
      const fixture = create({
        advisors: [{ userId: 'adv-1', name: 'Bob', email: 'bob@x.org' }],
        relationships: [link({ status: 'ACTIVE' })],
      });

      const row = fixture.componentInstance.networkRows[0];
      expect(row.canEnd).toBeTrue();
      expect(row.canRequest).toBeFalse();

      fixture.componentInstance.endLink(row);
      http.expectOne({ url: '/api/relationships/r1/end', method: 'POST' }).flush(link({ status: 'ENDED' }));
      http.expectOne('/api/relationships/mine').flush([link({ status: 'ENDED' })]);

      const refreshed = fixture.componentInstance.networkRows[0];
      expect(refreshed.canEnd).toBeFalse();
      expect(refreshed.canRequest).toBeTrue();
    });

    it('shows an alert when a request fails', () => {
      const fixture = create({ advisors: [{ userId: 'adv-1', name: 'Bob', email: 'bob@x.org' }] });

      fixture.componentInstance.requestLink(fixture.componentInstance.networkRows[0]);
      http.expectOne({ url: '/api/relationships', method: 'POST' })
        .flush({ message: 'nope' }, { status: 409, statusText: 'Conflict' });

      expect(fixture.componentInstance.networkActionError).toBeTrue();
      expect(fixture.componentInstance.networkBusy).toBeFalse();
    });
  });

  describe('suggested postings', () => {
    it('lists a suggestion with the sender and position it carries', () => {
      const fixture = create({ suggestions: [suggestion()] });

      const row = fixture.componentInstance.suggestionRows[0];
      expect(row.advisor).toBe('Bob');
      expect(row.company).toBe('Acme GmbH');
      expect(row.position).toBe('Developer');
      expect(row.message).toBe('Good fit for you');
      expect(row.canAnswer).toBeTrue();
    });

    it('only offers accept/decline while a suggestion is still pending', () => {
      const fixture = create({ suggestions: [suggestion({ status: 'ACCEPTED' })] });

      expect(fixture.componentInstance.suggestionRows[0].canAnswer).toBeFalse();
    });

    it('PATCHes the suggestion to ACCEPTED and reloads the list', () => {
      const fixture = create({ suggestions: [suggestion()] });

      fixture.componentInstance.answerSuggestion(fixture.componentInstance.suggestionRows[0], 'ACCEPTED');

      const req = http.expectOne({ url: '/api/my/suggestions/1', method: 'PATCH' });
      expect(req.request.body).toEqual({ status: 'ACCEPTED' });
      req.flush(suggestion({ status: 'ACCEPTED' }));

      http.expectOne('/api/my/suggestions').flush([suggestion({ status: 'ACCEPTED' })]);

      expect(fixture.componentInstance.suggestionsBusy).toBeFalse();
      expect(fixture.componentInstance.suggestionRows[0].canAnswer).toBeFalse();
    });

    it('shows an alert when answering a suggestion fails', () => {
      const fixture = create({ suggestions: [suggestion()] });

      fixture.componentInstance.answerSuggestion(fixture.componentInstance.suggestionRows[0], 'REJECTED');
      http.expectOne({ url: '/api/my/suggestions/1', method: 'PATCH' })
        .flush({ message: 'nope' }, { status: 500, statusText: 'Server Error' });

      expect(fixture.componentInstance.suggestionsActionError).toBeTrue();
      expect(fixture.componentInstance.suggestionsBusy).toBeFalse();
    });
  });

  it('has no axe-detectable accessibility violations', async () => {
    const fixture = create({
      advisors: [{ userId: 'adv-1', name: 'Bob', email: 'bob@x.org' }],
      suggestions: [suggestion()],
    });
    fixture.detectChanges();
    await expectNoAxeViolations(fixture);
  });
});
