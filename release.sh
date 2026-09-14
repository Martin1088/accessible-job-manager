#!/usr/bin/env bash
#
# Cuts a release from the terminal: changelog, commit, PR into main, tag, and
# the GitHub release. The image is not built here - pushing the tag starts
# .github/workflows/release.yml, which builds and publishes it.
#
#   ./release.sh 0.2.0
#
# Every check that can fail does so before anything is written, and each abort
# says what to do instead. Nothing here asks a question: a release is a thing
# you either meant to do or did not.
#
# main is rule-protected on GitHub (no direct push, from anyone, ever), so the
# merge happens through a PR via gh, and the release tag is cut from main's
# resulting tip rather than from the commit made on develop - see the "tag
# main's merge commit" section below for why.
#
# Environment overrides: RELEASE_BRANCH (default develop), MAIN_BRANCH (main),
# REMOTE (origin), ALLOW_EMPTY_CHANGELOG=1 to release with an empty Unreleased
# section.

set -euo pipefail

RELEASE_BRANCH="${RELEASE_BRANCH:-develop}"
MAIN_BRANCH="${MAIN_BRANCH:-main}"
REMOTE="${REMOTE:-origin}"
CHANGELOG="CHANGELOG.md"

die() {
    echo "release: $1" >&2
    exit 1
}

usage() {
    echo "usage: ./release.sh <version>        e.g. ./release.sh 0.2.0"
}

case "${1:-}" in
    -h|--help) usage; exit 0 ;;
    "") usage >&2; exit 1 ;;
esac

[ "$#" -eq 1 ] || die "expected one argument, got $#"

VERSION="$1"
TAG="v${VERSION}"
DATE="$(date +%Y-%m-%d)"

# --- checks ----------------------------------------------------------------

echo "$VERSION" | grep -Eq '^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.]+)?$' \
    || die "'$VERSION' is not a version like 0.2.0 (leave the leading v off)"

git rev-parse --git-dir >/dev/null 2>&1 || die "not a git repository"
cd "$(git rev-parse --show-toplevel)"

[ -f "$CHANGELOG" ] || die "$CHANGELOG is missing"

command -v gh >/dev/null 2>&1 \
    || die "gh (GitHub CLI) is required - $MAIN_BRANCH is rule-protected, so the merge into it can only happen through a PR"

branch="$(git rev-parse --abbrev-ref HEAD)"
[ "$branch" = "$RELEASE_BRANCH" ] \
    || die "on '$branch', releases are cut from '$RELEASE_BRANCH' (git switch $RELEASE_BRANCH)"

if [ -n "$(git status --porcelain)" ]; then
    git status --short >&2
    die "working tree is not clean - commit or stash the above first"
fi

git rev-parse -q --verify "refs/tags/$TAG" >/dev/null \
    && die "tag $TAG already exists locally"

echo "Fetching $REMOTE"
git fetch --quiet --tags "$REMOTE" || die "could not fetch from $REMOTE"

git rev-parse -q --verify "refs/tags/$TAG" >/dev/null \
    && die "tag $TAG already exists on $REMOTE"

for b in "$RELEASE_BRANCH" "$MAIN_BRANCH"; do
    git rev-parse -q --verify "refs/remotes/$REMOTE/$b" >/dev/null \
        || die "$REMOTE has no branch '$b'"
done

[ "$(git rev-parse "$RELEASE_BRANCH")" = "$(git rev-parse "$REMOTE/$RELEASE_BRANCH")" ] \
    || die "$RELEASE_BRANCH differs from $REMOTE/$RELEASE_BRANCH - pull or push first"

grep -q '^## \[Unreleased\]' "$CHANGELOG" \
    || die "$CHANGELOG has no '## [Unreleased]' section to release"

# The body of Unreleased: everything up to the next version heading.
unreleased="$(awk '/^## \[Unreleased\]/ { inside = 1; next } /^## \[/ { inside = 0 } inside' "$CHANGELOG")"
entries="$(echo "$unreleased" | grep -v '^###' | grep -v '^[[:space:]]*$' || true)"

if [ -z "$entries" ] && [ "${ALLOW_EMPTY_CHANGELOG:-}" != "1" ]; then
    die "Unreleased is empty - write the entries first, or set ALLOW_EMPTY_CHANGELOG=1"
fi

echo "Releasing $TAG from $RELEASE_BRANCH ($(git rev-parse --short HEAD))"

# --- changelog -------------------------------------------------------------

