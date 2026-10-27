import { MAILTO_LIMIT, fittingMailto, mailtoUrl } from './mailto';

describe('mailtoUrl', () => {
  it('encodes line breaks as CRLF and umlauts as UTF-8', () => {
    const url = mailtoUrl('jobs@acme.example', 'Bewerbung – Müller', 'Zeile 1\nZeile 2');
    expect(url).toBe('mailto:jobs@acme.example?subject=Bewerbung%20%E2%80%93%20M%C3%BCller&body=Zeile%201%0D%0AZeile%202');
  });

  it('keeps commas between several addresses readable', () => {
    expect(mailtoUrl('a@x.example, b@x.example', 's', '')).toMatch(/^mailto:a@x\.example,%20b@x\.example\?/);
  });

  it('opens a mail without recipient when the position has no address', () => {
    expect(mailtoUrl('', 's', 'b')).toBe('mailto:?subject=s&body=b');
  });
});

describe('fittingMailto', () => {
  it('uses the full letter while the link stays within the limit', () => {
    const result = fittingMailto('a@x.example', 'S', 'kurz', 'note');
    expect(result.short).toBeFalse();
    expect(result.url).toContain('body=kurz');
  });

  it('falls back to the short note when the letter would overflow the link', () => {
    const result = fittingMailto('a@x.example', 'S', 'ä'.repeat(MAILTO_LIMIT), 'note');
    expect(result.short).toBeTrue();
    expect(result.url).toContain('body=note');
    expect(result.url.length).toBeLessThanOrEqual(MAILTO_LIMIT);
  });
});
