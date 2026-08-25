# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

### Backend (run from repo root)

```bash
./gradlew compileJava          # compile only
./gradlew test                 # unit tests (excludes AngularTemplateApplicationTests which needs a live DB)
./gradlew bootRun              # starts backend + builds Angular first (port 8060)

# Run a single test class
./gradlew test --tests "de.samply.manager.services.CompanyServiceTest"
```

### Frontend (run from `AppClient/`)

```bash
npm start                      # dev server on :4200, proxies /api /login /oauth2 → :8060 (logout is POST /api/logout)
npx ng test --watch=false --browsers=ChromeHeadless
npx ng test --watch=false --browsers=ChromeHeadless --include="**/company-form/**"
```

Karma needs a Chrome/Chromium binary. The devcontainer installs Chromium and sets
`CHROME_BIN` for you (see `.devcontainer/devcontainer.json`), and it is found
automatically wherever one is on `PATH` (GitHub's runners, most desktops).
Outside those, install one and export the variable first:

```bash
sudo apt-get update && sudo apt-get install -y chromium   # Debian/Ubuntu
export CHROME_BIN=/usr/bin/chromium
export CHROME_BIN=/opt/homebrew/bin/chromium              # macOS/Homebrew
```

### Dev infrastructure

```bash
cd dev && docker compose up -d                        # Postgres, Garage (S3), Gotenberg, Traefik
cd dev && docker compose -f authentik.yml up -d       # Authentik (OIDC), separate stack
```

## Architecture

### Tech stack

Spring Boot 3.5.16 · Java 26 · Lombok 1.18.38 · Angular 22 standalone · PostgreSQL · Garage S3 · Gotenberg (LibreOffice PDF) · OIDC via Authentik

### Build pipeline

`bootRun`/`build` trigger `npmBuild` → `copyFrontend`, which compiles Angular and copies the output into `src/main/resources/static/` so Spring Boot serves it. The frontend is not a separate deployment.

### Authentication & role mapping

The application has exactly three roles, defined once by the `AppRole` enum: `USER`, `ADVISOR`, `REVIEWER`. Their authorities (`ROLE_USER`, …) are derived from the enum constant, never from the name of the identity provider group that conferred them — which is what keeps the hardcoded `hasRole('ADVISOR')` in `SecurityConfig` and the `@PreAuthorize` annotations on the advisor/reviewer controllers correct when a deployment renames its groups.

**One translation point.** `SecurityRolesProperties` (`job-manager.security` in `application.yml`) names the groups that confer each role, and `RoleMapper` is the only place that translation happens. Because the values are just strings the provider emits, any IdP works without a code change: Authentik group names, an Entra ID group object GUID, a Keycloak role. `claim` selects which claim to read (default `groups`); several values may confer one role. Matching is case-insensitive.

A group the configuration does not mention **confers nothing** — the application grants only its own three roles, so a group named `advisor` belonging to another application in the same directory cannot confer advisor rights here. Misconfiguration (a role no group confers, one group conferring two roles) fails startup rather than degrading quietly.

`GroupsGrantedAuthoritiesMapper` applies `RoleMapper` at login, reading the claim from the ID token and falling back to userinfo. `RoleCheckSuccessHandler` rejects a login that holds none of the three roles — holding no role is not the same as holding `USER` — honours the role picked via `/api/login/as/{role}`, and upserts a `UserProfile` row.

`GET /api/me` returns `roles` (canonical names, whitelisted through `AppRole.fromAuthority`, so non-role authorities like `OIDC_USER` cannot leak into it). The Angular `AuthService` subscribes with `shareReplay(1)` and derives `isUser$`, `isAdvisor$`, `isReviewer$` from a single `hasRole()` helper.

Unauthenticated `text/html` requests are redirected to OAuth; API requests receive a 401. `WebController` catches all SPA routes (up to 4 path segments) and forwards to `index.html`.

### Per-user data isolation

`Company.userId` stores the OIDC subject string. `CompanyService` always filters by `userId` and throws `403 FORBIDDEN` on update/delete when the caller's subject doesn't match. All controllers extract `user.getSubject()` from `@AuthenticationPrincipal OidcUser`.

### Document access

`DocumentAccess` entity stores `(documentId, reviewerSubject)` pairs. Users grant reviewers access via `POST /api/documents/{id}/access`. `ReviewerController` queries `DocumentAccess` to build a grouped view of users who have shared documents with the caller.

### Cover letter generation

Two providers exist side by side. Both share `CoverLetterLabels` (salutations, subject/greeting prefixes, closing formula) so a contact is greeted identically whichever one is used.

**.docx provider** — `WordCoverLetterService` fills mail-merge fields in a `.docx` template using docx4j, then POSTs the filled file to Gotenberg (`/forms/libreoffice/convert`) as multipart to get a PDF back. Template files are stored in Garage S3 via `DocumentStorageService`.

**HTML provider** (`de.samply.manager.coverletter`, `/api/html/cover-letter`) — Thymeleaf → HTML → Gotenberg (`/forms/chromium/convert/html`). The pipeline is `CoverLetterTemplate` (editable data from the frontend) → `CoverLetterAssembler` → `CoverLetterModel` → `HtmlCoverLetterRenderer` or `TextCoverLetterRenderer`.

Rules this split enforces, in order of how easily they are broken:

- **The layout is a server invariant.** `templates/cover-letter/din5008.html` and its DIN 5008 measurements never leave the backend, so the geometry is a guarantee rather than a claim. The frontend edits blocks and style *values*; it never computes a millimetre.
- **One logic, two output formats.** `?format=text` renders the linearized preview from the same assembled `CoverLetterModel` as `?format=pdf`. Never reimplement placeholder or layout logic in TypeScript to render a preview - preview and PDF would diverge.
- **Sanitize, then substitute.** `MarkupSanitizer` reduces block markup to an inline subset (`b/strong/i/em/u/br/span/a`) *before* `PlaceholderResolver` inserts HTML-escaped values. The reverse order would let a company name containing angle brackets reach the template as markup.
- **`StyleSettings` is untrusted input.** `StyleSettingsValidator` fills unset components with `StyleSettings.din5008FormB()` and rejects impossible geometry. Its `fontFamily` whitelist matters: that value is the only style setting written into the stylesheet as text, and the Thymeleaf CSS inlining used there is the unescaped `[(${...})]` form (the escaped `[[...]]` form emits CSS identifier escapes like `\32 4\.1mm`, which Chromium does not read as a length).
- **`CssLengths` formats locale-free.** A `24,1mm` produced under a German default locale is an invalid CSS length and Chromium drops the declaration silently.

`Din5008PdfGeometryTest` prints a letter through the dev Gotenberg and reads the text coordinates back with PDFBox, asserting each line lands in its DIN zone. It skips itself when Gotenberg is unreachable (`cd dev && docker compose up -d gotenberg`).

### Angular routing & guards

Function-based guards in `AppClient/src/app/core/guards/`:
- `authGuard` — checks `/api/me` directly (used to protect the login-required shell)
- `advisorGuard` / `reviewerGuard` — check `AuthService.isAdvisor$` / `isReviewer$`; redirect to `/forbidden` on failure
- `userGuard` — requires the `USER` role for user-only routes (`/companies`, `/applications`, `/documents`)

Home components for each role redirect away if the role doesn't match (advisors → `/advisor`, reviewers → `/reviewer`).

## Backend conventions

### Error handling

Services must throw `de.samply.manager.exception.ApiException` subtypes (`NotFound`, `Forbidden`, `Conflict`, `BadRequest`, `UnsupportedMediaType`, `Unauthorized`, `BadGateway`, `InternalServerError`) instead of constructing `ResponseStatusException` inline. `GlobalExceptionHandler` is the single place that maps exceptions to the `{status, error, message}` response body — add a new `@ExceptionHandler` there (or a new `ApiException` subtype) rather than handling errors ad hoc in a controller or service.

### No hardcoded user-facing strings

Error messages and other user-facing text belong in `src/main/resources/messages*.properties`, resolved via the injected `MessageSource` — never as string literals in Java. Two patterns are in use depending on whether the text varies by the `Language` enum:
- **Language-dependent text** (e.g. cover letter labels, salutations): keyed the same across `messages.properties`/`messages_de.properties`/`messages_en.properties`/`messages_nl.properties`, resolved via `Language.locale()` (see `CoverLetterService.label(key, language)`).
- **Locale-independent text** (e.g. API error messages, like `error.snapshot.*` in `JobPostingSnapshotService`): keyed only in the base `messages.properties`, resolved with `messageSource.getMessage(key, args, Locale.ROOT)` — Spring falls back to the base bundle when a locale-specific file lacks the key.

## Testing notes

### Java 26 + Mockito

The build is configured with `-Dnet.bytebuddy.experimental=true` and the Mockito agent (`-javaagent:…mockito-core.jar`) to work around Byte Buddy's Java 26 limitation. These are already in `build.gradle`; no extra flags are needed.

`AngularTemplateApplicationTests` (full `@SpringBootTest`) is excluded because it requires a live PostgreSQL and OIDC server. Add `@SpringBootTest` integration tests to the same exclusion pattern only when a test DB is available.

### @WebMvcTest setup

`src/test/resources/application.yml` provides:
- H2 in-memory DB
- Static OAuth2 provider URLs (no `issuer-uri` that would trigger a live OIDC discovery request)

`@WebMvcTest` on controllers needs `@MockitoBean` for `CompanyService`, `RoleCheckSuccessHandler`, and `GroupsGrantedAuthoritiesMapper` (the latter two are referenced by `SecurityConfig`). Use `SecurityMockMvcRequestPostProcessors.oidcLogin()` to supply a test principal, with `.authorities(…)` to give it a role.

`oidcLogin()` sets authorities directly and so bypasses the authorities mapper. To exercise the group-name-to-role translation together with the `hasRole` checks, import the real `GroupsGrantedAuthoritiesMapper` and run the claim through it to build the authorities — `RenamedGroupAuthorizationTest` does this, and is the regression guard for a deployment that renames its IdP groups.

## Accessibility requirements

See `AppClient/CLAUDE.md` for the full accessibility checklist (skip link, landmark regions, `aria-sort`, `role="alert"`, `<dl>` for key-value data, etc.). All UI changes must maintain those requirements.
