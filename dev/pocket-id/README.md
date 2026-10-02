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

## Switching user

Logging out of the app also ends the Pocket ID session (the app calls Pocket
ID's `end-session`), so after a logout Pocket ID asks you to sign in again.
**Do not answer that with a passkey** unless the passkey belongs to the user you
want next.

On Windows, passkeys live in **Windows Hello**, not in the browser - Chrome,
Edge and Firefox all share one store. If Windows Hello holds a single passkey
for `localhost` (typically your own account), every passkey sign-in in every
browser lands on that account. Switching browsers or using a private window does
not change that, and if that account is only in `User`, "Log in as Advisor"
cannot work.

To switch to another user, sign in with a login link instead:

1. Log out of the app.
2. Create a link for the next user:
   ```bash
   docker exec pocket-id /app/pocket-id one-time-access-token test-advisor
   ```
3. Open the printed `http://localhost:1411/lc/…` link. You are now signed in to
   Pocket ID as that user.
4. Open `http://localhost:8060` and log in. Pocket ID already has a session and
   does not ask for a passkey; if the Windows Hello prompt appears anyway,
   cancel it.

The link sets that browser's own Pocket ID session, so different browsers (or
browser profiles) can be signed in as different users side by side.

**Faster switching:** while signed in as a test user, add a passkey for it at
`http://localhost:1411/settings/account`. Once Windows Hello holds one passkey
per test user, it shows a picker at every sign-in and links are no longer
needed.

A user meant to act in more than one role (e.g. your own account as Advisor) can
simply be added to more groups in Pocket ID's admin UI; the app then grants all
of those roles.

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
