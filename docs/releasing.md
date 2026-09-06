# Releasing

A release here is a git tag. Everything else - the changelog entry, the jar
version, the published image - follows from it, so there is exactly one thing
to get right.

```bash
./release.sh 0.2.0
```

## What the script does

1. Refuses to start unless the working tree is clean, `develop` is checked out
   and in sync with the remote, the tag does not exist yet, and `Unreleased`
   in `CHANGELOG.md` actually has entries.
2. Renames `## [Unreleased]` to `## [0.2.0] - <today>`, drops the subsections
   that stayed empty, and opens a fresh `Unreleased` block above it.
3. Commits `chore(release): 0.2.0`.
4. Creates the annotated tag `v0.2.0`, with the changelog section as the tag
   message.
5. Merges `develop` into `main` - fast-forward where possible, so that
   `git describe` on `main` is the tag itself rather than one commit past it.
6. Pushes `main`, `develop` and the tag.
7. Creates the GitHub release with `gh release create --notes-from-tag`, which
   is why the notes live in the tag message. Without `gh` installed it prints
   the command instead of failing.

Overrides, if a repository ever needs them: `RELEASE_BRANCH`, `MAIN_BRANCH`,
`REMOTE`, and `ALLOW_EMPTY_CHANGELOG=1`.

## The changelog

[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), maintained by hand.
No generator: a generator has to be fed conventional commits precisely enough
that the release notes read well, which is more work than writing the line
while the change is fresh.

**The rule that makes it worth having:** an entry goes into `Unreleased` in the
same commit as the change it describes. A changelog reconstructed from git log
at release time is a list of commits with extra steps.

## Commits

Conventional Commits as a convention, not a gate - no commitlint, no hook.

```
feat: advisor job import
fix: empty Personio location field on remote postings
docs: eval setup
chore: Gradle 8.x
```

The payoff is a readable `git log` and a faster changelog draft at release
time, not automation.

## Where the version comes from

`build.gradle` derives it from `git describe --tags --always --dirty`, minus a
leading `v`. Three consequences worth knowing:

- Between releases the version is the short commit hash - a build that is not a
  release says so.
- `-Pversion=0.2.0` overrides it. The image build needs that: `.dockerignore`
  excludes `.git`, so there is nothing to describe inside the builder, and the
  `APP_VERSION` build argument is passed straight through.
- Without git, or outside a repository, the build falls back to
  `0.0.0-SNAPSHOT` rather than failing. A version string is not worth a broken
  build.

## The image

Pushing a `v*` tag starts `.github/workflows/release.yml`, which runs the
tests (the Dockerfile builds with `-x test`), asserts that the version Gradle
reports matches the tag, and pushes
`ghcr.io/martin1088/accessible-job-manager` tagged `vX.Y.Z` and `latest`.

A pre-release tag such as `v1.0.0-rc1` does not move `latest` - the Azure
deployment in `dev/azure/main.bicep` defaults to that tag, and a release
candidate has no business landing there.
