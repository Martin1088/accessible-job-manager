import { DocumentLanguage } from './document';

export type BlockType = 'PARAGRAPH' | 'HEADING' | 'BULLET_LIST';

export type BlockKey = 'SUBJECT' | 'SALUTATION' | 'REGARDS' | BlockType;

export type LayoutLetterKey = 'DIN5008_COVER_LETTER_A' | 'DIN5008_COVER_LETTER_B' | 'DIN5008_CV';

export interface LetterBlock {
  id: string | null;
  key: BlockKey;
  content: string;
  items: string[];
}

/** Mirrors `HtmlLetterTemplateDto`; `userId` is not returned, every template is the caller's. */
export interface HtmlLetterTemplate {
  id: string;
  /** What the user calls this template; the label the documents list shows. */
  name: string;
  language: DocumentLanguage;
  layoutLetter: LayoutLetterKey;
  blocks: LetterBlock[];
  style?: unknown;
  version: number;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface HtmlLetterTemplateRequest {
  name?: string;
  layoutLetter?: LayoutLetterKey;
  blocks: LetterBlock[];
  style?: unknown;
}

export interface CoverLetterRenderRequest {
  attachments: string[];
}

/** Mirrors `CoverLetterEmailDto`: a letter prepared for the user's own mail client. */
export interface CoverLetterEmail {
  to: string;
  subject: string;
  body: string;
}
