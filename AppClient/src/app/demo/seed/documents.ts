/**
 * Seed data - the applicant's documents, and what the reviewer sees of them.
 *
 * The reviewer's view is deliberately narrower than the applicant's: `SHARES`
 * lists four grants, one of them revoked, and only the two active ones on
 * `COVER_LETTER_TEMPLATE` documents reach `/api/reviewer/users`. That gap is the
 * point of the reviewer role, so it has to survive into the demo intact.
 *
 * Captured against the API of `feature/advisor` @ 72c91ad, 2026-08-30.
 */
import { Document, DocumentLanguage, DocumentType } from '../../model/document';

export const DOCUMENTS: Document[] = [
  { id: 'doc-cv',        label: 'Lebenslauf 2026',              filename: 'lebenslauf-2026.pdf',        mimeType: 'application/pdf', type: 'CV',                    language: 'GERMAN',  createdAt: '2026-01-08T10:00:00' },
  { id: 'doc-cv-en',     label: 'CV (English)',                 filename: 'cv-2026-en.pdf',             mimeType: 'application/pdf', type: 'CV',                    language: 'ENGLISH', createdAt: '2026-06-18T14:25:00' },
  { id: 'doc-cert-1',    label: 'Zeugnis Fachinformatikerin',   filename: 'zeugnis-fachinformatik.pdf', mimeType: 'application/pdf', type: 'CERTIFICATE',           language: 'GERMAN',  createdAt: '2026-01-08T10:05:00' },
  { id: 'doc-cert-2',    label: 'Arbeitszeugnis Nordlicht',     filename: 'arbeitszeugnis.pdf',         mimeType: 'application/pdf', type: 'CERTIFICATE',           language: 'GERMAN',  createdAt: '2026-04-02T09:10:00' },
  { id: 'doc-letter-de', label: 'Anschreiben Standard (DE)',    filename: 'anschreiben-de.docx',        mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', type: 'COVER_LETTER_TEMPLATE', language: 'GERMAN',  createdAt: '2026-02-01T11:00:00' },
  { id: 'doc-letter-en', label: 'Cover letter (EN)',            filename: 'cover-letter-en.docx',       mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', type: 'COVER_LETTER_TEMPLATE', language: 'ENGLISH', createdAt: '2026-06-19T09:30:00' },
];

export interface DemoShare {
  readonly documentId: string;
  readonly reviewerId: string;
  readonly grantedAt: string;
  /** Set means the applicant took the access back; the reviewer must stop seeing it. */
  readonly revokedAt: string | null;
}

export const SHARES: DemoShare[] = [
  { documentId: 'doc-letter-de', reviewerId: 'demo-reviewer', grantedAt: '2026-07-11T14:45:00', revokedAt: null },
  { documentId: 'doc-letter-en', reviewerId: 'demo-reviewer', grantedAt: '2026-07-11T14:46:00', revokedAt: null },
  { documentId: 'doc-cv',        reviewerId: 'demo-reviewer', grantedAt: '2026-07-11T14:47:00', revokedAt: '2026-08-03T08:00:00' },
];

/** One filled default slot, as the demo stores it; `/api/documents/defaults` joins in the document. */
export interface DemoDefault {
  readonly type: DocumentType;
  readonly language: DocumentLanguage;
  readonly documentId: string;
}

/**
 * The German and the English CV and the German letter template are the
 * applicant's defaults. The CV check compares the default CV in the interface
 * language, so it runs in German and English and shows its "no default CV"
 * notice in Dutch and Spanish.
 */
export const DEFAULTS: DemoDefault[] = [
  { type: 'CV',                    language: 'GERMAN', documentId: 'doc-cv' },
  { type: 'CV',                    language: 'ENGLISH', documentId: 'doc-cv-en' },
  { type: 'COVER_LETTER_TEMPLATE', language: 'GERMAN', documentId: 'doc-letter-de' },
];
