import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { routes } from './app.routes';
import { provideHttpClient, withXsrfConfiguration, withXhr } from '@angular/common/http';
import { provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { LanguageService } from './core/language.service';
import { AccessibilityService } from './core/accessibility.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withXhr(),
      withXsrfConfiguration({
        cookieName: 'XSRF-TOKEN',
        headerName: 'X-XSRF-TOKEN'
      })
    ),
    // Order matters: the http loader provider must come after
    // provideTranslateService so it overrides the no-op default loader.
    provideTranslateService({ fallbackLang: 'en' }),
    provideTranslateHttpLoader({ prefix: '/i18n/', suffix: '.json' }),
    // Blocks the first render until the initial language file has loaded, so
    // `| translate` pipes never briefly show their raw key (e.g. "LANGUAGE.EN")
    // before the translations arrive.
    provideAppInitializer(() => firstValueFrom(inject(LanguageService).init())),
    // Applies data-* attributes/CSS custom properties to <html> from the caller's
    // stored accessibility preferences before first paint.
    provideAppInitializer(() => firstValueFrom(inject(AccessibilityService).init()))
  ]
};
