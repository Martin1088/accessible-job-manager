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
npm start                      # dev server on :4200, proxies /api /login /logout /oauth2 → :8060
CHROME_BIN=/opt/homebrew/bin/chromium npx ng test --watch=false --browsers=ChromeHeadless
CHROME_BIN=/opt/homebrew/bin/chromium npx ng test --watch=false --browsers=ChromeHeadless --include="**/company-form/**"
```

### Dev infrastructure

```bash
cd dev && docker compose up -d   # Postgres, Garage (S3), Gotenberg, Traefik, Authentik
```

## Architecture

### Tech stack

Spring Boot 3.4.4 · Java 26 · Lombok 1.18.38 · Angular 19 standalone · PostgreSQL · Garage S3 · Gotenberg (LibreOffice PDF) · OIDC via Authentik

### Build pipeline

`bootRun`/`build` trigger `npmBuild` → `copyFrontend`, which compiles Angular and copies the output into `src/main/resources/static/` so Spring Boot serves it. The frontend is not a separate deployment.

### Authentication & role mapping

OIDC login is handled by Authentik. `GroupsGrantedAuthoritiesMapper` reads the `groups` claim (list of strings) from the ID token or userinfo and adds `ROLE_<GROUP_UPPERCASE>` to the Spring Security authorities. This means Authentik groups `"Advisor"` and `"Reviewer"` (any casing) become `ROLE_ADVISOR` and `ROLE_REVIEWER`. `RoleCheckSuccessHandler` upserts a `UserProfile` row on every successful login.

`GET /api/me` reads roles from `Authentication.getAuthorities()`, strips `ROLE_`, and filters out `OIDC_USER`. The Angular `AuthService` subscribes to `/api/me` with `shareReplay(1)` and derives `isUser$`, `isAdvisor$`, `isReviewer$`.

Unauthenticated `text/html` requests are redirected to OAuth; API requests receive a 401. `WebController` catches all SPA routes (up to 4 path segments) and forwards to `index.html`.

### Per-user data isolation

`Company.userId` stores the OIDC subject string. `CompanyService` always filters by `userId` and throws `403 FORBIDDEN` on update/delete when the caller's subject doesn't match. All controllers extract `user.getSubject()` from `@AuthenticationPrincipal OidcUser`.

### Document access

`DocumentAccess` entity stores `(documentId, reviewerSubject)` pairs. Users grant reviewers access via `POST /api/documents/{id}/access`. `ReviewerController` queries `DocumentAccess` to build a grouped view of users who have shared documents with the caller.

### Cover letter generation

`WordCoverLetterService` fills mail-merge fields in a `.docx` template using docx4j, then POSTs the filled file to Gotenberg (`/forms/libreoffice/convert`) as multipart to get a PDF back. Template files are stored in Garage S3 via `DocumentStorageService`.

### Angular routing & guards

Function-based guards in `AppClient/src/app/core/guards/`:
- `authGuard` — checks `/api/me` directly (used to protect the login-required shell)
- `advisorGuard` / `reviewerGuard` — check `AuthService.isAdvisor$` / `isReviewer$`; redirect to `/forbidden` on failure
- `userGuard` — rejects advisors and reviewers from user-only routes (`/companies`, `/applications`, `/documents`)

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

`@WebMvcTest` on controllers needs `@MockitoBean` for `CompanyService`, `RoleCheckSuccessHandler`, and `GroupsGrantedAuthoritiesMapper` (the latter two are referenced by `SecurityConfig`). Use `SecurityMockMvcRequestPostProcessors.oidcLogin()` to supply a test principal.

## Accessibility requirements

See `AppClient/CLAUDE.md` for the full accessibility checklist (skip link, landmark regions, `aria-sort`, `role="alert"`, `<dl>` for key-value data, etc.). All UI changes must maintain those requirements.
