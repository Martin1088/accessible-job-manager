# Pocket ID (local OIDC)

A lighter alternative to the Authentik stack for testing login and the
User / Advisor / Reviewer role mapping.

```bash
cd dev && docker compose -f pocket-id.yml up -d
```

Pocket ID stores everything in SQLite under `dev/pocket-id/data` (git-ignored);
delete that folder to start over.

## What is preset

`pocket-id-seed` runs `seed.sh` after Pocket ID is healthy - the counterpart of
Authentik's blueprint. It talks to the admin API with `STATIC_API_KEY` and is
safe to re-run (`docker compose -f pocket-id.yml up pocket-id-seed`).

| Username        | Group      | Lands on    |
|-----------------|------------|-------------|
| `admin`         | -          | Pocket ID admin UI only (holds no app role) |
| `test-user`     | `User`     | `/`         |
| `test-advisor`  | `Advisor`  | `/advisor`  |
| `test-reviewer` | `Reviewer` | `/reviewer` |

The OIDC client `access-job-manager` gets the **same client ID and secret as the
Authentik provider** (the `OIDC_CLIENT_ID` / `OIDC_CLIENT_SECRET` defaults in
`authentik.yml`), callback `http://localhost:8060/login/oauth2/code/authentik`
and logout callback `http://localhost:8060/`.

## Logging in

Pocket ID has no passwords - only passkeys and one-time login links. Create a
link for any preset user (valid one hour, usable once):

```bash
docker exec pocket-id /app/pocket-id one-time-access-token test-advisor
```

Start the login from the app (`http://localhost:8060`) and open the link when
Pocket ID asks you to sign in - or open the link first, then log in to the app.
While signed in you can add a passkey for that user under *My Account*; your
browser or OS keeps one passkey per test user, so later logins need no link.
`http://localhost` counts as a secure context, so passkeys work without TLS.

Use one browser profile or private window per test user to keep several roles
open side by side.

## Pointing the app at Pocket ID

Client ID and secret are unchanged; in `dev/.env` set:

```properties
OIDC_ISSUER_URI=http://localhost:1411
OIDC_AUTH_URI=http://localhost:1411/authorize
OIDC_SCOPE=openid, email, profile, groups
```

`groups` in the scope is required: Pocket ID only emits the `groups` claim when
that scope is requested, and without it every login holds no role and is
rejected with `/login?error=wrong_role`.
