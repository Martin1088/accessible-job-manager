import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';

import { HomeComponent } from './home.component';
import { expectNoAxeViolations } from '../../../testing/a11y';

describe('HomeComponent (Advisor)', () => {
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

  it('should create', () => {
    const fixture = TestBed.createComponent(HomeComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

<<<<<<< HEAD
  describe('assignment requests', () => {
    let http: HttpTestingController;

    beforeEach(() => http = TestBed.inject(HttpTestingController));
    afterEach(() => http.verify());

    const rel = (over: Record<string, unknown> = {}) => ({
      id: 'rel-1', applicantId: 'u1', applicantName: 'Dana',
      counterpartId: 'me', counterpartName: 'Me',
      kind: 'ADVISOR', status: 'REQUESTED', createdAt: '2026-08-25T08:00:00', ...over,
    });

    function create(incoming: unknown[] = [rel()]) {
      const fixture = TestBed.createComponent(HomeComponent);
      fixture.detectChanges();
      http.expectOne('/api/advisor/my-users').flush([]);
      http.expectOne('/api/relationships/incoming').flush(incoming);
      http.expectOne('/api/companies').flush([]);
      http.expectOne('/api/advisor/suggestions').flush([]);
      http.match('/api/me').forEach(r =>
        r.flush({ sub: 'me', name: 'Me', email: 'm@x.org', roles: ['ADVISOR'] }));
      return fixture;
    }

    it('lists only pending advisor-kind requests', () => {
      const fixture = create([
        rel(),
        rel({ id: 'rel-2', status: 'ACTIVE' }),
        rel({ id: 'rel-3', kind: 'REVIEWER' }),
      ]);

      expect(fixture.componentInstance.requestRows.map(r => r.id)).toEqual(['rel-1']);
      expect(fixture.componentInstance.requestRows[0].user).toBe('Dana');
      expect(fixture.componentInstance.requestRows[0].requested).toBe('2026-08-25');
    });

    it('accepts a request, then reloads requests and My Users', () => {
      const fixture = create();
      const accept = fixture.componentInstance.requestActions[0];
      accept.handler(fixture.componentInstance.requestRows[0]);

      http.expectOne({ url: '/api/relationships/rel-1/accept', method: 'POST' }).flush(rel({ status: 'ACTIVE' }));
      http.expectOne('/api/relationships/incoming').flush([]);
      http.expectOne('/api/advisor/my-users').flush([{ userId: 'u1', name: 'Dana', email: 'd@x.org' }]);

      expect(fixture.componentInstance.requestRows).toEqual([]);
      expect(fixture.componentInstance.userRows).toEqual([{ name: 'Dana', email: 'd@x.org' }]);
    });

    it('declines a request and leaves My Users untouched', () => {
      const fixture = create();
      const decline = fixture.componentInstance.requestActions[1];
      decline.handler(fixture.componentInstance.requestRows[0]);

      http.expectOne({ url: '/api/relationships/rel-1/decline', method: 'POST' }).flush(rel({ status: 'DECLINED' }));
      http.expectOne('/api/relationships/incoming').flush([]);
      http.expectNone('/api/advisor/my-users');

      expect(fixture.componentInstance.requestRows).toEqual([]);
    });

    it('shows an error when the requests list fails to load', () => {
      const fixture = TestBed.createComponent(HomeComponent);
      fixture.detectChanges();
      http.expectOne('/api/advisor/my-users').flush([]);
      http.expectOne('/api/relationships/incoming').flush('nope', { status: 500, statusText: 'Server Error' });
      http.expectOne('/api/companies').flush([]);
      http.expectOne('/api/advisor/suggestions').flush([]);
      http.match('/api/me').forEach(r =>
        r.flush({ sub: 'me', name: 'Me', email: 'm@x.org', roles: ['ADVISOR'] }));

      expect(fixture.componentInstance.requestError).toBeTruthy();
    });
||||||| 9a74271
=======
  it('has no axe-detectable accessibility violations', async () => {
    const fixture = TestBed.createComponent(HomeComponent);
    fixture.detectChanges();
    await expectNoAxeViolations(fixture);
>>>>>>> develop
  });
});
