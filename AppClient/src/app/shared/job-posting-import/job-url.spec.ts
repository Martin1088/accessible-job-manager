import { normalizeJobUrl } from './job-url';

describe('normalizeJobUrl', () => {

  it('returns a plain URL unchanged', () => {
    expect(normalizeJobUrl('https://karriere.example.de/stellen/1234'))
      .toBe('https://karriere.example.de/stellen/1234');
  });

  it('recovers the link from a phone share sheet', () => {
    // The exact shape the Indeed iOS share button produces: a title line, then
    // the same URL twice. This whole string reaches `new URI(...)` today and
    // fails on the space in "(Junior) Plattform".
    const shared = '(Junior) Plattform Architekt (m/w/d)\n'
      + 'https://de.indeed.com/viewjob?jk=b89f7b8d6ed64c7a&from=appshareios '
      + 'https://de.indeed.com/viewjob?jk=b89f7b8d6ed64c7a&from=appshareios';

    expect(normalizeJobUrl(shared)).toBe('https://de.indeed.com/viewjob?jk=b89f7b8d6ed64c7a');
  });

  it('finds the link when a single-line input has stripped the newline', () => {
    // <input> collapses a pasted newline, so the title runs straight into the
    // URL with no separator at all.
    expect(normalizeJobUrl('(Junior) Plattform Architekt (m/w/d)https://de.indeed.com/viewjob?jk=abc'))
      .toBe('https://de.indeed.com/viewjob?jk=abc');
  });

  it('keeps the posting id while dropping Indeed share tracking', () => {
    expect(normalizeJobUrl('https://de.indeed.com/viewjob?jk=abc123&from=appshareios&tk=xyz&advn=99'))
      .toBe('https://de.indeed.com/viewjob?jk=abc123');
  });

  it('keeps the posting id while dropping LinkedIn tracking', () => {
    expect(normalizeJobUrl('https://www.linkedin.com/jobs/view/4012345678/?trk=public_jobs&refId=abc&position=3'))
      .toBe('https://www.linkedin.com/jobs/view/4012345678/');
  });

  it('does not strip a host-scoped tracking name on an unrelated host', () => {
    // `from` is share provenance on Indeed and a real parameter elsewhere.
    expect(normalizeJobUrl('https://jobs.example.com/apply?id=7&from=archive'))
      .toBe('https://jobs.example.com/apply?id=7&from=archive');
  });

  it('strips universally-tracking parameters on any host', () => {
    expect(normalizeJobUrl('https://jobs.example.com/x?id=7&utm_source=news&utm_campaign=q1&gclid=z'))
      .toBe('https://jobs.example.com/x?id=7');
  });

  it('drops the query string entirely when nothing but tracking was in it', () => {
    expect(normalizeJobUrl('https://jobs.example.com/x?utm_source=news'))
      .toBe('https://jobs.example.com/x');
  });

  it('strips sentence punctuation that clings to a link in prose', () => {
    expect(normalizeJobUrl('Schau dir das an: https://jobs.example.com/stelle/9.'))
      .toBe('https://jobs.example.com/stelle/9');
  });

  it('drops a bracket that wraps the link but keeps one that belongs to it', () => {
    expect(normalizeJobUrl('(siehe https://jobs.example.com/a)'))
      .toBe('https://jobs.example.com/a');
    expect(normalizeJobUrl('https://en.wikipedia.org/wiki/Architect_(disambiguation)'))
      .toBe('https://en.wikipedia.org/wiki/Architect_(disambiguation)');
  });

  it('takes the first link when several are pasted', () => {
    expect(normalizeJobUrl('https://first.example.com/a and https://second.example.com/b'))
      .toBe('https://first.example.com/a');
  });

  it('returns null for text with no link in it', () => {
    // This is the signal the caller uses to offer the paste-the-text path.
    expect(normalizeJobUrl('Wir suchen eine Plattform-Architektin (m/w/d) in Vollzeit.')).toBeNull();
  });

  it('returns null for empty or absent input', () => {
    expect(normalizeJobUrl('')).toBeNull();
    expect(normalizeJobUrl(null)).toBeNull();
    expect(normalizeJobUrl(undefined)).toBeNull();
  });

  it('ignores a non-http scheme', () => {
    expect(normalizeJobUrl('ftp://files.example.com/posting.pdf')).toBeNull();
    expect(normalizeJobUrl('mailto:jobs@example.com')).toBeNull();
  });
});
