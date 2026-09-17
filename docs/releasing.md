# Releasing

A release here is a git tag. Everything else - the changelog entry, the jar
version, the published image - follows from it, so there is exactly one thing
to get right.

```bash
./release.sh 0.2.0
```

## What the script does

1. Refuses to start unless `gh` is installed, the working tree is clean,
   `develop` is checked out and in sync with the remote, the tag does not
   exist yet, and `Unreleased` in `CHANGELOG.md` actually has entries.
2. Renames `## [Unreleased]` to `## [0.2.0] - <today>`, drops the subsections
   that stayed empty, and opens a fresh `Unreleased` block above it.
3. Commits `chore(release): 0.2.0` and pushes `develop`.
4. Opens a PR from `develop` into `main` with `gh pr create` and merges it
   with `gh pr merge --merge`. `main` carries a ruleset that refuses every
   direct push, with no bypass for anyone, so this is the only way in -
   see below.
5. Tags **`main`'s resulting merge commit** (not the commit made in step 3)
   as `v0.2.0`, with the changelog section as the annotated tag message, then
   pushes the tag.
6. Creates the GitHub release with `gh release create --notes-from-tag`, which
   is why the notes live in the tag message.

Overrides, if a repository ever needs them: `RELEASE_BRANCH`, `MAIN_BRANCH`,
`REMOTE`, and `ALLOW_EMPTY_CHANGELOG=1`.

### Why the tag moves to the merge commit

A GitHub PR merge always adds a commit on top of `main` - even when the merge
is a trivial fast-forward, the "Create a merge commit" button still creates
one. Tagging the commit made on `develop` in step 3 would leave `main`'s tip
one commit past the tag, which breaks `git describe --tags` on `main`
(`build.gradle` derives the version from it). Tagging `main`'s tip right after
the merge instead keeps `git describe` on `main` reporting the tag exactly, at
the cost of one extra step. The tag simply isn't cut until the content is
actually on `main`.

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
