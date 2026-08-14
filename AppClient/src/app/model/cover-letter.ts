/**
 * Mirrors the backend `de.samply.manager.coverletter` and `de.samply.manager.types` records.
 *
 * Note what is absent: the recipient block, the return address, the salutation, the
 * date and the signature are all derived on the server from the application the
 * letter is rendered for. The frontend never sends them, and never computes them.
 *
 * The split that matters here is template vs. render. A `HtmlLetterTemplate` is written
 * once and holds only what is reusable - layout, style and blocks. The sender and the
 * attachments belong to one sending and travel with the render call instead, which is
 * why no rendered letter is ever stored: it can always be produced again.
 */

/** The body block kinds, a subset of the backend `BlockKey`. */
export type BlockType = 'PARAGRAPH' | 'HEADING' | 'BULLET_LIST';

/** Every slot a stored block can fill: the single-occurrence parts plus the body kinds. */
export type BlockKey = 'SUBJECT' | 'SALUTATION' | 'REGARDS' | BlockType;

export type LayoutLetterKey = 'DIN5008_COVER_LETTER_A' | 'DIN5008_COVER_LETTER_B' | 'DIN5008_CV';

export interface LetterBlock {
  id: string | null;
  key: BlockKey;
  /** May contain the inline markup subset and {{placeholder}} refs; both resolved server-side. */
  content: string;
  /** Only used by BULLET_LIST; empty for every other key. */
  items: string[];
}

/** A stored template as the server returns it. */
export interface HtmlLetterTemplate {
  id: string;
  userId: string;
  layoutLetter: LayoutLetterKey;
  blocks: LetterBlock[];
  /**
   * DIN 5008 geometry. The server returns it, but the form never edits it and never
   * sends it back: `StyleSettingsValidator` fills every unset component from
   * `StyleSettings.din5008FormB()`, so omitting it keeps the layout a server invariant.
   */
  style?: unknown;
  version: number;
}

/** The writable part of a template; owner and version stay server-side. */
export interface HtmlLetterTemplateRequest {
  layoutLetter?: LayoutLetterKey;
  blocks: LetterBlock[];
  style?: unknown;
}

/**
 * The per-sending half of a letter, supplied when a template is rendered. The sender
 * is not among them: the server reads it from the caller's profile, so a letter can
 * never be sent from an address the user has not maintained.
 */
export interface CoverLetterRenderRequest {
  attachments: string[];
}
