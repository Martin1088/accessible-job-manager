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

- Review queue: positions that turned up - from the posting import or the paste
  flow - wait at `/queue` instead of joining the company list directly. Accept
  and Dismiss per row, each announcing the outcome and how many are left; focus
  moves to the next row before the acted-on one is removed, so working through
  the list never loses the reading position. Existing positions are unaffected:
  the column default is `ACCEPTED`. The queue belongs to the applicant -
  `/api/positions/**` answers 403 to an advisor or reviewer.
- Release process: `CHANGELOG.md`, `./release.sh`, and a workflow that builds
  and publishes the container image to `ghcr.io` when a `v*` tag is pushed.

### Changed

- The build version comes from `git describe` instead of a hardcoded
  `0.0.1-SNAPSHOT`, so jar, tag and image tag are the same version. The image
  build passes it in with `-Pversion=`, having no `.git` of its own.

### Fixed
