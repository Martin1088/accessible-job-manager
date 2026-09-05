/**
 * Recognising the company a user is typing among the ones they already have.
 *
 * Company names are creative: diacritics, symbols, punctuation, legal forms and
 * several accepted spellings of the same word all describe one company. No
 * canonical form gets every pair right, which is why this feeds a hint on the
 * form and never a uniqueness rule - a wrong match here costs the user a
 * glance, where a wrong match in a database constraint would refuse to save a
 * company that really does exist.
 *
 * Being only a hint is also why matching is deliberately generous, and why
 * legal forms are left alone: "Meyer GmbH" and "Meyer AG" are two different
 * legal entities, so they must not read as the same company - and typing
 * "Meyer" still surfaces both through the partial match below.
 */

/** Dropped, not folded: NFKC would turn "™" into the letters "TM". */
const SYMBOLS = /[™®©℠]/g;
const COMBINING_MARKS = /[̀-ͯ]/g;
const NON_ALPHANUMERIC = /[^\p{L}\p{N}]+/gu;

/** Punctuation and spacing carry no identity: "ABC-Tech" is "ABC Tech". */
function collapse(value: string): string {
  return value.replace(NON_ALPHANUMERIC, ' ').trim();
}

/**
 * The forms a name is compared under. Two are needed because an umlaut has two
 * accepted spellings: "Müller" is written "Mueller" as often as it is folded to
 * "Muller", and a user typing either has to reach the company stored under the
 * other. A name without umlauts produces a single form.
 */
export function nameKeys(name: string | undefined | null): string[] {
  const base = (name ?? '').replace(SYMBOLS, '').normalize('NFKC').toLowerCase();
  if (!base.trim()) return [];

  const transliterated = collapse(base
    .replace(/ä/g, 'ae').replace(/ö/g, 'oe').replace(/ü/g, 'ue').replace(/ß/g, 'ss'));
  const folded = collapse(base
    .replace(/ß/g, 'ss').normalize('NFKD').replace(COMBINING_MARKS, ''));

  return transliterated === folded ? [folded] : [transliterated, folded];
}

/** True when both names describe the same company under any of their forms. */
export function isSameName(a: string | undefined | null, b: string | undefined | null): boolean {
  const keys = nameKeys(b);
  return nameKeys(a).some(key => keys.includes(key));
}

/**
 * True when `name` spells out something that starts with, or contains, what has
 * been typed - the looser test behind the "similar companies" list, which is
 * read while the name is still half-written.
 */
export function containsName(name: string | undefined | null, typed: string): boolean {
  const keys = nameKeys(name);
  return nameKeys(typed).some(part => keys.some(key => key.includes(part)));
}
