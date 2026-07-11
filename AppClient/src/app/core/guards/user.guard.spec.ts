import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { userGuard } from './user.guard';
import { AuthService } from '../auth.service';

describe('userGuard', () => {
  let authSpy: jasmine.SpyObj<AuthService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(() => {
    authSpy = jasmine.createSpyObj('AuthService', [], { isUser$: of(true) });
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });
  });

  function runGuard() {
    return TestBed.runInInjectionContext(() => userGuard());
  }

  it('returns true for a regular user', (done) => {
    (Object.getOwnPropertyDescriptor(authSpy, 'isUser$')!.get as any) = () => of(true);
    Object.defineProperty(authSpy, 'isUser$', { get: () => of(true) });

    runGuard().subscribe(result => {
      expect(result).toBeTrue();
      expect(routerSpy.navigate).not.toHaveBeenCalled();
      done();
    });
  });

  it('redirects to /forbidden for advisor', (done) => {
    Object.defineProperty(authSpy, 'isUser$', { get: () => of(false) });

    runGuard().subscribe(result => {
      expect(result).toBeFalse();
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/forbidden']);
      done();
    });
  });
});
