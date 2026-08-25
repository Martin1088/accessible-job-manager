import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { reviewerGuard } from './reviewer.guard';
import { AuthService } from '../auth.service';

describe('reviewerGuard', () => {
  let authSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authSpy = jasmine.createSpyObj('AuthService', [], { isReviewer$: of(false) });
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });
  });

  function runGuard() {
    return TestBed.runInInjectionContext(() => reviewerGuard());
  }

  it('returns true for a reviewer', (done) => {
    Object.defineProperty(authSpy, 'isReviewer$', { get: () => of(true) });

    runGuard().subscribe(result => {
      expect(result).toBeTrue();
      expect(routerSpy.navigate).not.toHaveBeenCalled();
      done();
    });
  });

  it('redirects to /forbidden for a non-reviewer', (done) => {
    Object.defineProperty(authSpy, 'isReviewer$', { get: () => of(false) });

    runGuard().subscribe(result => {
      expect(result).toBeFalse();
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/forbidden']);
      done();
    });
  });
});
