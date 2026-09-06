# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
maintained by hand — an entry belongs in the same commit as the change it
describes. Versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html); a released version
is a git tag `vX.Y.Z`, and `./release.sh` is what creates one.

**Nothing has been released yet.** `[Unreleased]` is what the first tag will
publish. Everything below it, under *Project history*, predates this file and
was reconstructed from the git log afterwards — grouped by month and by theme
rather than by version, because there were no versions. It is there for
orientation, not as a record of releases that happened.

## [Unreleased]

### Added

- Release process: `CHANGELOG.md`, `./release.sh`, and a workflow that builds
  and publishes the container image to `ghcr.io` when a `v*` tag is pushed.

### Changed

- The build version comes from `git describe` instead of a hardcoded
  `0.0.1-SNAPSHOT`, so jar, tag and image tag are the same version. The image
  build passes it in with `-Pversion=`, having no `.git` of its own.

### Fixed

## [Project history]

Reconstructed from the git log. Dates are when the work landed on `develop`,
not release dates.

### 2026-09 — Advisor suggestions

- Advisors suggest a position to one of their users; the user accepts or
  declines. Accepting copies the company and the position into the user's own
  catalogue, together with the archived posting PDF, so the suggestion turns
  into something they own rather than a reference to the advisor's data.
- Advisor job import: the posting importer from the user's home page, with the
  advisor's ending — hand the extracted posting straight to one of their users.
- Job posting parser extended; failing tests around the advisor work fixed.

### 2026-08 — Cover letters, posting import, advisor role, public demo

- Cover letter generation in two providers side by side: mail-merge into a
  `.docx` template via docx4j, and a Thymeleaf → HTML → Gotenberg pipeline
  whose DIN 5008 geometry lives on the server and never reaches the frontend.
  The same assembled model renders the PDF and the linearized text preview.
- PDF/UA-1 conformance of the generated letter checked with veraPDF, and its
  DIN zones checked by reading text coordinates back with PDFBox.
- Job posting import: parse a posting from a URL through a local Ollama model,
  with a pasted-text and an uploaded-PDF fallback for boards that refuse the
  server, plus an archived snapshot of the posting filed against the position.
- Advisor role: assigned users, relationships between applicant, advisor and
  reviewer, and a job search against the Adzuna aggregator — off unless the
  operator configures their own key.
- Document sharing: a user grants a reviewer access to individual documents;
  revoking removes it from the reviewer's view while the document stays.
- Company and application overview export.
- Accessibility service and validation work; data tables retextured, given a
  card pattern on narrow screens and fixed dark-mode surfaces.
- A fully static demo build served from seed data on GitHub Pages, with a check
  that asserts the built bundle makes no `/api/` call at all.
- Authentik provisioned from a blueprint as the development identity provider;
  dev data kept in the workspace folder; DevPod and dev-environment docs.
- Login page, logo and mobile layout work.

### 2026-07 — Deployment, CI, i18n

- Runtime internationalisation (English, German, Dutch), account menu, and
  CRUD polish for applications and documents.
- User Guide page, legal pages, and a personalized cover letter template
  download on the user's home page.
- Storage abstraction behind one interface: Garage/S3 for development, Azure
  Blob for the deployed instance.
- Azure Container Apps deployment as a bicep template with OIDC wiring, moving
  from a self-hosted Postgres container to a managed Flexible Server.
- CI: Gradle build plus Angular Karma tests, Dependabot, and a container image
  pushed to GHCR on `main` and `develop`.
- Role mapping made deployment-independent: the identity provider's group names
  are configuration, so an Entra ID group GUID confers a role as readily as an
  Authentik group name.
- Job posting snapshot service; global exception handling with one place that
  maps exceptions to the API's error body.

### 2026-04 — 2026-06 — Domain model and first prototype

- Initial commit, the domain model around companies, positions and
  applications, and the first working build with S3-backed document storage.
- The three roles the application has ever since — applicant, advisor,
  reviewer — as a first prototype, with the navigation that carries them.
