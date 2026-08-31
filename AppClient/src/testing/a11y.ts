// axe-core assertion helper for component specs.
//
// Two things this wrapper exists to get right, both of which bite when axe is
// called directly from a spec:
//
// 1. Scope. axe's default ruleset assumes a whole document. Run against a
//    component fixture it reports `region`, `page-has-heading-one`,
//    `html-has-lang` and friends - findings about the test harness page, not
//    about the component. Those page-level rules are disabled here; they belong
//    to a full-page check (see the focus-order/Playwright step), not to a unit
//    spec. Everything else in the WCAG 2.0/2.1 A and AA tags stays on.
//
// 2. Report size. A raw axe violation carries the complete outer HTML of every
//    affected node. Dumped into a Karma failure - or into a Claude Code session -
//    that is by far the most expensive output in the whole a11y toolchain, and
//    the HTML is the part you least need: the selector already tells you where
//    to look. `summarize()` reduces each violation to id, impact, help text and
//    target selectors.
//
// Usage in a spec:
//
//   const fixture = TestBed.createComponent(FooComponent);
//   fixture.detectChanges();
//   await expectNoAxeViolations(fixture);

import axe, { type AxeResults, type ElementContext, type RunOptions, type Result } from 'axe-core';
import type { ComponentFixture } from '@angular/core/testing';

/** Rules that only make sense against a complete document, not a component fragment. */
const PAGE_LEVEL_RULES = [
  'region',
  'page-has-heading-one',
  'html-has-lang',
  'html-lang-valid',
  'landmark-one-main',
  'bypass',
  'document-title',
] as const;

const DEFAULT_OPTIONS: RunOptions = {
  runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
  rules: Object.fromEntries(PAGE_LEVEL_RULES.map((id) => [id, { enabled: false }])),
  resultTypes: ['violations'],
};

/** One violation, stripped of the node HTML that makes raw axe output so bulky. */
export interface AxeFinding {
  id: string;
  impact: string;
  help: string;
  targets: string[];
}

export function summarize(violations: Result[]): AxeFinding[] {
  return violations.map((v) => ({
    id: v.id,
    impact: v.impact ?? 'unknown',
    help: v.help,
    targets: v.nodes.flatMap((n) => n.target.map(String)),
  }));
}

function format(findings: AxeFinding[]): string {
  return findings
    .map((f) => `${f.impact}: ${f.id} - ${f.help} [${f.targets.join(', ')}]`)
    .join('\n');
}

/** Runs axe against a fixture (or a raw element) and fails the spec on any violation. */
export async function expectNoAxeViolations(
  target: ComponentFixture<unknown> | ElementContext,
  options: RunOptions = {},
): Promise<void> {
  const context = isFixture(target) ? (target.nativeElement as ElementContext) : target;
  const results: AxeResults = await axe.run(context, { ...DEFAULT_OPTIONS, ...options });
  const findings = summarize(results.violations);

  expect(findings.length)
    .withContext(findings.length ? `\n${format(findings)}\n` : '')
    .toBe(0);
}

function isFixture(value: unknown): value is ComponentFixture<unknown> {
  return typeof value === 'object' && value !== null && 'nativeElement' in value;
}
