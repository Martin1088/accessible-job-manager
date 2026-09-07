# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
maintained by hand — an entry belongs in the same commit as the change it
describes, not reconstructed at release time. Versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html); a released version
is a git tag `vX.Y.Z`, and `./release.sh` is what creates one.

Older changes than the first release below are not listed: they predate this
file, and inventing entries for them afterwards would make the log look more
reliable than it is.

## [Unreleased]

### Added

- Job posting import checks whether the PDF snapshot can be rendered right
  after a URL import succeeds. When it cannot (a consent wall, a page too heavy
  for Chromium, Gotenberg unreachable), the importer now says so and offers to
  attach a printed PDF, which is filed with the company as the posting record —
  previously that copy failed silently at company-creation time.
- Release process: `CHANGELOG.md`, `./release.sh`, and a workflow that builds
  and publishes the container image to `ghcr.io` when a `v*` tag is pushed.

### Changed

- The build version comes from `git describe` instead of a hardcoded
  `0.0.1-SNAPSHOT`, so jar, tag and image tag are the same version. The image
  build passes it in with `-Pversion=`, having no `.git` of its own.

### Fixed

- SSRF hardening. The URL validator that guarded job posting fetches existed in
  two identical copies; it is now one `OutboundUrlGuard`, used by the parser, the
  snapshot service and the Oracle HCM extractor (which previously concatenated an
  unvalidated host out of the user's URL). It additionally rejects ranges the JDK
  predicates miss — CGNAT `100.64.0.0/10`, `198.18.0.0/15`, `192.0.0.0/24`, and
  IPv4 addresses arriving as IPv4-mapped IPv6 — and pins a positive DNS cache TTL
  so the address it approved is the one actually connected to.
- Gotenberg is on its own Docker network with no route to Postgres, Garage or
  Traefik. Its Chromium fetches job posting URLs itself and follows its own
  redirects, so application-side validation cannot constrain it; the production
  egress requirement is documented under "Gotenberg network isolation" in the
  Readme.
- Document upload now verifies the file's actual bytes (PDF `%PDF-`, DOCX
  `PK\x03\x04`) instead of trusting the client's `Content-Type` header, and
  reduces the supplied filename to a safe token (`[A-Za-z0-9._-]`, no path
  segments, max 255) before storing it. The stored MIME type is the document
  type's own, no longer a client-controlled string.
