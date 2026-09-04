/**
 * Recovering a job posting URL from whatever the user actually pasted.
 *
 * People rarely arrive with a bare URL. A phone share sheet produces a title
 * line followed by the same link twice; a link copied out of an email arrives
 * wrapped in prose and trailing punctuation. All of those reach the backend as
 * one string, fail `new URI(...)` on the first space, and come back as
 * "Malformed URL" - which blames the user for a link that was fine.
 *
 * This runs in the browser rather than on the server on purpose: the cleaned
 * URL is written back into the field, so the user sees what will be fetched and
 * can correct it. A server-side rewrite would be invisible.
 */

/**
 * Stops at whitespace and at the quoting/bracketing characters that wrap a URL
 * in prose or markup. A second copy of the same link - what a share sheet
 * appends - is separated by whitespace, so taking the first match drops it.
 */
const URL_IN_TEXT = /https?:\/\/[^\s<>"'`\][{}|\\^]+/i;

/** Sentence punctuation that clings to a pasted URL but is not part of it. */
const TRAILING_PUNCTUATION = /[.,;:!?]+$/;

/**
 * Tracking parameters no site needs to serve the page. Only ones that are
 * *never* functional: a parameter that might carry meaning somewhere is left
 * alone, because a URL this function breaks is worse than a URL it fails to
 * tidy.
 */
const UNIVERSAL_TRACKING = /^(?:utm_[a-z_]*|gclid|fbclid|msclkid|mc_cid|mc_eid|igshid)$/i;

/**
 * Per-host tracking. Scoped by host rather than added to the list above
 * because these names are too generic to strip everywhere: `from` is share
 * provenance on Indeed and a meaningful filter on plenty of other sites.
 *
 * Indeed's `jk` and LinkedIn's `currentJobId` identify the posting itself and
 * must survive - dropping them would leave a URL pointing at a search page.
 */
const HOST_TRACKING: readonly { readonly host: RegExp; readonly params: ReadonlySet<string> }[] = [
  { host: /(?:^|\.)indeed\.[a-z.]+$/i, params: new Set(['from', 'advn', 'tk', 'vjs', 'xpse', 'xkcb']) },
  { host: /(?:^|\.)linkedin\.com$/i,   params: new Set(['trk', 'trkInfo', 'refId', 'originalSubdomain', 'position', 'pageNum']) },
];

/**
 * Drops a trailing `)` only when it closes nothing inside the URL, so
 * "(see https://example.com/a)" loses it while a Wikipedia-style
 * ".../Foo_(bar)" keeps it.
 */
function dropUnbalancedTail(candidate: string): string {
  let result = candidate;
  while (result.endsWith(')')) {
    const opens = (result.match(/\(/g) ?? []).length;
    const closes = (result.match(/\)/g) ?? []).length;
    if (opens >= closes) break;
    result = result.slice(0, -1);
  }
  return result;
}

/**
 * The first http(s) URL in `pasted`, with tracking parameters removed, or
 * `null` when there is no URL in it at all - which is the signal that the user
 * pasted posting text rather than a link.
 */
export function normalizeJobUrl(pasted: string | null | undefined): string | null {
  const match = URL_IN_TEXT.exec(pasted ?? '');
  if (!match) return null;

  const candidate = dropUnbalancedTail(match[0].replace(TRAILING_PUNCTUATION, ''));

  let url: URL;
  try {
    url = new URL(candidate);
  } catch {
    return null;
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') return null;

  const hostRules = HOST_TRACKING.find(rule => rule.host.test(url.hostname));
  for (const key of [...url.searchParams.keys()]) {
    if (UNIVERSAL_TRACKING.test(key) || hostRules?.params.has(key)) {
      url.searchParams.delete(key);
    }
  }

  return url.toString();
}
