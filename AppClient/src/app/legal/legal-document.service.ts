import { Injectable, inject } from '@angular/core';
import { httpResource } from '@angular/common/http';

import { LanguageService } from '../core/language.service';
import { LegalDocument, LegalSlug } from './legal-document.model';

/**
 * Fetches a legal document for the language the interface is currently in.
 *
 * This is the first use of `httpResource` in this app - everything else predates it and
 * uses RxJS. It earns its place here because the refetch-on-language-switch behaviour is
 * the whole requirement, and reading the language signal inside the URL computation *is*
 * that behaviour: ngx-translate swaps `currentLang`, the request recomputes, the new
 * document replaces the old one. A route resolver would fetch once per navigation and
 * leave the page in the previous language until the visitor navigated away and back.
 */
@Injectable({ providedIn: 'root' })
export class LegalDocumentService {
  private readonly language = inject(LanguageService);

  /** Call from a component field initializer - `httpResource` needs an injection context. */
  resourceFor(slug: LegalSlug) {
    return httpResource<LegalDocument>(() => {
      const lang = this.language.current();
      // Root-relative and literal, so the demo's HttpBackend still intercepts it.
      return lang ? `/api/legal/${slug}?lang=${encodeURIComponent(lang)}` : undefined;
    });
  }
}
