import { ApplicationConfig, EnvironmentInjector, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { provideRouter, withHashLocation } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { routes } from './app.routes';
import { HttpBackend, provideHttpClient, withXhr, withXsrfConfiguration } from '@angular/common/http';
import { provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { LanguageService } from './core/language.service';
import { AccessibilityService } from './core/accessibility.service';
import { DemoBackend } from './demo/demo-backend';
import { createDemoControls } from './demo/demo-controls';
import { DEMO_CONTROLS, DEMO_MODE } from './demo/demo-mode';

/**
 * The `demo` build configuration swaps this in for `app.config.ts`
 * (`fileReplacements` in angular.json).
 *
 * It is deliberately the regular configuration plus three lines. `HttpBackend`
 * is the one token every `HttpClient` call funnels into, so replacing it covers
 * the services, the components that call `HttpClient` directly, the route guards
 * and the two bootstrap initializers at once - and nothing else in the app has
 * to know that it is running as a demo.
 *
 * `provideHttpClient` and `provideTranslateHttpLoader` stay: the translation
 * files are real static assets on GitHub Pages, and DemoBackend passes anything
 * that is not `/api/*` through to them. That is what keeps the language switch
 * working without a second loader implementation.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),

    // Hash routing so a deep link works on GitHub Pages, which has no rewrite
    // rule to send unknown paths back to index.html.
    provideRouter(routes, withHashLocation()),

    provideHttpClient(withXhr(),
      withXsrfConfiguration({
        cookieName: 'XSRF-TOKEN',
        headerName: 'X-XSRF-TOKEN'
      })
    ),
    { provide: HttpBackend, useClass: DemoBackend },
    { provide: DEMO_MODE, useValue: true },
    {
      provide: DEMO_CONTROLS,
      useFactory: (injector: EnvironmentInjector) => createDemoControls(injector),
      deps: [EnvironmentInjector],
    },

    provideTranslateService({ fallbackLang: 'en' }),
    provideTranslateHttpLoader({ prefix: 'i18n/', suffix: '.json' }),

    provideAppInitializer(() => firstValueFrom(inject(LanguageService).init())),
    provideAppInitializer(() => firstValueFrom(inject(AccessibilityService).init())),

    // Screen readers read the document title on load, which is more reliable
    // than announcing into a live region while the page is still bootstrapping.
    // It is the first thing a visitor hears, and it says what this is.
    provideAppInitializer(() => { inject(Title).setTitle('Demo — Job Application Manager'); }),
  ]
};
