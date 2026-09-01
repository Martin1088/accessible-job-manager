
## Running the tests

```bash
npx ng test --watch=false
```

`karma.conf.js` picks the browser launcher: as root — which is what the devcontainer
runs as — it uses `ChromeHeadlessNoSandbox`, because Chromium refuses to start as root
without `--no-sandbox`. Everywhere else it uses the stock, sandboxed `ChromeHeadless`.
Passing `--browsers=ChromeHeadless` overrides the choice, which is what CI does.

A browser still has to exist. The devcontainer ships without one:

```bash
sudo apt-get update && sudo apt-get install -y chromium
export CHROME_BIN=/usr/bin/chromium
```

Because the file is supplied, `@angular/build:karma` no longer injects its own
defaults — `frameworks`, `plugins` and `reporters` are restated there and have to stay.

## Accessibility

Accessibility is a core requirement, not an afterthought:

- **Skip link** — "Skip to main content" visible on keyboard focus
- **Landmark regions** — `<header>`, `<main id="main-content">`, `<nav aria-label="…">`
- **Active page** — `aria-current="page"` on the active nav link (via Angular `routerLinkActive`)
- **Role-aware navigation** — separate `<nav>` blocks per role, hidden when not applicable
- **Data tables** — `<caption>`, `scope="col"` on all `<th>`, three-state sort with `aria-sort`, `role="status"` on empty state
- **Forms** — `aria-required`, `aria-describedby`, `<label>` for every input
- **Alerts** — `role="alert"` on error messages for live-region announcement
- **Definition lists** — `<dl>`/`<dt>`/`<dd>` for key-value profile data
- **Colour** — never hardcode a text colour. Muted/secondary text uses
  `var(--color-text-muted)`, defined once per theme in `src/styles.scss`. The hardcoded
  `#64748b` it replaced measured 4.37:1 against `--color-bg` (`#f5f5f5`) and failed AA;
  it passed only when it happened to sit on a white card, which is exactly the kind of
  background-dependent bug a literal hex hides. A new colour needs checking against the
  surface it will actually render on, in all three themes (default, high-contrast, dark).

## Internationalization (i18n)

