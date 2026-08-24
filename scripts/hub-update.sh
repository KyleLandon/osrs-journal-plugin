#!/usr/bin/env bash
# Update OSRS Journal on the RuneLite Plugin Hub.
#
# Official flow (https://github.com/runelite/plugin-hub#updating-a-plugin):
#   1. Push the plugin commit to GitHub (Hub builds that exact hash).
#   2. On a plugin-hub fork: checkout -B osrs-journal upstream/master
#   3. Set commit=<40-char hash> in plugins/osrs-journal
#   4. Commit "update osrs-journal" and force-push that branch (not master).
#   5. Open one PR against runelite/plugin-hub; reuse it for CI fixes.
#
# Usage (from the plugin repo):
#   ./scripts/hub-update.sh           # prepare the hub branch
#   ./scripts/hub-update.sh --pr      # also open/reuse the PR
set -euo pipefail

PLUGIN_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HUB_DIR="${PLUGIN_HUB_DIR:-$(cd "$PLUGIN_ROOT/.." && pwd)/plugin-hub}"
HUB_UPSTREAM="https://github.com/runelite/plugin-hub.git"
HUB_FORK="git@github.com:KyleLandon/plugin-hub.git"
PLUGIN_INTERNAL_NAME="osrs-journal"
OPEN_PR=0

if [[ "${1:-}" == "--pr" ]]; then
	OPEN_PR=1
fi

cd "$PLUGIN_ROOT"

if [[ -n "$(git status --porcelain)" ]]; then
	echo "Plugin repo has uncommitted changes. Commit and push them first." >&2
	echo "Hub CI clones GitHub — local-only commits never ship." >&2
	git status -sb >&2
	exit 1
fi

git fetch origin
BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$BRANCH" != "main" && "$BRANCH" != "master" ]]; then
	echo "Checkout main/master before a Hub update (current: $BRANCH)." >&2
	exit 1
fi

LOCAL="$(git rev-parse HEAD)"
REMOTE="$(git rev-parse "origin/$BRANCH")"
if [[ "$LOCAL" != "$REMOTE" ]]; then
	echo "HEAD is not on origin/$BRANCH. Push the plugin repo first:" >&2
	echo "  git push origin $BRANCH" >&2
	exit 1
fi

COMMIT="$(git rev-parse HEAD)"
if [[ ${#COMMIT} -ne 40 ]]; then
	echo "Expected a 40-character commit hash, got: $COMMIT" >&2
	exit 1
fi

echo "==> Plugin commit for Hub: $COMMIT"

if [[ ! -d "$HUB_DIR/.git" ]]; then
	echo "==> Cloning plugin-hub fork into $HUB_DIR"
	git clone "$HUB_FORK" "$HUB_DIR"
fi

cd "$HUB_DIR"
git remote get-url origin >/dev/null
if ! git remote get-url upstream >/dev/null 2>&1; then
	git remote add upstream "$HUB_UPSTREAM"
fi
git fetch upstream
git checkout -B "$PLUGIN_INTERNAL_NAME" upstream/master

MANIFEST="plugins/$PLUGIN_INTERNAL_NAME"
if [[ ! -f "$MANIFEST" ]]; then
	echo "Missing $MANIFEST — is this the right plugin-hub clone?" >&2
	exit 1
fi

# Only bump commit=. Leave repository= and warning= alone.
tmp="$(mktemp)"
awk -v hash="$COMMIT" '
	BEGIN { updated = 0 }
	/^commit=/ { print "commit=" hash; updated = 1; next }
	{ print }
	END { if (!updated) exit 2 }
' "$MANIFEST" > "$tmp"
mv "$tmp" "$MANIFEST"

echo "==> Manifest:"
cat "$MANIFEST"

if git diff --quiet -- "$MANIFEST"; then
	echo "Hub already points at $COMMIT — nothing to update."
	exit 0
fi

git add "$MANIFEST"
git commit -m "update ${PLUGIN_INTERNAL_NAME}"
# Official Hub docs use -f on this plugin-named branch (never on master).
git push -f -u origin "$PLUGIN_INTERNAL_NAME"

echo "==> Pushed $PLUGIN_INTERNAL_NAME @ $COMMIT"
echo "    After CI is green, wait for a maintainer / auto-review bot."
echo "    Fix CI on THIS PR by pushing another commit= bump — do not open a second PR."

if [[ "$OPEN_PR" -eq 1 ]]; then
	existing="$(gh pr list --repo runelite/plugin-hub --head "KyleLandon:${PLUGIN_INTERNAL_NAME}" --state open --json number,url --jq '.[0].url' || true)"
	if [[ -n "$existing" ]]; then
		echo "==> Existing PR: $existing"
	else
		gh pr create --repo runelite/plugin-hub \
			--head "KyleLandon:${PLUGIN_INTERNAL_NAME}" \
			--base master \
			--title "Update OSRS Journal" \
			--body "$(cat <<EOF
Update OSRS Journal to \`${COMMIT}\`.

Plugin source: https://github.com/KyleLandon/osrs-journal-plugin
EOF
)"
	fi
fi
