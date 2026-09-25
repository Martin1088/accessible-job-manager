/**
 * The shape of `GET /api/legal/{slug}` - this deployment's own privacy policy or legal
 * notice.
 *
 * Structured data, never markup: the renderer fixes the accessible semantics (heading
 * level, `<section aria-labelledby>`, `<dl>` for key-value pairs) so a deployment supplies
 * wording and cannot supply a document that breaks them. Mirrors the Java records in
 * `de.samply.manager.legal`.
 */

export interface LegalDefinition {
  readonly term: string;
  readonly value: string;
  /** Held to a scheme allow-list by the backend before it ever reaches the browser. */
  readonly href?: string;
}

export interface LegalParagraphBlock {
  readonly type: 'paragraph';
  readonly text: string;
}

export interface LegalListBlock {
  readonly type: 'list';
  readonly items: readonly string[];
}

export interface LegalDefinitionsBlock {
  readonly type: 'definitions';
  readonly items: readonly LegalDefinition[];
}

export type LegalBlock = LegalParagraphBlock | LegalListBlock | LegalDefinitionsBlock;

/**
 * A heading and the blocks under it. Blocks are an ordered list rather than one typed
 * body because the rights section is a lead-in paragraph, then a list, then a closing
 * paragraph - all under a single heading the legal text does have.
 */
export interface LegalSection {
  readonly heading: string;
  readonly blocks: readonly LegalBlock[];
}

export interface LegalDocument {
  readonly slug: LegalSlug;
  /** What was asked for. Differs from `language` when this deployment has no such translation. */
  readonly requestedLanguage: string;
  /** The language of the text below - drives the `lang` attribute (WCAG 3.1.2). */
  readonly language: string;
  readonly title: string;
  readonly intro?: readonly string[];
  readonly sections: readonly LegalSection[];
}

/**
 * The documents a deployment can publish. `datenschutz` and `impressum` back the two
 * routes and ship bundled defaults; `demo-hinweis` has none, so it is absent unless a
 * deployment publishes it - which is what keeps a "this is only a test instance" warning
 * off a production login page. Slugs stay German, like the public URLs.
 */
export type LegalSlug = 'datenschutz' | 'impressum' | 'demo-hinweis';
