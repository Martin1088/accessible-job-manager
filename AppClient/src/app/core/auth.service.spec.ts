import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { AuthService, UserMe } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AuthService]
    });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function respondWith(me: UserMe) {
    http.expectOne('/api/me').flush(me);
  }

  it('isUser$ emits true when the USER role is held', (done) => {
    service.isUser$.subscribe(v => {
      expect(v).toBeTrue();
      done();
    });
    respondWith({ sub: 'u1', name: 'Alice', email: 'a@b.com', roles: ['USER'] });
  });

  it('isUser$ emits false for ADVISOR', (done) => {
    service.isUser$.subscribe(v => {
      expect(v).toBeFalse();
      done();
    });
    respondWith({ sub: 'u2', name: 'Bob', email: 'b@b.com', roles: ['ADVISOR'] });
  });

  it('isUser$ emits false for REVIEWER', (done) => {
    service.isUser$.subscribe(v => {
      expect(v).toBeFalse();
      done();
    });
    respondWith({ sub: 'u3', name: 'Carol', email: 'c@b.com', roles: ['REVIEWER'] });
  });

  it('isUser$ emits false when no role is held', (done) => {
    service.isUser$.subscribe(v => {
      expect(v).toBeFalse();
      done();
    });
    respondWith({ sub: 'u4', name: 'Dan', email: 'd@b.com', roles: [] });
  });

  it('isAdvisor$ emits true when the ADVISOR role is held', (done) => {
    service.isAdvisor$.subscribe(v => {
      expect(v).toBeTrue();
      done();
    });
    respondWith({ sub: 'u2', name: 'Bob', email: 'b@b.com', roles: ['ADVISOR'] });
  });

  it('isAdvisor$ emits false for regular user', (done) => {
    service.isAdvisor$.subscribe(v => {
      expect(v).toBeFalse();
      done();
    });
    respondWith({ sub: 'u1', name: 'Alice', email: 'a@b.com', roles: ['USER'] });
  });

  it('isReviewer$ emits true when the REVIEWER role is held', (done) => {
    service.isReviewer$.subscribe(v => {
      expect(v).toBeTrue();
      done();
    });
    respondWith({ sub: 'u3', name: 'Carol', email: 'c@b.com', roles: ['REVIEWER'] });
  });

  it('logout posts to /api/logout so the CSRF token is sent', () => {
    spyOn(service, 'redirectTo');
    service.logout();

    const req = http.expectOne('/api/logout');
    expect(req.request.method).toBe('POST');
    req.flush({ redirectUrl: '/' });
  });

  it('logout follows the redirect the backend returns', () => {
    const redirect = spyOn(service, 'redirectTo');
    service.logout();

    http.expectOne('/api/logout').flush({ redirectUrl: 'http://idp/end-session/' });

    expect(redirect).toHaveBeenCalledWith('http://idp/end-session/');
  });

  it('logout returns to the app when the call fails', () => {
    const redirect = spyOn(service, 'redirectTo');
    service.logout();

    http.expectOne('/api/logout').error(new ProgressEvent('error'));

    expect(redirect).toHaveBeenCalledWith('/');
  });

  it('me$ emits null on API error', (done) => {
    service.me$.subscribe(v => {
      expect(v).toBeNull();
      done();
    });
    http.expectOne('/api/me').error(new ProgressEvent('error'));
  });
});
