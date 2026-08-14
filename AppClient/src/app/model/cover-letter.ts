export type BlockType = 'PARAGRAPH' | 'HEADING' | 'BULLET_LIST';

export type BlockKey = 'SUBJECT' | 'SALUTATION' | 'REGARDS' | BlockType;

export type LayoutLetterKey = 'DIN5008_COVER_LETTER_A' | 'DIN5008_COVER_LETTER_B' | 'DIN5008_CV';

export interface LetterBlock {
  id: string | null;
  key: BlockKey;
  content: string;
  items: string[];
}

export interface HtmlLetterTemplate {
  id: string;
  userId: string;
  layoutLetter: LayoutLetterKey;
  blocks: LetterBlock[];
  style?: unknown;
  version: number;
}

export interface HtmlLetterTemplateRequest {
  layoutLetter?: LayoutLetterKey;
  blocks: LetterBlock[];
  style?: unknown;
}

export interface CoverLetterRenderRequest {
  attachments: string[];
}
