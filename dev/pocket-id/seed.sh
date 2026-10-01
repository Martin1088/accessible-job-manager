#!/bin/sh
# Pocket ID's counterpart to the Authentik blueprint: creates the three role
# groups, the OIDC client and one test user per role through the admin API,
# authenticated by STATIC_API_KEY. Idempotent - runs on every `up`, creates
# what is missing and re-applies the client settings.
set -eu

API="${POCKET_ID_URL:-http://pocket-id:1411}/api"
KEY="${STATIC_API_KEY:?STATIC_API_KEY is required}"
CLIENT_ID="${OIDC_CLIENT_ID:?OIDC_CLIENT_ID is required}"
CLIENT_SECRET="${OIDC_CLIENT_SECRET:?OIDC_CLIENT_SECRET is required}"
REDIRECT_URI="${OIDC_REDIRECT_URI:-http://localhost:8060/login/oauth2/code/authentik}"
LOGOUT_URI="${OIDC_POST_LOGOUT_REDIRECT_URI:-http://localhost:8060/}"

api() {
  method=$1 path=$2
  shift 2
  curl -fsS -X "$method" -H "X-API-Key: $KEY" -H 'Content-Type: application/json' "$@" "$API$path"
}

LIMIT='pagination%5Blimit%5D=100'

group_id() {
  api GET "/user-groups?$LIMIT" | jq -r --arg n "$1" '.data[] | select(.name == $n) | .id'
}

ensure_group() {
  id=$(group_id "$1")
  if [ -z "$id" ]; then
    id=$(api POST /user-groups -d "$(jq -n --arg n "$1" '{name: $n, friendlyName: $n}')" | jq -r .id)
    echo "group $1 created" >&2
  fi
  echo "$id"
}

# ensure_user <username> <first name> <admin true|false> [group id]
ensure_user() {
  id=$(api GET "/users?$LIMIT" | jq -r --arg u "$1" '.data[] | select(.username == $u) | .id')
  if [ -z "$id" ]; then
    id=$(api POST /users -d "$(jq -n --arg u "$1" --arg f "$2" --argjson a "$3" \
      '{username: $u, email: ($u + "@example.com"), emailVerified: true,
        firstName: $f, lastName: "Test", displayName: ($f + " Test"), isAdmin: $a}')" | jq -r .id)
    echo "user $1 created" >&2
  fi
  if [ -n "${4:-}" ]; then
    api PUT "/users/$id/user-groups" -d "$(jq -n --arg g "$4" '{userGroupIds: [$g]}')" >/dev/null
  fi
}

G_USER=$(ensure_group "${GROUP_USER:-User}")
G_ADVISOR=$(ensure_group "${GROUP_ADVISOR:-Advisor}")
G_REVIEWER=$(ensure_group "${GROUP_REVIEWER:-Reviewer}")

CLIENT=$(jq -n --arg id "$CLIENT_ID" --arg cb "$REDIRECT_URI" --arg lo "$LOGOUT_URI" \
  '{id: $id, name: "access-job-manager", callbackURLs: [$cb], logoutCallbackURLs: [$lo],
    isPublic: false, pkceEnabled: false, skipConsent: false}')
if api GET "/oidc/clients/$CLIENT_ID" >/dev/null 2>&1; then
  api PUT "/oidc/clients/$CLIENT_ID" -d "$CLIENT" >/dev/null
else
  api POST /oidc/clients -d "$CLIENT" >/dev/null
  echo "client $CLIENT_ID created" >&2
fi

# Pocket ID stores only a hash of a secret, so whether the existing one still
# matches OIDC_CLIENT_SECRET cannot be checked - replace it on every run.
for s in $(api GET "/oidc/clients/$CLIENT_ID/secrets" | jq -r '(.data? // .)[] | .id'); do
  api DELETE "/oidc/clients/$CLIENT_ID/secrets/$s" >/dev/null
done
api POST "/oidc/clients/$CLIENT_ID/secrets" -d "$(jq -n --arg s "$CLIENT_SECRET" '{secret: $s}')" >/dev/null

ensure_user admin Admin true
ensure_user test-user User false "$G_USER"
ensure_user test-advisor Advisor false "$G_ADVISOR"
ensure_user test-reviewer Reviewer false "$G_REVIEWER"

echo "Pocket ID seeded. Login link for a user:"
echo "  docker exec pocket-id /app/pocket-id one-time-access-token test-user"
