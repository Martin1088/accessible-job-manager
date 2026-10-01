# Pocket ID (local OIDC)

A lighter alternative to the Authentik stack for testing login and the
User / Advisor / Reviewer role mapping.

```bash
cd dev && docker compose -f pocket-id.yml up -d
```

Pocket ID stores everything in SQLite under `dev/pocket-id/data` (git-ignored).
It has no equivalent of Authentik's blueprints, and its first admin has to
register a passkey in a browser, so the objects the Authentik blueprint creates
are set up by hand once. They survive restarts; delete `data/` to start over.

## One-time setup

1. Open <http://localhost:1411/setup>, create the admin account and register a
   passkey. `http://localhost` counts as a secure context, so WebAuthn works
   without TLS.
2. **Groups** - create `User`, `Advisor` and `Reviewer`. The group *name* is
   what lands in the `groups` claim and must match `GROUP_USER`,
   `GROUP_ADVISOR`, `GROUP_REVIEWER` (matching is case-insensitive); the friendly
   name is display only.
3. **OIDC client** - create `access-job-manager`:
   - Callback URL: `http://localhost:8060/login/oauth2/code/authentik`
     (the path is the Spring registration id, which stays `authentik`)
   - Logout callback URL: `http://localhost:8060/`
   - Leave *Public client* off - the app authenticates with a client secret.
   Copy the client ID and secret.
4. **Users** - create test users and add them to one group each. They sign in
   with a one-time login link from the admin UI, then register their own
   passkey. A user in none of the three groups is rejected by design.
5. Point the app at Pocket ID in `dev/.env`:

   ```properties
   OIDC_CLIENT_ID=<client id>
   OIDC_CLIENT_SECRET=<client secret>
   OIDC_ISSUER_URI=http://localhost:1411
   OIDC_AUTH_URI=http://localhost:1411/authorize
   OIDC_SCOPE=openid, email, profile, groups
   ```

   `groups` in the scope is required: Pocket ID only emits the `groups` claim
   when that scope is requested, and without it every login holds no role.

Check the provider is up with
`curl -s http://localhost:1411/.well-known/openid-configuration`.
