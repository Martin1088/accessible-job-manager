import { asciiFilename, buildEml, encodeHeaderWord, textToHtml } from './mime-builder';

/** Decodes an RFC 2047 `B` header back to text, word by word. */
function decodeHeader(header: string): string {
  return header.split(/\r\n /).map(word => {
    const match = /^=\?UTF-8\?B\?(.*)\?=$/.exec(word);
    if (!match) return word;
    const bytes = Uint8Array.from(atob(match[1]), c => c.charCodeAt(0));
    return new TextDecoder().decode(bytes);
  }).join('');
}

function headerOf(eml: string, name: string): string | null {
  const head = eml.split('\r\n\r\n')[0];
  const match = new RegExp(`^${name}: ((?:.*)(?:\\r\\n .*)*)`, 'm').exec(head);
  return match ? match[1] : null;
}

/** The base64 body of the part whose headers contain `marker`, decoded to bytes. */
function partBytes(eml: string, marker: string): Uint8Array {
  const start = eml.indexOf(marker);
  const bodyStart = eml.indexOf('\r\n\r\n', start) + 4;
  const bodyEnd = eml.indexOf('\r\n--', bodyStart);
  const base64 = eml.substring(bodyStart, bodyEnd).replace(/\r\n/g, '');
  return Uint8Array.from(atob(base64), c => c.charCodeAt(0));
}

describe('buildEml', () => {
  const pdf = new Uint8Array([0x25, 0x50, 0x44, 0x46, 0x2d, ...Array.from({ length: 300 }, (_, i) => i % 256)]);
  // Real UUID boundaries, so the line-length check sees their actual length.
  const eml = buildEml({
    to: 'jobs@acme.example',
    subject: 'Bewerbung als Softwareentwicklerin – Größe zählt',
    text: 'Sehr geehrte Frau Müller,\n\nmehr unter https://example.org/jobs?id=1&x=2.',
    html: '<p>Hallo</p>',
    attachments: [{ name: 'Zeugnis Müller.pdf', type: 'application/pdf', bytes: pdf }],
  });

  it('uses CRLF line endings only', () => {
    expect(eml).toContain('\r\n');
    expect(/[^\r]\n/.test(eml)).toBeFalse();
    expect(/\r(?!\n)/.test(eml)).toBeFalse();
  });

  it('leaves From and Date to the mail client, and opens as a draft in Outlook', () => {
    expect(headerOf(eml, 'From')).toBeNull();
    expect(headerOf(eml, 'Date')).toBeNull();
    expect(headerOf(eml, 'X-Unsent')).toBe('1');
    expect(headerOf(eml, 'To')).toBe('jobs@acme.example');
  });

  it('encodes a subject with umlauts per RFC 2047 and decodes back to the same text', () => {
    const subject = headerOf(eml, 'Subject')!;
    expect(subject).toMatch(/^=\?UTF-8\?B\?/);
    expect(decodeHeader(subject)).toBe('Bewerbung als Softwareentwicklerin – Größe zählt');
  });

  it('names an attachment with umlauts twice: an ASCII fallback and RFC 2231', () => {
    expect(eml).toContain(' filename="Zeugnis_Muller.pdf";');
    expect(eml).toContain(" filename*=UTF-8''Zeugnis%20M%C3%BCller.pdf");
  });

  it('wraps every base64 line at 76 characters and keeps header lines short', () => {
    for (const line of eml.split('\r\n')) {
      expect(line.length).withContext(line).toBeLessThanOrEqual(78);
    }
  });

  it('carries the attachment byte for byte', () => {
    expect(partBytes(eml, ' name="Zeugnis_Muller.pdf"')).toEqual(pdf);
  });

  it('nests text and HTML as alternatives inside the mixed message', () => {
    const mixed = /multipart\/mixed;\r\n boundary="([^"]+)"/.exec(eml)![1];
    const alternative = /multipart\/alternative;\r\n boundary="([^"]+)"/.exec(eml)![1];
    expect(mixed).not.toBe(alternative);
    expect(eml.indexOf(`--${alternative}--`)).toBeLessThan(eml.indexOf('Content-Disposition: attachment'));
    expect(eml.trimEnd().endsWith(`--${mixed}--`)).toBeTrue();
    expect(new TextDecoder().decode(partBytes(eml, 'text/plain'))).toContain('Frau Müller,\r\n\r\nmehr');
  });

  it('omits the To header when there is no recipient', () => {
    const draft = buildEml({ to: ' ', subject: 'x', text: '', html: '', attachments: [] });
    expect(headerOf(draft, 'To')).toBeNull();
  });
});

describe('encodeHeaderWord', () => {
  it('leaves plain ASCII as it is', () => {
    expect(encodeHeaderWord('Application as developer')).toBe('Application as developer');
  });

  it('splits a long subject into words of whole characters', () => {
    const subject = 'Bewerbung als Ä'.repeat(10);
    const encoded = encodeHeaderWord(subject);
    expect(encoded.split('\r\n ').length).toBeGreaterThan(1);
    expect(decodeHeader(encoded)).toBe(subject);
  });
});

describe('asciiFilename', () => {
  it('drops diacritics and replaces what a header cannot carry', () => {
    expect(asciiFilename('Straße & Söhne "neu".pdf')).toBe('Strasse_Sohne_neu_.pdf');
  });
});

describe('textToHtml', () => {
  it('escapes markup, keeps paragraphs and links URLs', () => {
    const html = textToHtml('Hallo <b>Welt</b>\nZeile zwei\n\nSiehe https://example.org/a?b=1&c=2.', 'de');
    expect(html).toContain('<html lang="de">');
    expect(html).toContain('<p>Hallo &lt;b&gt;Welt&lt;/b&gt;<br>\nZeile zwei</p>');
    expect(html).toContain('<a href="https://example.org/a?b=1&amp;c=2">https://example.org/a?b=1&amp;c=2</a>.');
  });
});
