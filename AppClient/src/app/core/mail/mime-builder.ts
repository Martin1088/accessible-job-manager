/**
 * A complete email draft as an `.eml` file (RFC 5322 + MIME), built in the browser so
 * the attachments never pass through a mail server of ours. Opened in the user's own
 * mail app it carries recipient, subject, body and every attachment - the one route
 * that works where neither the Web Share API nor `mailto:` can attach a file.
 *
 * Deliberately without `From:` and `Date:`: the client fills in the account it sends
 * from. `X-Unsent: 1` makes classic Outlook open the file as an editable draft; other
 * clients show it as a received message and need "Send again" / "Edit as new".
 */

export interface EmlAttachment {
  name: string;
  type: string;
  bytes: Uint8Array;
}

export interface EmlMessage {
  to: string;
  subject: string;
  text: string;
  html: string;
  attachments: EmlAttachment[];
}

const CRLF = '\r\n';
const BASE64_LINE = 76;
/**
 * Bytes per RFC 2047 encoded word: 39 bytes are 52 base64 chars, 64 with the
 * `=?UTF-8?B?…?=` frame - 73 after `Subject: `, inside the 78 a line should keep to.
 */
const ENCODED_WORD_BYTES = 39;

const encoder = new TextEncoder();

export function buildEml(message: EmlMessage, boundaryId: () => string = randomId): string {
  // Every part is base64, and "=_" can never occur in base64 output, so a boundary
  // containing it cannot collide with content - no scan of the content needed.
  const mixed = `=_ajm_mixed_${boundaryId()}`;
  const alternative = `=_ajm_alt_${boundaryId()}`;

  const lines: string[] = [];
  if (message.to.trim()) {
    lines.push(`To: ${message.to.trim()}`);
  }
  lines.push(
    `Subject: ${encodeHeaderWord(message.subject)}`,
    'X-Unsent: 1',
    'MIME-Version: 1.0',
    'Content-Type: multipart/mixed;',
    ` boundary="${mixed}"`,
    '',
    `--${mixed}`,
    'Content-Type: multipart/alternative;',
    ` boundary="${alternative}"`,
    '',
    `--${alternative}`,
    ...textPart('text/plain', message.text),
    `--${alternative}`,
    ...textPart('text/html', message.html),
    `--${alternative}--`,
  );

  for (const attachment of message.attachments) {
    const fallback = asciiFilename(attachment.name);
    lines.push(
      `--${mixed}`,
      `Content-Type: ${attachment.type};`,
      ` name="${fallback}"`,
      'Content-Disposition: attachment;',
      ` filename="${fallback}";`,
      ` filename*=UTF-8''${rfc2231(attachment.name)}`,
      'Content-Transfer-Encoding: base64',
      '',
      ...wrapBase64(base64(attachment.bytes)),
    );
  }
  lines.push(`--${mixed}--`, '');
  return lines.join(CRLF);
}

/**
 * The letter text as the HTML alternative: escaped, blank lines as paragraphs, URLs
 * as links. The link text is the URL itself - the text body carries no other
 * wording for a link, so there is nothing more descriptive to show.
 */
export function textToHtml(text: string, lang?: string): string {
  const paragraphs = text
    .replace(/\r\n/g, '\n')
    .split(/\n{2,}/)
    .filter(p => p.trim())
    .map(p => `<p>${linkify(escapeHtml(p)).replace(/\n/g, '<br>\n')}</p>`);
  const langAttribute = lang ? ` lang="${escapeHtml(lang)}"` : '';
  return `<!DOCTYPE html>\n<html${langAttribute}><head><meta charset="utf-8"></head><body>\n`
    + paragraphs.join('\n') + '\n</body></html>\n';
}

/** RFC 2047 `B` encoding, split into words of whole characters; plain ASCII stays as is. */
export function encodeHeaderWord(value: string): string {
  const flat = value.replace(/[\r\n]+/g, ' ');
  if (/^[\x20-\x7e]*$/.test(flat)) {
    return flat;
  }
  const words: string[] = [];
  let chunk = '';
  for (const char of flat) {
    if (encoder.encode(chunk + char).length > ENCODED_WORD_BYTES) {
      words.push(chunk);
      chunk = '';
    }
    chunk += char;
  }
  if (chunk) words.push(chunk);
  return words.map(w => `=?UTF-8?B?${base64(encoder.encode(w))}?=`).join(CRLF + ' ');
}

/** The `filename=` fallback for clients that ignore `filename*`: umlauts lose their marks. */
export function asciiFilename(name: string): string {
  const ascii = name
    .replace(/ß/g, 'ss')
    .normalize('NFKD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^A-Za-z0-9._-]+/g, '_');
  return ascii || 'attachment';
}

export function base64(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.length; i += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  }
  return btoa(binary);
}

function textPart(type: string, content: string): string[] {
  return [
    `Content-Type: ${type}; charset=UTF-8`,
    'Content-Transfer-Encoding: base64',
    '',
    ...wrapBase64(base64(encoder.encode(content.replace(/\r?\n/g, CRLF)))),
  ];
}

function wrapBase64(encoded: string): string[] {
  const lines: string[] = [];
  for (let i = 0; i < encoded.length; i += BASE64_LINE) {
    lines.push(encoded.substring(i, i + BASE64_LINE));
  }
  return lines;
}

/** RFC 2231 / RFC 5987 value: percent-encoded UTF-8, `'()*` included. */
function rfc2231(value: string): string {
  return encodeURIComponent(value).replace(/['()*!]/g, c => '%' + c.charCodeAt(0).toString(16).toUpperCase());
}

function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/** Runs on escaped text, so the URL it links is already safe to put in an attribute. */
function linkify(escaped: string): string {
  return escaped.replace(/\bhttps?:\/\/[^\s<]*[^\s<.,;:!?)\]]/g, url => `<a href="${url}">${url}</a>`);
}

function randomId(): string {
  return typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : Math.random().toString(36).slice(2) + Date.now().toString(36);
}
