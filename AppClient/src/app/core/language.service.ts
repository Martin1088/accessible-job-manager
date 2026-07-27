import { Injectable, inject, signal, DOCUMENT } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

export const SUPPORTED_LANGS = ['en', 'de', 'nl'] as const;
export type SupportedLang = typeof SUPPORTED_LANGS[number];

const STORAGE_KEY = 'lang';

/**
 * Owns runtime language switching. Besides swapping ngx-translate's active
 * language, it keeps <html lang> in sync (screen readers use it to pick the
 * right speech-synthesis voice/pronunciation) and exposes an announcement
 * string for a live region so switching is heard, not just seen.
 */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly translate = inject(TranslateService);
  private readonly document = inject(DOCUMENT);

  readonly current = this.translate.currentLang;
  readonly announcement = signal('');

  init(): void {
    const stored = localStorage.getItem(STORAGE_KEY);
    const browser = this.translate.getBrowserLang();
    const initial = this.isSupported(stored) ? stored
      : this.isSupported(browser) ? browser
      : 'en';
    this.apply(initial, false);
  }

  use(lang: string): void {
    if (this.isSupported(lang)) this.apply(lang, true);
  }

  private apply(lang: SupportedLang, announce: boolean): void {
    this.translate.use(lang).subscribe(() => {
      this.document.documentElement.lang = lang;
      localStorage.setItem(STORAGE_KEY, lang);
      if (announce) {
        const langName = this.translate.instant(`LANGUAGE.${lang.toUpperCase()}`);
        this.announcement.set(this.translate.instant('LANGUAGE.CHANGED', { language: langName }));
      }
    });
  }

  private isSupported(lang: string | null | undefined): lang is SupportedLang {
    return !!lang && (SUPPORTED_LANGS as readonly string[]).includes(lang);
  }
}
