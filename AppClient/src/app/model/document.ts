export type DocumentType = 'CV' | 'COVER_LETTER_TEMPLATE' | 'CERTIFICATE' | 'OTHER';
export type DocumentLanguage = 'GERMAN' | 'ENGLISH' | 'DUTCH';

const UI_TO_DOCUMENT_LANGUAGE: Record<string, DocumentLanguage> = {
  de: 'GERMAN',
  en: 'ENGLISH',
  nl: 'DUTCH',
};

/**
 * The document language matching a UI language code, for defaulting a language
 * field to the one the user is already reading in. Only a default - writing a
 * letter in a language other than the interface's is the point of those fields.
 */
export function uiToLetterLanguage(uiLang: string | null | undefined): DocumentLanguage {
  return UI_TO_DOCUMENT_LANGUAGE[uiLang ?? ''] ?? 'ENGLISH';
}

export interface Document {
  id: string;
  label: string;
  filename: string;
  mimeType: string;
  type: DocumentType;
  language: DocumentLanguage;
  createdAt?: string;
  updatedAt?: string;
}
