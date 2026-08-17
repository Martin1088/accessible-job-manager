
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
