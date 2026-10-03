import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

import { LanguageService } from '../../core/language.service';
import { LegalDocument } from '../legal-document.model';

/**
 * Renders a legal document. Presentational - the route component owns the fetch.
 *
 * This template is where the accessibility of both legal pages lives: a deployment
 * supplies wording, and the heading level, the `<section aria-labelledby>` wiring, the
 * `<ul role="list">` and the `<dl>` are fixed here. That is the reason the documents are
 * structured data rather than Markdown or an HTML fragment - a legal department writes
 * the text, and nobody in that chain is going to check how a screen reader announces it.
 */
@Component({
  selector: 'app-legal-document',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './legal-document.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './legal-document.component.scss'
})
export class LegalDocumentComponent {
  readonly document = input<LegalDocument | undefined>(undefined);
  readonly loading = input(false);
  readonly error = input<unknown>(undefined);
  /** Shown as the title until the document's own arrives, and if it never does. */
  readonly titleKey = input.required<string>();

  /**
   * Where this document sits in the page's heading outline. `1` is a page of its own -
   * the document's title is the `<h1>` and its sections are `<h2>`. `2` embeds it under
   * an existing `<h1>`, as the demo notice does on the login page, so the title becomes
   * an `<h2>` and the sections `<h3>`. Skipping a level would break the rotor's outline
   * for exactly the readers this application is built for.
   */
  readonly headingLevel = input<1 | 2>(1);

  /** Set when the embedding page names a landmark after this document's title. */
  readonly titleId = input<string | null>(null);

  private readonly language = inject(LanguageService);

  /**
   * WCAG 3.1.2 (Language of Parts): only set when the document is not in the interface
   * language, which happens when this deployment did not publish it in that language.
   * Without it a German privacy policy shown to a Spanish interface is read out by a
   * Spanish speech synthesiser. Set unconditionally it would be redundant with
   * `<html lang>` and would stop meaning anything.
   */
  readonly documentLanguage = computed(() => {
    const doc = this.document();
    return doc && doc.language !== this.language.current() ? doc.language : null;
  });

  /**
   * Section ids are generated rather than authored. An operator-supplied id can duplicate,
   * start with a digit or contain a space - three ways to silently break the
   * section/heading association that nobody reviewing legal wording would spot.
   */
  sectionId(index: number): string {
    return `legal-sec-${index}`;
  }
}