Every user-facing string ships in all three locales at once —
`public/i18n/en.json`, `de.json`, `nl.json` — never just one. A component
with hardcoded English text is a latent bug, not a shortcut: the login page
went untranslated for a while for exactly this reason (it predated the rest
of the app's `| translate` convention) and nobody noticed until someone
tested it in German. Adding a key to only `en.json` reproduces that bug.

- Use the `TranslatePipe` (`{{ 'NAMESPACE.KEY' | translate }}`) in templates,
  not `TranslateService.instant()` in TypeScript — the pipe re-evaluates on
  a language switch, `instant()` freezes the value at the moment it ran (see
  `DataTableComponent`'s own comment on why it translates reactively).
- For text that depends on component state (e.g. one of a few error codes),
  branch in the template with `@switch` on the raw value and translate each
  case, rather than resolving the final string in TypeScript — see
  `login.component.html`'s handling of the `error` query param.
- A person's name or the product name (`Job Application Manager`,
  `Martin Jurk`) is not a translation key — it doesn't change per locale, and
  the value is byte-identical in all three files. It still *lives* in
  `LOGIN.TITLE` / `HOME.TITLE`, so read those rather than hardcoding the
  string: one edit renames the product everywhere. German compounds it with
  Durchkopplung (`Job-Application-Manager-Dashboard`), Dutch hyphenates only
  the appended noun (`Job Application Manager-dashboard`) — that is spelling,
  not translation. Check an existing key first before assuming everything
  needs one.
- Reuse this app's own established vocabulary for a concept instead of
  inventing new wording — role names in particular already have translated
  labels (`PROFILE.ROLE_USER/ROLE_ADVISOR/ROLE_REVIEWER`); a new feature
  that mentions a role should read those rather than retranslating it.

**Verify translations render, not just parse.** After adding or changing
keys:

- [ ] All three JSON files still parse (`node -e "JSON.parse(require('fs').readFileSync(path))"`
      per file, or just load the page — a syntax error breaks the whole
      bundle, not just the new key).
- [ ] Switch the language selector and confirm the new text actually
      changes — a missing key silently falls back to showing the raw
      `NAMESPACE.KEY` string instead of failing loudly.
- [ ] Check German specifically for layout overflow. German strings run
      noticeably longer than English for the same content (see this file's
      own German copy for proof) — a button or card sized to the English
      text is the most common place this breaks. Verify at both the
      mobile breakpoint and the widest layout the copy appears in.

## Mobile & Responsive Conventions

Screen-reader-first is the priority here too: visual adaptation for small
viewports must never change the accessibility tree. These rules complement
the `angular-developer` skill, which covers framework APIs but not layout
strategy.

### Breakpoint

One mobile breakpoint, defined once in `src/styles/_breakpoints.scss` and
imported everywhere it's needed:

```scss
$bp-mobile: 600px;

@mixin mobile {
  @media (max-width: $bp-mobile) {
    @content;
  }
}
```

A component pulls it in with `@use '<relative-path-to>/styles/breakpoints' as bp;`
and wraps mobile-only rules in `@include bp.mobile { … }`. Do not add further
breakpoints without a concrete need — fewer breakpoints means fewer layout
states to test with VoiceOver.

### Tables

Keep `<table>`. Native table semantics (row/column announcement, rotor
navigation, cell-by-cell reading) are the best-supported pattern in
VoiceOver — do not replace a table with `@angular/aria` Grid, which is for
interactive, cell-focusable rasters (editable cells, arrow-key traversal)
that this project does not have.

Required markup, exemplified by the shared `app-data-table`
(`AppClient/src/app/shared/data-table/`) and the bespoke tables in
`application-list` and `reviewer/home`:

- `<caption>` on every table.
- `scope="col"` on every `<th>`.
- `[attr.data-label]` on every `<td>`, matching its header text. This drives
  the mobile card layout via CSS `content: attr(data-label)`.
- The sort control is a real `<button>` inside the `<th>`, never a clickable
  `<th>` or a bare icon. Its decorative glyph carries `aria-hidden="true"`.

**Sorting** uses `aria-sort` (`ascending`/`descending`/`none`, only ever
non-`none` on the currently sorted column) *and* a `LiveAnnouncer` call on
every `sortBy()` — `aria-sort` alone is not reliably announced on change by
VoiceOver, so the announcement is what confirms the action, while
`aria-sort` covers state on re-read. Both are required. See
`DataTableComponent.announceSort()` for the pattern, and the
`TABLE.SORT_ANNOUNCE_*` keys in `public/i18n/*.json`.

**Mobile card layout is CSS-only** — `thead { display: none; }` plus
`tr { display: block; }` / `td { display: block; …; &::before { content:
attr(data-label); display: block; } }` under `@include bp.mobile`. The DOM
stays a single `<table>`, so VoiceOver still reports table structure and
header association per cell; there is no separate mobile template, no
`BreakpointObserver`, no JS. Never use `visibility: hidden` or `opacity: 0`
on `thead` here — the header row would stay in the layout and remain
reachable while looking hidden.

The card shape, shared by `app-data-table`, `application-list` and
`reviewer/home`:

- **Label above value**, not label-left/value-right. The older
  `justify-content: space-between` pairing left a wide empty gutter between
  a short label and a short value on a phone, and pushed long values
  (emails, filenames) onto a cramped second line.
- **The first cell is the card's heading** — larger, on the paper band. Its
  `data-label` stays as a small overline rather than being dropped, so a
  reader that announces `::before` content gets the same label/value pairing
  there as in every other cell.
- **An actions cell** (`td:has(button)`) switches back to a wrapping flex
  row under a full-width label, so several buttons sit side by side instead
  of stacking one per line.
- **A terracotta `border-inline-start`** marks where one card starts. The
  hairline `--rule` is ~1.4:1 against the card, which is fine for a
  decorative separator but too faint to be the only thing dividing two
  cards; the accent edge measures above 5:1 in every theme.

**Table colour is tokens only.** `app-data-table` carries the DIN 5008
stationery (`--paper`/`--card`/`--ink`/`--ink-soft`/`--rule`/`--accent`) in
its own stylesheet — pages no longer retexture it through a `::ng-deep`
mixin, and `styles/_correspondence.scss` no longer ships one. The
hard-coded `#fff`/`#f7f7f7` that stylesheet used to carry is exactly the bug
the colour rule above describes: in dark mode the cards stayed white while
the text followed `--color-text`, so a phone showed near-white values on
white. `reviewer/home` is outside the stationery's scope and follows the
`--color-*` family instead, but the same rule applies — no literal hex on a
table surface.

### Sticky elements

Any `position: sticky`/`fixed` element must add `padding-block-start:
env(safe-area-inset-top)` (needs `viewport-fit=cover` in the viewport meta
tag, see below) or offset by the cumulative height of any sticky element
above it. At most two stacked sticky elements; z-index descends down the
page. None of the current shell (`app.component.html`) is sticky — if that
changes, apply this before shipping it.

### Touch targets

Minimum 44×44 CSS px for interactive elements that are otherwise sized to
their glyph — this project applies it concretely to table header sort
buttons and row action/download buttons (`min-inline-size`/`min-block-size:
44px`), since those are the controls a mobile card layout puts directly
under a thumb. Keep at least 8px separation between adjacent targets so a
mis-tap doesn't trigger the neighbour.

### Forms

- Every input has a real `<label for>` — placeholder text is not a label,
  it disappears on focus and isn't announced consistently.
- Filter fields (search field, search term, year, month — see
  `company-list`, `application-list`, `documents`) are grouped in a
  `<fieldset>` with a `<legend>` (visually hidden via `.sr-only` is fine, to
  keep the existing visual design) so VoiceOver announces the group
  context.
- Inputs stack to full width below the breakpoint; no side-by-side pairs on
  mobile (see `.filter-fieldset`'s `@include bp.mobile` rule).
- Result-count/filter changes are announced via `LiveAnnouncer`, not left to
  visual inspection.

### Viewport

`index.html` must allow user zoom — never `user-scalable=no` or
`maximum-scale=1`:

```html
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
```

`viewport-fit=cover` is what makes `env(safe-area-inset-*)` return non-zero
values, and is required for the sticky-element rule above.

### Verification checklist

Run before merging any layout change:

- [ ] `ng build` passes
- [ ] `npm run lint:a11y` passes (template accessibility rules)
- [ ] `npx ng test --watch=false` passes — every component
      spec runs axe via `expectNoAxeViolations`, so a contrast or labelling regression
      fails here rather than at review
- [ ] No horizontal scroll at 375px viewport width
- [ ] VoiceOver rotor "Headings": every page section reachable
- [ ] VoiceOver rotor "Form Controls": every input has a distinct name
- [ ] VoiceOver rotor "Tables": each table found and named via `<caption>`
- [ ] Inside a table: cell-by-cell navigation announces column headers
- [ ] Sorting a column produces a spoken confirmation
- [ ] Sticky elements (if any) don't overlap the first content element on scroll
- [ ] Pinch-zoom to 200% works and no content is clipped
