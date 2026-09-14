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
npx ng test --watch=false
npx ng test --watch=false --include="**/company-form/*.spec.ts"

npm run lint                   # everything; advisory warnings, exit 0
npm run lint:a11y              # template accessibility rules only, errors only - the CI gate
```

The `--include` pattern has to end in `*.spec.ts`. A directory glob such as
`**/company-form/**` also matches that component's `.html` and `.scss`, which the test
builder then tries to bundle as entry points and fails with "No loader is configured
for .scss files".

Do not add `--browsers=ChromeHeadless`. `karma.conf.js` already picks the launcher:
as root — which is what the devcontainer runs as — it uses `ChromeHeadlessNoSandbox`,
because Chromium refuses to start as root without `--no-sandbox`. Everywhere else
(GitHub's runners, a desktop) it picks the stock, sandboxed `ChromeHeadless`. Naming the
browser explicitly overrides that choice and fails in the devcontainer with
`Running as root without --no-sandbox is not supported`. (`build.yml` does pass the
flag; on a non-root runner it selects the launcher `karma.conf.js` would have chosen
anyway, so it is redundant there rather than wrong.)

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

### Object storage

Two providers sit behind the `StorageService` interface, selected by `storage.provider`: `GarageStorageService` (S3 via AWS SDK v2, the default) and `AzureBlobStorageService`. Object keys are `{userId}/{type_lowercase}/{uuid}.{ext}`, so the owning subject is in the key prefix.

**Every read goes through the backend.** `DocumentService.bytes`, `ReviewerController`, `JobPostingSnapshotService` and `WordLetterTemplateService` all stream the object through the application, which is what enforces the ownership and `DocumentAccess` checks — a presigned URL would bypass exactly the check the sharing model rests on. `StorageService.presignedGet` has no callers for that reason.

**SSE-C encryption at rest is S3-only and off by default** (`storage.s3.encryption.mode`, `none` or `sse-c`). `SseCustomerKey` is the single place the three `x-amz-server-side-encryption-customer-*` headers are built; it is a null object, so `upload` and `download` carry no conditional and cannot diverge. Rules that are easy to break:

- **The MD5 header is the digest of the raw decoded key bytes, not of the Base64 text.** Both are valid Base64 of 16 bytes, so the mix-up type-checks and only fails against a live Garage. `SseCustomerKeyTest` pins it against fixed vectors.
- **`delete` takes no key** — `DeleteObjectRequest` has no SSE-C fields at all.
- **`contentLength` stays the plaintext length.** SSE-C encrypts server-side, so the object is stored and reported at the size sent. Client-side encryption would break that contract, which is why it was not chosen.
- **`presignedGet` throws when encryption is on.** A browser following the URL cannot send the headers, and signing them in would mean shipping the master key to the browser.
- **The key is validated at startup** (`S3Properties.Encryption`), so a missing or malformed key fails the boot instead of the first upload — including under `storage.provider=azure`, since `S3Config` is not conditional. `Encryption.toString()` is masked; note that the sibling `secretKey` is not, and that adding `spring-boot-starter-actuator` would expose both through `/actuator/configprops`.
- **Multipart is not covered.** Every upload today is a single `PutObject` under the 20MB limit; multipart would have to repeat the headers on create, every part, and complete.

### Cover letter generation

Two providers exist side by side. Both share `CoverLetterLabels` (salutations, subject/greeting prefixes, closing formula) so a contact is greeted identically whichever one is used.

**.docx provider** — `WordCoverLetterService` fills mail-merge fields in a `.docx` template using docx4j, then POSTs the filled file to Gotenberg (`/forms/libreoffice/convert`) as multipart to get a PDF back. Template files are stored in Garage S3 via `StorageService`.

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

## Release discipline

Versions are git tags; `build.gradle` derives `version` from
`git describe --tags --always --dirty`, so there is no version literal to bump.
`-Pversion=` overrides it, which is how the Docker build gets a version without
`.git` in its context.

`CHANGELOG.md` follows Keep a Changelog and is maintained by hand: **a user-facing
change adds its entry under `## [Unreleased]` in the same commit**, never
reconstructed at release time. Commit subjects follow Conventional Commits
(`feat:`, `fix:`, `docs:`, `chore:`) as a convention - nothing enforces it.

`./release.sh <version>` cuts the release (changelog, commit, tag, merge to
`main`, push, GitHub release); pushing the tag triggers
`.github/workflows/release.yml`, which publishes the image. Details in
`docs/releasing.md`.

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

### Job posting fixtures

`ExtractionFixtureTest` runs the whole extractor chain over saved postings in
`src/test/resources/postings/` and writes each run's tier-by-tier report to
`build/reports/postings/<name>.txt`. A fixture is two files — `<name>.html` (the
page, saved from a browser) and `<name>.properties`, which must set `url=` and
may add `boardHint=` and any number of `expect.<field>=` assertions.

```bash
./gradlew test --tests "*ExtractionFixtureTest"
```

The URL is not decoration: Jsoup resolves links against it, the ATS tier
dispatches on its host, and several heuristics read the path — a fixture run
against the wrong URL is not the extraction the server would have performed.
A fixture with no `expect.*` keys is reported but not asserted, which is the
state a posting is in while its parse is still being worked on; adding the keys
is what turns a fixed parse into a guarded one.

This replaced `POST /api/posting/extractors/test`, a debug endpoint that shipped
in the production artifact. **Do not reintroduce a live-URL debug endpoint for
this.** The pipeline is a pure function of a parsed `Document`, so it needs no
server to exercise, and the boards worth debugging are the ones a server cannot
fetch — `JobPostingParserService.upstreamFailure` documents Indeed answering 403
to every request from a server, so a live-URL run against one could only ever
show its own 502.

The ATS tier is constructed with **no adapters**. Ashby, Personio, OracleHCM and
Comeet each hold a `RestClient` and call the board's API, which a fixture run
must not do; that tier's coverage lives in `ComeetAdapterTest` via
`MockRestServiceServer`. Every other tier is a pure function of the document, so
the harness touches no network.

### Live probe for the `/overview` path

`LiveOverviewProbeTest` runs the real `/overview` chain against a live URL and
writes the result to `build/reports/postings/live-overview.txt`:

```bash
./gradlew test --tests "*LiveOverviewProbeTest" -Dposting.url=https://…
```

Opt-in — without `-Dposting.url` it skips, so a normal `./gradlew test` makes no
outbound request. It skips again if Gotenberg is down, and reports the LLM half
as skipped if Ollama is down, so the render is never lost to a missing model.

**It shows the LLM result, not a tier report.** Gotenberg returns a PDF and only
`PDFTextStripper` text is read back, so the JSON-LD and microdata tiers cannot
run on it — that is why `full-chain` still fetches HTML itself. For the tier
chain use the fixture harness above.

An upstream `401`/`403`/`429` is reported as *the board refusing this host*
rather than a pipeline failure, because that is a real and common outcome:
Indeed answers `403` to Gotenberg's Chromium exactly as it does to the plain
`HttpClient`, including for its own homepage, and from a datacenter IP a browser
User-Agent only changes the refusal to `401`. The supported route for such a
board is a printed PDF through `/overview-pdf`.

`DevServices` (`src/test/java/de/samply/manager/testing/`) resolves the dev
service URLs and holds the shared TCP reachability probe that decides these
skips — `Din5008PdfGeometryTest` and `CoverLetterPdfUaTest` now use it too,
where each previously carried its own copy. `build.gradle`'s `test` block
forwards `posting.url`, `gotenberg.url`, `ollama.url` and `ollama.model` into
the test JVM; without that forwarding a `-D` on the Gradle command line reaches
the daemon and never the tests.

## Automated accessibility checks

Three layers, each answering a question the one below it cannot:

- **Template linting** (`npm run lint:a11y`, runs in CI) - `angular-eslint`'s
  accessibility preset over every `.html` in `AppClient/src`. All templates pass today,
  so any finding is a regression from the commit under test. The general `npm run lint`
  additionally reports pre-existing TypeScript style debt as warnings; it is deliberately
  not a gate, and `eslint.config.js` explains which rules were demoted and why.
- **axe-core in Karma** - every component spec ends with
  `await expectNoAxeViolations(fixture)`. The helper lives in
  `AppClient/src/testing/a11y.ts`; it disables the page-level rules that are meaningless
  against a component fragment, and collapses each violation to id/impact/selector rather
  than dumping the full node HTML. This layer is what caught `#64748b` failing contrast
  against `--color-bg`, which is why muted text now goes through `--color-text-muted`
  rather than a hardcoded hex.
- **PDF/UA-1 via veraPDF** (`CoverLetterPdfUaTest`) - reads the generated cover letter's
  structure tree, where `Din5008PdfGeometryTest` reads only its coordinates. Requires the
  dev Gotenberg and skips itself without it. `HtmlToPdfConverter` sends
  `generateTaggedPdf` and `din5008.html` carries `th:lang` to make the format reachable
  at all.

Automated rules catch roughly a third of real barriers. This is a regression net, not
evidence of conformance - whether a sort announcement is *useful*, or a braille line
reads in a sensible order, still needs the manual pass.

## Accessibility requirements

See `AppClient/CLAUDE.md` for the full accessibility checklist (skip link, landmark regions, `aria-sort`, `role="alert"`, `<dl>` for key-value data, etc.). All UI changes must maintain those requirements.
