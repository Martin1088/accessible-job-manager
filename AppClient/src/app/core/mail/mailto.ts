/**
 * `mailto:` (RFC 6068) carries recipient, subject and body - no attachments, no HTML -
 * and some browser and client pairs cut off or drop a URL past roughly 2,000
 * characters. 1,500 leaves room for that without measuring every pair.
 */
export const MAILTO_LIMIT = 1500;

export function mailtoUrl(to: string, subject: string, body: string): string {
  // Commas between addresses and the "@" read better unencoded, and RFC 6068 allows both.
  const address = encodeURIComponent(to.trim()).replace(/%40/g, '@').replace(/%2C/g, ',');
  const query = [
    `subject=${encodeURIComponent(subject)}`,
    `body=${encodeURIComponent(body.replace(/\r?\n/g, '\r\n'))}`,
  ].join('&');
  return `mailto:${address}?${query}`;
}

/**
 * The full body when it fits, otherwise the short note - which then says the letter
 * is attached, so nothing is lost but the inline copy.
 */
export function fittingMailto(to: string, subject: string, body: string, shortBody: string):
    { url: string; short: boolean } {
  const full = mailtoUrl(to, subject, body);
  return full.length <= MAILTO_LIMIT
    ? { url: full, short: false }
    : { url: mailtoUrl(to, subject, shortBody), short: true };
}
