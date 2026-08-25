import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { advisorGuard } from './advisor.guard';
import { AuthService } from '../auth.service';

describe('advisorGuard', () => {
  let authSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authSpy = jasmine.createSpyObj('AuthService', [], { isAdvisor$: of(false) });
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });
  });

  function runGuard() {
    return TestBed.runInInjectionContext(() => advisorGuard());
  }

  it('returns true for an advisor', (done) => {
    Object.defineProperty(authSpy, 'isAdvisor$', { get: () => of(true) });

    runGuard().subscribe(result => {
      expect(result).toBeTrue();
      expect(routerSpy.navigate).not.toHaveBeenCalled();
      done();
    });
  });

  it('redirects to /forbidden for a non-advisor', (done) => {
    Object.defineProperty(authSpy, 'isAdvisor$', { get: () => of(false) });

    runGuard().subscribe(result => {
      expect(result).toBeFalse();
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/forbidden']);
      done();
    });
  });
});
