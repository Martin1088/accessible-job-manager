export type ContrastMode = 'SYSTEM' | 'HIGH' | 'DARK';
export type PreferredFontFamily = 'SYSTEM' | 'SANS_SERIF' | 'SERIF' | 'DYSLEXIA_FRIENDLY';

/**
 * Every field is null until the user explicitly overrides it. Null means
 * "follow the browser's prefers-* media query", not any hardcoded value.
 */
export interface UserPreferences {
  fontScale: number | null;
  contrastMode: ContrastMode | null;
  reduceMotion: boolean | null;
  hideImages: boolean | null;
  lineHeight: number | null;
  fontFamily: PreferredFontFamily | null;
}

export const DEFAULT_PREFERENCES: UserPreferences = {
  fontScale: null,
  contrastMode: null,
  reduceMotion: null,
  hideImages: null,
  lineHeight: null,
  fontFamily: null,
};
