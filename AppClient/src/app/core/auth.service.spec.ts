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

  it('isUser$ emits true when user has no special groups', (done) => {
    service.isUser$.subscribe(v => {
      expect(v).toBeTrue();
      done();
    });
    respondWith({ sub: 'u1', name: 'Alice', email: 'a@b.com', groups: [] });
  });

  it('isUser$ emits false for ADVISOR', (done) => {
    service.isUser$.subscribe(v => {
      expect(v).toBeFalse();
      done();
    });
    respondWith({ sub: 'u2', name: 'Bob', email: 'b@b.com', groups: ['ADVISOR'] });
  });

  it('isUser$ emits false for REVIEWER', (done) => {
    service.isUser$.subscribe(v => {
      expect(v).toBeFalse();
      done();
    });
    respondWith({ sub: 'u3', name: 'Carol', email: 'c@b.com', groups: ['REVIEWER'] });
  });

  it('isAdvisor$ emits true when group is ADVISOR', (done) => {
    service.isAdvisor$.subscribe(v => {
      expect(v).toBeTrue();
      done();
    });
    respondWith({ sub: 'u2', name: 'Bob', email: 'b@b.com', groups: ['ADVISOR'] });
  });

  it('isAdvisor$ emits false for regular user', (done) => {
    service.isAdvisor$.subscribe(v => {
      expect(v).toBeFalse();
      done();
    });
    respondWith({ sub: 'u1', name: 'Alice', email: 'a@b.com', groups: [] });
  });

  it('isReviewer$ emits true when group is REVIEWER', (done) => {
    service.isReviewer$.subscribe(v => {
      expect(v).toBeTrue();
      done();
    });
    respondWith({ sub: 'u3', name: 'Carol', email: 'c@b.com', groups: ['REVIEWER'] });
  });

  it('me$ emits null on API error', (done) => {
    service.me$.subscribe(v => {
      expect(v).toBeNull();
      done();
    });
    http.expectOne('/api/me').error(new ProgressEvent('error'));
  });
});
