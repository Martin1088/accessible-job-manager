/**
 * Mirrors the backend `de.samply.manager.coverletter` records.
 *
 * Note what is absent: the recipient block, the return address, the salutation, the
 * date and the signature are all derived on the server from the application the
 * letter is rendered for. The frontend never sends them, and never computes them.
 */

export type BlockType = 'PARAGRAPH' | 'HEADING' | 'BULLET_LIST';

export interface CoverLetterSender {
  name: string;
  street: string;
  postalCode: string;
  city: string;
  email: string;
  phone: string;
}

export interface CoverLetterBlock {
  type: BlockType;
  /** May contain the inline markup subset and {{placeholder}} refs; both resolved server-side. */
  text: string;
  /** Only used by BULLET_LIST; ignored for the other types. */
  items: string[];
}

export interface CoverLetterTemplate {
  sender: CoverLetterSender;
  subject: string | null;
  greeting: string | null;
  blocks: CoverLetterBlock[];
  closing: string | null;
  attachments: string[];
  /**
   * DIN 5008 geometry. `/template/default` returns it, but the form never edits it and
   * never sends it back: `StyleSettingsValidator` fills every unset component from
   * `StyleSettings.din5008FormB()`, so omitting it keeps the layout a server invariant.
   */
  style?: unknown;
}
