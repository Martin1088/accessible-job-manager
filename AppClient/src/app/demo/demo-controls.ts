import { EnvironmentInjector, runInInjectionContext } from '@angular/core';
import { Router } from '@angular/router';
import { filter, take } from 'rxjs/operators';

import { AuthService } from '../core/auth.service';
import { DemoControls, DemoRole } from './demo-mode';
import { DemoDb } from './demo-db';

/** Where each role lands. The role guards would redirect anyway; this skips the bounce. */
const HOME_ROUTE: Readonly<Record<DemoRole, string>> = {
  USER: '/',
  ADVISOR: '/advisor',
  REVIEWER: '/reviewer',
};

/**
 * Wires the demo bar to the in-memory database. Provided only by
 * `app.config.demo.ts`, which is the reason `DemoDb` and everything it imports
 * stays out of every other build.
 */
export function createDemoControls(injector: EnvironmentInjector): DemoControls {
  return runInInjectionContext(injector, () => {
    const db = injector.get(DemoDb);
    const auth = injector.get(AuthService);
    const router = injector.get(Router);

    function goHome(role: DemoRole): void {
      // `me$` replays its previous value to a new subscriber, so a guard
      // evaluated right now would still see the old role. Wait for the identity
      // that matches before navigating.
      auth.me$.pipe(
        filter(me => !!me?.roles.includes(role)),
        take(1),
      ).subscribe(() => void router.navigate([HOME_ROUTE[role]]));
    }

    return {
      role: db.role.asReadonly(),

      switchTo(role: DemoRole): void {
        db.role.set(role);
        auth.refresh();
        goHome(role);
      },

      reset(): void {
        db.reset();
        auth.refresh();
        goHome('USER');
      },
    };
  });
}
