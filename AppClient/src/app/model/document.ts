export type DocumentType = 'CV' | 'COVER_LETTER_TEMPLATE' | 'CERTIFICATE' | 'OTHER';

export interface Document {
  id: string;
  label: string;
  filename: string;
  mimeType: string;
  type: DocumentType;
  createdAt?: string;
  updatedAt?: string;
}
