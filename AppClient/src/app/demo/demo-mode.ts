import { InjectionToken, Signal } from '@angular/core';

/** The three people the demo can be looked at as. Replaces the three sign-ins. */
export type DemoRole = 'USER' | 'ADVISOR' | 'REVIEWER';

/**
 * True only in the `demo` build configuration, where `app.config.demo.ts` overrides
 * it. The default factory keeps every other build honest: nothing has to know the
 * demo exists, and the production bundle evaluates the demo branches to `false`.
 */
export const DEMO_MODE = new InjectionToken<boolean>('DEMO_MODE', {
  providedIn: 'root',
  factory: () => false,
});

/** What the demo bar and the login page are allowed to do. Implemented in the demo build only. */
export interface DemoControls {
  /** `null` until the visitor has entered through the login letter. */
  readonly role: Signal<DemoRole | null>;
  /**
   * The seeded person behind each role, by name only. The login letter greets
   * visitors with real names ("Enter as Sabine Vogt") without importing the
   * seed tree - the data crosses over here, inside the demo-only factory.
   */
  readonly people: Readonly<Record<DemoRole, { readonly name: string }>>;
  /** Switches the active person and lands on that role's home route. */
  switchTo(role: DemoRole): void;
  /** Back to the seed data. */
  reset(): void;
}

/**
 * Null in every build but `demo`.
 *
 * This token is what keeps the seed data out of the production bundle. The demo
 * bar is an ordinary component in the shell, so it is compiled into every build -
 * but it only ever sees this token, never `DemoDb`. The implementation, and with
 * it the whole `seed/` tree, is reachable only from `app.config.demo.ts`, which
 * the production build never compiles.
 */
export const DEMO_CONTROLS = new InjectionToken<DemoControls | null>('DEMO_CONTROLS', {
  providedIn: 'root',
  factory: () => null,
});
