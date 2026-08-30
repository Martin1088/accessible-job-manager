import { DOCUMENT, Injectable, inject } from '@angular/core';
import { Observable, combineLatest, map, tap } from 'rxjs';
import { PreferencesService } from '../services/preferences.service';
import { ContrastMode, PreferredFontFamily, UserPreferences } from '../model/user-preferences';

type EffectiveContrast = 'system' | 'high' | 'dark';
type EffectiveFontFamily = 'system' | 'sans-serif' | 'serif' | 'dyslexia-friendly';

interface EffectivePreferences {
  contrast: EffectiveContrast;
  reduceMotion: boolean;
  hideImages: boolean;
  fontFamily: EffectiveFontFamily;
  fontScale: number;
  lineHeight: number;
}

const DEFAULT_FONT_SCALE = 1;
const DEFAULT_LINE_HEIGHT = 1.5;

/** Emits the current value of a media query, starting with its value at subscription time. */
function mediaQuery$(query: string): Observable<boolean> {
  return new Observable<boolean>(subscriber => {
    const mql = window.matchMedia(query);
    subscriber.next(mql.matches);
    const listener = (event: MediaQueryListEvent) => subscriber.next(event.matches);
    mql.addEventListener('change', listener);
    return () => mql.removeEventListener('change', listener);
  });
}

/**
 * Applies accessibility preferences to <html> as data-* attributes and CSS
 * custom properties - no overlay layer, no !important injected into the DOM.
 * A stored preference (non-null) overrides the browser's prefers-contrast /
 * prefers-color-scheme / prefers-reduced-motion default.
 */
@Injectable({ providedIn: 'root' })
export class AccessibilityService {

  private readonly document = inject(DOCUMENT);
  private readonly preferences = inject(PreferencesService);

  private readonly systemHighContrast$ = mediaQuery$('(prefers-contrast: more)');
  private readonly systemDark$ = mediaQuery$('(prefers-color-scheme: dark)');
  private readonly systemReduceMotion$ = mediaQuery$('(prefers-reduced-motion: reduce)');

  private readonly effective$: Observable<EffectivePreferences> = combineLatest([
    this.preferences.preferences$,
    this.systemHighContrast$,
    this.systemDark$,
    this.systemReduceMotion$,
  ]).pipe(
    map(([prefs, systemHighContrast, systemDark, systemReduceMotion]) =>
      this.resolve(prefs, systemHighContrast, systemDark, systemReduceMotion))
  );

  /** Loads the stored preferences and starts keeping <html> in sync; called once at bootstrap. */
  init(): Observable<UserPreferences> {
    this.effective$.pipe(tap(effective => this.apply(effective))).subscribe();
    return this.preferences.load();
  }

  private resolve(
    prefs: UserPreferences,
    systemHighContrast: boolean,
    systemDark: boolean,
    systemReduceMotion: boolean
  ): EffectivePreferences {
    return {
      contrast: this.resolveContrast(prefs.contrastMode, systemHighContrast, systemDark),
      reduceMotion: prefs.reduceMotion ?? systemReduceMotion,
      hideImages: prefs.hideImages ?? false,
      fontFamily: this.resolveFontFamily(prefs.fontFamily),
      fontScale: prefs.fontScale ?? DEFAULT_FONT_SCALE,
      lineHeight: prefs.lineHeight ?? DEFAULT_LINE_HEIGHT,
    };
  }

  private resolveContrast(
    mode: ContrastMode | null,
    systemHighContrast: boolean,
    systemDark: boolean
  ): EffectiveContrast {
    if (mode === 'HIGH') return 'high';
    if (mode === 'DARK') return 'dark';
    if (systemHighContrast) return 'high';
    if (systemDark) return 'dark';
    return 'system';
  }

  private resolveFontFamily(family: PreferredFontFamily | null): EffectiveFontFamily {
    switch (family) {
      case 'SANS_SERIF': return 'sans-serif';
      case 'SERIF': return 'serif';
      case 'DYSLEXIA_FRIENDLY': return 'dyslexia-friendly';
      default: return 'system';
    }
  }

  private apply(effective: EffectivePreferences): void {
    const html = this.document.documentElement;
    html.setAttribute('data-contrast', effective.contrast);
    html.setAttribute('data-reduce-motion', String(effective.reduceMotion));
    html.setAttribute('data-hide-images', String(effective.hideImages));
    html.setAttribute('data-font-family', effective.fontFamily);
    html.style.setProperty('--font-scale', String(effective.fontScale));
    html.style.setProperty('--line-height', String(effective.lineHeight));
  }
}