# Rename Unreleased to the release and open a fresh Unreleased block above it.
awk -v version="$VERSION" -v date="$DATE" '
    function emit(line, fresh) { lines[++n] = line; scaffold[n] = fresh }

    /^## \[Unreleased\]/ && !replaced {
        emit("## [Unreleased]", 1)
        emit("", 1)
        emit("### Added", 1)
        emit("", 1)
        emit("### Changed", 1)
        emit("", 1)
        emit("### Fixed", 1)
        emit("", 1)
        emit("## [" version "] - " date, 1)
        replaced = 1
        next
    }
    { emit($0, 0) }

    END {
        for (i = 1; i <= n; i++) {
            # An empty "### Fixed" under a shipped version claims a category
            # that had nothing in it. The new Unreleased block is exempt: there
            # the empty headings are the scaffolding to write into.
            if (lines[i] ~ /^### / && !scaffold[i]) {
                empty = 1
                for (j = i + 1; j <= n; j++) {
                    if (lines[j] ~ /^#/) break
                    if (lines[j] !~ /^[[:space:]]*$/) { empty = 0; break }
                }
                if (empty) {
                    # Drop the blank line that followed the heading too.
                    if (lines[i + 1] ~ /^[[:space:]]*$/) i++
                    continue
                }
            }
            print lines[i]
        }
    }
' "$CHANGELOG" > "$CHANGELOG.tmp"

mv "$CHANGELOG.tmp" "$CHANGELOG"
echo "Updated $CHANGELOG: [$VERSION] - $DATE"

# --- commit ------------------------------------------------------------------

git add "$CHANGELOG"
git commit --quiet -m "chore(release): $VERSION"
echo "Committed chore(release): $VERSION"

# The notes come out of the rewritten file, not out of the section read
# earlier: by now the empty subsections are gone, and release notes that
# announce an empty "Fixed" are worse than none.
notes="$(awk -v heading="## [$VERSION] - $DATE" '
    $0 == heading { inside = 1; next }
    /^## \[/ { inside = 0 }
    inside { lines[++n] = $0; if ($0 !~ /^[[:space:]]*$/) { if (!first) first = n; last = n } }
    END { for (i = first; i <= last; i++) print lines[i] }
' "$CHANGELOG")"

git push --quiet "$REMOTE" "$RELEASE_BRANCH" || die "pushing $RELEASE_BRANCH failed"
echo "Pushed $RELEASE_BRANCH to $REMOTE"

# --- merge into main via PR ---------------------------------------------------

# main takes no push, from anyone, by any route other than a merged PR - see
# the ruleset note at the top of this file. Required approvals are 0, so this
# merges immediately; it only blocks if that ever changes upstream.
pr_body="${notes:-Release $VERSION.}"
pr_url="$(gh pr create --base "$MAIN_BRANCH" --head "$RELEASE_BRANCH" \
    --title "chore(release): $VERSION" --body "$pr_body")" \
    || die "opening the PR from $RELEASE_BRANCH into $MAIN_BRANCH failed - $RELEASE_BRANCH is pushed, open it by hand"
echo "Opened $pr_url"

gh pr merge "$pr_url" --merge --delete-branch=false \
    || die "merging $pr_url failed - merge it by hand, then tag $MAIN_BRANCH's new tip as $TAG and push the tag (see docs/releasing.md)"
echo "Merged into $MAIN_BRANCH"

# --- tag main's merge commit --------------------------------------------------

git fetch --quiet "$REMOTE" "$MAIN_BRANCH" || die "fetching $MAIN_BRANCH after the merge failed - $pr_url is merged, tag it by hand"
merge_sha="$(git rev-parse "$REMOTE/$MAIN_BRANCH")"

# The tag message carries the changelog section, which is what
# 'gh release create --notes-from-tag' publishes as the release notes.
# --cleanup=verbatim: the section headings start with '#', which git would
# otherwise strip from the message as comments. Tagging the merge commit
# (rather than the commit just made on develop) is what keeps 'git describe'
# on main reporting the tag itself, not one commit past it - a GitHub PR
# merge always adds a commit, so the tag has to move to match it.
printf '%s\n\n%s\n' "$TAG" "$notes" | git tag -a "$TAG" --cleanup=verbatim -F - "$merge_sha"
echo "Tagged $TAG on $MAIN_BRANCH ($merge_sha)"

git push --quiet "$REMOTE" "$TAG" || die "pushing $TAG failed - the image build starts on this push"
echo "Pushed $TAG to $REMOTE"

# Bring the local mirrors in line with what's now on the remote. main is just
# a mirror the script manages, never a branch worked on directly, so resetting
# it to match is not discarding anything.
git switch --quiet "$MAIN_BRANCH"
git reset --quiet --hard "$REMOTE/$MAIN_BRANCH"
git switch --quiet "$RELEASE_BRANCH"

# --- github release ------------------------------------------------------------

if gh release create "$TAG" --title "$TAG" --notes-from-tag; then
    echo "Created GitHub release $TAG"
else
    # The tag is pushed and the image is building; only the release page is
    # missing, and that is worth saying rather than exiting 0 quietly.
    die "gh release create failed - the tag is pushed, create the release by hand"
fi

echo "Done. The image build is running in GitHub Actions (release.yml)."
