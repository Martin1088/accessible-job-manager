export type DocumentType = 'CV' | 'COVER_LETTER_TEMPLATE' | 'CERTIFICATE' | 'OTHER';
export type DocumentLanguage = 'GERMAN' | 'ENGLISH' | 'DUTCH';

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
