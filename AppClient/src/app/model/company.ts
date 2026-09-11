export type Gender = 'MALE' | 'FEMALE' | 'DIVERSE';

/** Which way an applicant has to go to apply for a position. */
export type ApplicationMethod = 'EMAIL' | 'WEB_FORM' | 'UNKNOWN';

/** Language the application for a position should be written in. */
export type Language = 'GERMAN' | 'ENGLISH' | 'DUTCH';

const UI_TO_LANGUAGE: Record<string, Language> = {
  de: 'GERMAN',
  en: 'ENGLISH',
  nl: 'DUTCH',
};

/**
 * The apply language matching a UI language code, for defaulting a new
 * position's language field to the one the user is already reading in. Only a
 * default - applying in a language other than the interface's is the point of
 * this field.
 */
export function uiToApplyLanguage(uiLang: string | null | undefined): Language {
  return UI_TO_LANGUAGE[uiLang ?? ''] ?? 'ENGLISH';
}

export interface CompanyLocation {
  id?: number;
  street: string;
  city: string;
  postcode?: string;
  country?: string;
}

export interface CompanyPosition {
  id?: number;
  title: string;
  contactGender?: Gender;
  contactTitle?: string;
  contactLastName?: string;
  applyLanguage?: Language;
  email?: string;
  /** Also holds the application link when applicationMethod is WEB_FORM. */
  website?: string;
  notes?: string;
  applicationMethod?: ApplicationMethod;
  createdAt?: string;
}

export interface Company {
  id?: number;
  name: string;
  locations: CompanyLocation[];
  positions: CompanyPosition[];
}
