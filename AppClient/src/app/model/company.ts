export type Gender = 'MALE' | 'FEMALE' | 'DIVERSE';

/** Which way an applicant has to go to apply for a position. */
export type ApplicationMethod = 'EMAIL' | 'WEB_FORM' | 'UNKNOWN';

/**
 * Whether a position has been through the review queue. A position that turned
 * up - from an import, from the paste flow - starts as NEW and is only part of
 * the catalogue once it has been accepted.
 */
export type TriageState = 'NEW' | 'ACCEPTED' | 'DISMISSED';

/** Language the application for a position should be written in. */
export type Language = 'GERMAN' | 'ENGLISH' | 'DUTCH';

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
  /**
   * Sent on create to file a position straight into a catalogue (the advisor's
   * pages do that); left off everywhere else, where the server's NEW applies.
   * Changing it later goes through the queue's accept/dismiss endpoints.
   */
  triageState?: TriageState;
  createdAt?: string;
}

export interface Company {
  id?: number;
  name: string;
  locations: CompanyLocation[];
  positions: CompanyPosition[];
}
