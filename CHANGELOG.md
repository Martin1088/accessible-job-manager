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
- Optional encryption at rest for the S3/Garage document store (SSE-C). With
  `S3_SSE_C_MODE=sse-c` and a 32-byte `S3_SSE_C_KEY`, Garage stores every
  document encrypted under a key it does not keep, so losing the key loses the
  documents. Off by default, and switching it on does not convert what is
  already in the bucket — those objects become unreadable. A missing or
  malformed key fails startup rather than the first upload. S3/Garage only; the
  `azure` provider is unaffected.

### Removed

- `POST /api/posting/extractors/test`, which reported each extractor tier's raw
  output for a URL, is no longer part of the deployed application. It was a
  manual development tool that no part of the frontend called, and it reached
  the same fetch `/full-chain` performs — so nothing that could be learned from
  it is now out of reach for an authenticated session. The tier-by-tier view it
  provided moved to a fixture harness in the test sources, which additionally
  covers the case the endpoint never could: a board like Indeed that answers 403
  to any server, whose page has to come from a browser regardless.

### Changed

- Upgraded to Spring Boot 4.1.1 (from 3.5.16, which reached end of life on
  2026-06-30 with no further patches). Brings Spring Framework 7, Spring
  Security 7 and Hibernate 7. REST JSON (de)serialization now runs on Jackson
  3 by default; the job posting extraction and LLM client code that
  constructs `com.fasterxml.jackson.databind.ObjectMapper` directly keeps
  working unchanged via the added `spring-boot-jackson2` compatibility
  module, which Spring ships for exactly this gradual-migration case.
  `AdzunaJobSearchSource`, which talks to `RestClient` directly rather than
  through an injected `ObjectMapper`, now reads its response with Jackson 3's
  own `JsonNode` instead, since `RestClient`'s default converter no longer
  understands the Jackson 2 type. Boot 4 also modularized what used to be one
  `spring-boot-autoconfigure` jar: `RestClient.Builder` autoconfiguration
  moved to its own starter (added as a main dependency, since nothing pulled
  it in transitively any more), `ErrorController` moved packages, and the
  `@WebMvcTest`/`@DataJpaTest` slices and their security autoconfiguration
  moved into separate test starters (added alongside the existing test
  starter). None of this changes application behavior; the OIDC login flow
  and CSRF handling were re-verified end to end against the dev Authentik
  stack.
- Importing a posting from a URL now reads the page as a browser renders it.
  `POST /api/posting/overview` prints the URL through Gotenberg's Chromium and
  extracts from that, instead of fetching HTML and reducing it to text — so a
  posting whose body is written by JavaScript imports instead of arriving empty,
  which is the case the importer has been recording as `JS_REQUIRED` all along.
  Nothing is given up: that path never read the page's markup, only its visible
  text. The full-chain extraction still fetches HTML, because its JSON-LD and
  microdata tiers cannot survive a PDF. If Gotenberg is unreachable the old
  fetch still runs, so a plain posting imports during an outage.
- Job posting snapshots are tagged, so the archived PDF carries a structure tree
  and reading order. They are deliberately not marked as PDF/UA-1: the structure
  comes from a third party's markup that nothing has checked, and claiming
  conformance for it would mislead the assistive technology that trusts the
  claim. The snapshot keeps rendering with print styles — a screen layout put on
  paper lands the site's cookie banner across the posting and cuts lines off at
  the right margin — while the new extraction render uses screen styles, where a
  banner costs only tokens and a print stylesheet hiding the body would cost the
  import.
- Renders are cached per user, URL and purpose for a couple of minutes. One
  import asks for the same URL up to four times across `/overview`,
  `/full-chain`, `/snapshot-validate` and `/snapshot`; this keeps that at the two
  Chromium renders it took before, rather than four.
- The build version comes from `git describe` instead of a hardcoded
  `0.0.1-SNAPSHOT`, so jar, tag and image tag are the same version. The image
  build passes it in with `-Pversion=`, having no `.git` of its own.

### Fixed

- A failed extraction says which failure it was. Every transport error from
  either model provider was reported as "extraction service unavailable" with
  the cause discarded, so a read timeout, a refused connection and the model
  answering an error were indistinguishable — and none of them reached a log,
  because only unhandled exceptions were logged. Timeouts and upstream errors
  now have their own messages, the cause is kept (which is what lets
  `FailureCategory` classify a timeout as one), a 5xx is logged with it, and a
  failed model call is recorded in the import diagnostics under
  `LLM_SERVICE_UNAVAILABLE`.
- The Azure extraction client had no connect or read timeout at all, so an
  endpoint that accepted the connection and never answered held the request open
  indefinitely. Both providers now read `job-posting.parser.connect-timeout-seconds`
  and `read-timeout-seconds`, as the Gotenberg client already did.
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
- Deleting a company no longer fails with a raw database error when it has a job
  posting snapshot. `CompanyPosition` owns no inverse collections, so the cascade
  from `Company` deleted positions out from under three uncascaded foreign keys —
  `documents`, `suggestions` and `applications`. Snapshots and suggestions are now
  removed with the company, including the stored files; applications instead
  block the delete with a readable 409 naming how many, matching the guard
  `updateCompany` has always had. Storage cleanup is best-effort, so an object
  already gone cannot make a company undeletable.
- Gotenberg now runs with `--chromium-deny-private-ips`, which refuses a private
  address inside the process that actually connects — so it applies to every
  redirect hop, which `OutboundUrlGuard` structurally cannot reach. It does not
  replace the guard: the guard still covers CGNAT, `198.18.0.0/15`,
  `192.0.0.0/24` and `0.0.0.0/8`, which the flag does not. Applied to the Compose
  stacks and the Azure template alike.
- Calls to Gotenberg have connect and read timeouts. All three call sites built
  their own client with none, so a Gotenberg that accepted a connection and then
  stopped answering held the request open indefinitely.
- `PersonioAdapter` validates the URL it builds. It is the one ATS adapter that
  puts a value taken from the user's URL in the *host* position; its siblings pin
  the host and pass their extracted values as path or query parameters, which
  cannot reach a different address.
- Document upload now verifies the file's actual bytes (PDF `%PDF-`, DOCX
  `PK\x03\x04`) instead of trusting the client's `Content-Type` header, and
  reduces the supplied filename to a safe token (`[A-Za-z0-9._-]`, no path
  segments, max 255) before storing it. The stored MIME type is the document
  type's own, no longer a client-controlled string.
- Downloading a document whose object is missing from the bucket (S3/Garage or
  Azure) no longer surfaces as an unhandled `NoSuchKeyException`/
  `BlobStorageException` and a bodyless 500. Both `StorageService`
  implementations now translate a missing key into the same `{status, error,
  message}` shape every other failure gets, with the original exception kept
  as the cause so it still shows up in the log.
