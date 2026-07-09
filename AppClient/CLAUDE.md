
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
