# AJM on Scaleway: pilot platform

A guide to running the Accessible Job Manager (AJM) as a pilot on Scaleway:
what gets created, in which order, and which errors came up along the way.
Everything is described as OpenTofu configuration (`deploy/scaleway/bootstrap/` and `deploy/scaleway/pilot/`).

## 1. Overview

| Building block | Scaleway service | Purpose |
|---|---|---|
| App | Serverless Container | Spring Boot backend with the Angular UI, port 8060 |
| Sign-in | Serverless Container (Pocket ID) | Passkey sign-in via OIDC, domain `id.<base>` |
| PDF generation | Serverless Container (Gotenberg), **private** | HTML/URL → PDF, reachable only with an IAM token |
| Database | Managed Database for PostgreSQL (`DB-DEV-S`), Private Network only | Data of the app and of Pocket ID |
| Documents | Object Storage, bucket `ajm-pilot-documents` | PDFs and templates, encrypted with SSE-C |
| Language model | Generative APIs (OpenAI-compatible) | Reading job postings, CV matching |
| Email | Transactional Email (TEM) | SMTP for Pocket ID |
| Images | Container Registry, namespace `ajm-pilot` | App, Gotenberg and Pocket ID images |
| State | Object Storage, bucket `ajm-pilot-tofu-state` | OpenTofu state, encrypted |

Domains: `<base>` (the application; with `app_subdomain = "app"` it is `app.<base>` instead) and `id.<base>` (sign-in).
The DNS provider is independent of Scaleway (Hetzner here); the records are set by hand.

## 2. Prerequisites

- OpenTofu ≥ 1.11, Docker, `jq`, `curl`, optionally the Scaleway CLI `scw`.
- A Scaleway account with organisation rights, and your own domain with access to its DNS.
- Two API key pairs that must **not** be mixed up:
  - `SCW_ACCESS_KEY` / `SCW_SECRET_KEY` for the provider.
  - `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` for the state backend (the same key works, but the
    backend part reads only the `AWS_*` variables).
- **The key needs `default-project-id` = project `ajm-pilot`.** Object Storage assigns requests to the key's
  default project. Without it, access to the state bucket returns `403`.
- Never put secrets in files, only in `TF_VAR_...` or environment variables. Watch for **trailing spaces** when
  copying (a secret with 37 instead of 36 characters caused `SignatureDoesNotMatch`).

## 3. Procedure in phases

### Phase 0: bootstrap (once, `deploy/scaleway/bootstrap/`)

Creates the project `ajm-pilot` and the state bucket. Because of the chicken-and-egg problem, this folder uses
local state (`terraform.tfstate` lives in the folder; do not check it in).

```sh
cd deploy/scaleway/bootstrap   # from the repository root
export SCW_ACCESS_KEY=... SCW_SECRET_KEY=... SCW_DEFAULT_ORGANIZATION_ID=...
tofu init && tofu apply
```

Then create an API key with default project `ajm-pilot` (see Prerequisites).

### Phase 1: registry, Pocket ID, email

1. Create `deploy/scaleway/pilot/pilot.tfvars` from `pilot.tfvars.example` (`project_id`, `operator_principal`, `base_domain`, image tags).
   The file is in `.gitignore`.
2. Run `tofu init` in `deploy/scaleway/pilot/` (with the `AWS_*` variables).
3. Create the registry namespace. Scaleway rejects names that are too short (HTTP 400), hence `ajm-pilot`.
4. Mirror the images into your own registry:

   ```sh
   printf '%s' "$SCW_SECRET_KEY" | docker login rg.fr-par.scw.cloud/ajm-pilot -u nologin --password-stdin

   docker pull --platform linux/amd64 gotenberg/gotenberg:8
   docker tag  gotenberg/gotenberg:8 rg.fr-par.scw.cloud/ajm-pilot/gotenberg:8
   docker push rg.fr-par.scw.cloud/ajm-pilot/gotenberg:8
   # likewise for pocket-id:v1
   ```

5. Create Pocket ID, the database, IAM and TEM. Transactional Email must be **activated in the console
   beforehand** (otherwise `403 No active offer subscription`).
6. Add the DNS records for email sending (`tofu output dns_email`): SPF, DKIM, DMARC. Then press "Check DNS" in
   the TEM console until everything is green.

### Phase 1b: domain for Pocket ID

1. `tofu output dns_id` shows the CNAME. Add it (target **with a trailing dot**).
2. Check with `dig +short id.<base> CNAME @8.8.8.8`.
3. Set `enable_id_domain = true` in `pilot.tfvars`, plan, apply.
4. Open `https://id.<base>`, create the administrator account with a passkey, and configure SMTP in Pocket ID
   (values from `tofu output smtp` and `tofu output -raw smtp_password`).

### Phase 2: OIDC client and app

1. Create an OIDC client in Pocket ID. Callback URL: `https://<app-address>/login/oauth2/code/authentik` (`https://<base>/…`, with a subdomain `https://app.<base>/…`).
2. Create the groups `User`, `Advisor`, `Reviewer`, add yourself to `User`, and allow the groups on the client.
   Without a group the app rejects the login.
3. In `pilot.tfvars`: `oidc_client_id`, `app_image` (with a fixed tag), `app_min_scale = 1` for testing.
   Set the secret with `export TF_VAR_oidc_client_secret='...'`.
4. Provide the app image. CI builds it to GHCR. The script `deploy/scaleway/push-image.sh` pulls it from
   there, retags it, pushes it into your own registry, writes `app_image` into `pilot.tfvars` and shows the
   plan (run `docker login` once beforehand, see Phase 1):

   ```sh
   deploy/scaleway/push-image.sh sha-<commit>            # look at the plan
   deploy/scaleway/push-image.sh sha-<commit> --apply    # and apply directly
   ```

   By hand the steps are:

   ```sh
   SRC=ghcr.io/martin1088/accessible-job-manager:sha-<commit>
   IMG=rg.fr-par.scw.cloud/ajm-pilot/accessible-job-manager-pilot:sha-<commit>
   docker pull --platform linux/amd64 $SRC && docker tag $SRC $IMG && docker push $IMG
   ```

   The image must contain the backend version that knows the `openai` provider and the Gotenberg token.
5. `tofu plan -var-file=pilot.tfvars -out=plan.tfplan`, check the plan (**no `destroy`, no `replace`** on
   bucket, database or Pocket ID), then `tofu apply plan.tfplan`. The script already runs the plan;
   use `--apply` only after reading it.

### Phase 3: domain for the app

1. `tofu output dns_app` shows the record. On the base domain (`app_subdomain = ""`) it is an **ALIAS**, on a subdomain a CNAME. In both cases the target has a trailing dot. A CNAME on the base domain is not allowed in DNS, and an ALIAS works only with providers that support it. Scaleway DNS does, and binding the container works with it (verified with `barrierefrei-bewerben.eu`; Hetzner not verified).
2. Set `enable_app_domain = true`, plan, apply. Scaleway issues the certificate, which takes a few minutes.
3. Open the app address and sign in. On the first call after a DNS change the local resolver may still remember the earlier failure (`Could not resolve host`); then flush the cache or use another network.

### Changing the domain

Changing the base domain (`base_domain`) affects sign-in, not just the address. The procedure that worked for `barrierefrei-bewerben.eu`:

1. Change `base_domain` in `pilot.tfvars` and set `enable_id_domain` and `enable_app_domain` to `false`. If the domain is already in Transactional Email (`scaleway-sdk-go: http error 409 Conflict: Domain already exists`), import it first: `scw tem domain list region=fr-par`, then `tofu import -var-file=pilot.tfvars scaleway_tem_domain.main fr-par/<id>`. If the DNS zone is at Scaleway, `autoconfig = true` writes SPF, DKIM, DMARC and MX itself.
2. Read the plan: no database, no bucket replaced.
3. In the DNS zone, add the records from `tofu output dns_id` (CNAME `id`) and `dns_app` (ALIAS on the base domain, or CNAME `app` with `app_subdomain = "app"`). **The target ends with a dot**, otherwise the console appends your own domain (`…scw.cloud.barrierefrei-bewerben.eu.`). Check with `dig +short id.<base> CNAME @8.8.8.8`.
4. Set `enable_id_domain = true`, apply.
5. **Passkeys are valid only for the domain they were created under.** All Pocket ID accounts are unusable after the change. Recreate Pocket ID's data: with the Managed Database, remove the contents of the `pocketid` database (connection options below), restart Pocket ID (`scw container container deploy <id> region=fr-par`), and create the admin account again at `https://id.<base>`. The user `pocketid` owns its tables, so drop them one by one with `DROP TABLE … CASCADE`.
6. In Pocket ID: SMTP (sender under the domain, TLS option **StartTLS** on port 587), groups `User`, `Advisor`, `Reviewer`, OIDC client with the new callback URL.
7. Set `oidc_client_id` and `TF_VAR_oidc_client_secret`, set `enable_app_domain = true`, apply. With `app_subdomain = ""` the app is on the base domain; the OIDC client's callback URL must match the app's address, otherwise the login fails at the redirect URI.

The app's data stays, but it is tied to the old Pocket ID user identifiers. New accounts do not see it.

## 4. How the app is configured

Everything the app needs is set by `deploy/scaleway/pilot/containers.tf`. The main points:

- **Language model:** `JOB_POSTING_LLM_PROVIDER=openai`, model `mistral-small-3.2-24b-instruct-2506`
  (`llm_model`). The app's key serves as `OPENAI_API_KEY`.
  **`OPENAI_BASE_URL` contains the project ID:** `https://api.scaleway.ai/<project-id>/v1`. Without it,
  Scaleway answers `403 insufficient permissions to access the resource`, even with a valid key.
  A different model can be set for CV matching: `cv_matching_model` in `pilot.tfvars`
  (becomes `CV_MATCHING_OPENAI_MODEL`). Empty means: same as `llm_model`. Reading postings stays on the
  smaller model. The variable must not reach the app empty, because an empty value counts as set and defeats
  the fallback, so it is only passed when it has a value.
- **Gotenberg:** private. The app sends the secret key of its own IAM application as `X-Auth-Token`
  (`GOTENBERG_AUTH_TOKEN`). Permissions: policy `gotenberg_caller`.
- **Documents:** S3 with path style, SSE-C (`S3_SSE_C_MODE=sse-c`, key `S3_SSE_C_KEY`).
- **Database:** one Managed Database for PostgreSQL (`DB-DEV-S`, PostgreSQL 16) with two databases (`ajm`,
  `pocketid`) and one user each, who sees only their own database. It has no public endpoint: the app and
  Pocket ID reach it through the Private Network (`network.tf`, `private_network_id`). The passwords are
  generated by `random_password` and live in the encrypted state. Backups are switched off
  (`disable_backup = true`). It runs continuously, so there is no cold start.
- **Sign-in:** `OIDC_ISSUER_URI=https://id.<base>`, groups `User`/`Advisor`/`Reviewer`.
- **Timeouts:** app container `timeout = 300` (CV matching may wait up to 300 s for the model),
  Gotenberg `--api-timeout=120s` and container `timeout = 180`, app → Gotenberg
  `GOTENBERG_READ_TIMEOUT_SECONDS=150` (longer than Gotenberg, so its error text arrives).
- **Gotenberg scaling:** `scaling_option { concurrent_requests_threshold = 1 }`, `max_scale = 3`. An import
  starts several renderings at once. The threshold is only a hint for scaling up, not a hard limit: if two
  requests arrive at a cold container at the same time, both land on the first instance and share its vCPU
  (`context deadline exceeded`, HTTP 503). Hence the longer timeouts. A cold start costs about 15 s extra.
  `gotenberg_min_scale = 2` keeps two instances warm (costs continuous running).
- **Diagnostics:** `DIAGNOSTICS_LOG_LEVEL=INFO` writes one line `DIAG|host|category|status|…` to stdout per import.

### Imprint and privacy policy

Without its own texts, the pilot shows the texts from the image. They do not name the operator and describe
Authentik and Garage instead of Pocket ID and Scaleway. The pilot therefore publishes its own:

- The texts are **not** in the public repository but in a private one (working title `ajm-pilot-legal`):
  `impressum.<language>.yaml` and `datenschutz.<language>.yaml`, at least `de`. The template with
  placeholders is in `deploy/scaleway/legal.example/`. It is not a finished legal text; a professional should review the texts.
- In `pilot.tfvars`: `legal_dir = "../../../../ajm-pilot-legal"` (path to the checkout). Then `tofu apply`.
  OpenTofu creates the bucket `ajm-pilot-legal` (read-only for the app), uploads the files and sets
  `LEGAL_DOCUMENTS_S3_BUCKET` on the app.
- A text change works the same way: change the YAML, `tofu apply`. The app picks it up within 30 s at most, without
  a restart.
- The image must contain the S3 access for legal texts (newer than the version of 31.10.2026).

Error patterns:

| Symptom | Cause |
|---|---|
| `tofu plan`: `legal_dir must contain at least one impressum.<lang>.yaml and one datenschutz.<lang>.yaml.` | A document is missing from the folder |
| `tofu plan`: `Every file in legal_dir must be named <slug>.<lang>.yaml, e.g. impressum.de.yaml.` | File name does not match |
| App container exits at startup with `The legal documents in job-manager.legal.s3-bucket are invalid` | A YAML file is faulty; the message names the file |
| Log `The legal documents in … changed but could not be loaded. The previous documents are still being served.` | A changed file is faulty; the old version stays online |

Open: whether `scaleway_object` with `file` stores the content in the state has not been checked. The state bucket is
encrypted and private.

## 5. Reading logs

Container logs are in Scaleway Cockpit (Loki). In the console, open the container's **Logs** →
**Open Grafana logs dashboard**. If data is missing, press **Trigger synchronization** there. Grafana → **Explore**,
data source `Scaleway Logs – fr-par`, code mode:

```
# Errors of the app
{resource_type="serverless_container", resource_name=~".*-app"} |~ "ERROR|Exception|DIAG\\|"

# Requests from the app to Gotenberg (status, duration)
{resource_type="serverless_container", resource_name=~".*gotenberg"} |= "request handled" != "curl"
```

Choose a narrow time range (for example the last 15 minutes) and look right after the attempt.

## 6. Known pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| `tofu init`: `No valid credential sources found` | `AWS_*` variables are missing | Set `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` |
| `403 Forbidden` on `HeadObject` for the state bucket | Key has a different default project | Create a new key with `default-project-id` |
| `SignatureDoesNotMatch` | Trailing space in the secret | Set the secret without the space |
| `docker push`: `denied` | Namespace missing or name too short | Create the namespace `ajm-pilot` |
| TEM: `403 No active offer subscription` | Service not activated | Activate Transactional Email in the console |
| Duplicate domain in the TEM console | The wizard created a second one | Delete the domain with the differing ID |
| Hetzner: `TXT records must be fully escaped` | TXT values must be quoted, in groups of 255 characters | Split and quote the value |
| Hetzner: CNAME is appended to the zone | Target without a trailing dot | Enter the target with a trailing dot |
| DKIM invalid at Hetzner | The special editor drops `k=rsa` | Create it through the general TXT form with correctly split groups |
| Pocket ID does not start, DB URL is read as a file path | The floating tag `v1` takes SQLite when `DB_PROVIDER` is missing | Set `DB_PROVIDER=postgres` |
| SMTP test in Pocket ID fails | TLS option does not match the port | 465 with TLS, 587 with StartTLS; renew the password after a key swap |
| Browser: `DNS_PROBE_FINISHED_NXDOMAIN`, but `dig @8.8.8.8` resolves | Router/resolver remembered the earlier refusal | Flush the DNS cache, use a public DNS server, or wait |
| App: "The server could not be reached. It may be restarting, or your connection dropped" | Status 0 in the browser: the app did not answer (connection cut, container timeout) | Check the app container's `timeout`, read the app log |
| App: "The job posting page could not be converted to PDF" | Gotenberg answers with an HTTP error (503 `context deadline exceeded`) | Read the Gotenberg log, see timeouts and `gotenberg_min_scale` above |
| App container: `Container crashed with exit code 1`, `java.net.SocketTimeoutException: Read timed out` at startup | OIDC discovery against Pocket ID hangs (Pocket ID is starting cold or failing) | Read the Pocket ID log; if needed `app_min_scale = 1` |
| App: "Job posting extraction model returned an error (403)" | `OPENAI_BASE_URL` without the project ID | Use the address with the project ID in the path |
| Upload: `AccessDenied` on the documents bucket | The app's key has no default project | Set `default_project_id` on `scaleway_iam_api_key.app` |
| `tofu output urls` fails after `-target` runs | `-target` does not refresh unrelated outputs | Apply once in full |

## 7. Backing up secrets

These values belong in a password manager. Without them the pilot cannot be restored:

- State passphrase (`state_passphrase`).
- SSE-C key (`sse_c_key`). **Without it, uploaded documents can no longer be read.**
- Pocket ID encryption key (`pocketid_encryption_key`).
- API keys (`SCW_*`, `AWS_*`) and the OIDC client secret.

The documents bucket has a policy that admits only the app and `operator_principal`. Without that entry,
the operator locks themselves out. `prevent_destroy` prevents accidental deletion.

## 8. Operation and costs

- `app_min_scale = 0` shuts the app down when nobody uses it. The first call afterwards needs a cold
  start. Use `1` for tests.
- Gotenberg runs with 1 vCPU and 2 GiB, but only while requests are running (`gotenberg_min_scale = 0`).
- The Managed Database (`DB-DEV-S`, 10 GB) runs continuously at a fixed price (roughly €12–20 a month; check the
  current price in the console). The Private Network is free.
- Matching and reading cost tokens at the Generative APIs. The model can be chosen per task.
- Always look at `tofu plan -out=plan.tfplan` and check it before `apply` runs.
  `destroy`/`replace` on bucket, database or Pocket ID is a warning sign.

### Connecting to the database

The database has only a private address. For one-off work (`psql`, schema fixes, account deletion) use the
Scaleway console, or temporarily add a `load_balancer {}` block to `scaleway_rdb_instance.main` with an ACL for your own IP and
remove it again afterwards. The admin user is `admin` (password: `random_password.db_admin`, in the encrypted
state). The users `ajm` and `pocketid` have the passwords `db_ajm` and `db_pocketid`.

### Deletion and retention

What remains for a while after deletion (as of Scaleway's documentation; the privacy policy states the same periods):

| Where | Retention after deletion |
|---|---|
| Managed Database for PostgreSQL | No backup (`disable_backup = true`): deleted data is gone immediately, and there is no recovery after a mistake either. |
| Object Storage (`ajm-pilot-documents`) | The bucket is versioned. The lifecycle rule `expire-deleted-versions` in `storage.tf` deletes non-current versions after 7 days. Without it a deleted file would remain as an old version indefinitely. |
| Cockpit logs | 7 days by default, adjustable. |

The documents are encrypted with SSE-C. Without the key, even Scaleway cannot read old versions.

Still to clarify with Scaleway (support request): are there other backups or replicas that remain after deletion?
When is data finally removed after deleting a bucket or a database?

### Deleting a user's account (on request)

The app has no "delete account" function. Users can delete companies, applications, documents and shares themselves, but the account itself remains. The privacy policy therefore says: "To have your account deleted, write to us." The law requires an answer within one month (Art. 12(3) GDPR). Proceed as follows:

1. **Verify the person.** The request must come from the account's email address. When in doubt, ask.
2. **Find the identifier.** The user identifier (`userId`, the OIDC subject) is the user's ID in Pocket ID. You find it in the Pocket ID interface under *Users* for the account in question, or in the database: `SELECT user_id, email FROM user_profiles WHERE email = '<address>';`
3. **Preferred: the person deletes their own content.** They delete their companies, applications and documents in the app. That also cleans up object storage, because `DocumentService` removes the object on deletion. Afterwards only steps 5 to 7 remain.
4. **Otherwise: remove the content yourself.** The person's data is in these tables (column `user_id` in each, unless noted): `companies` (with locations, positions and contacts), `applications`, `application_documents` (via the application), `documents`, `default_documents`, `html_letter_template`, `relationship` (`applicant_id`, `counterpart_id`, with `share`), `suggestions` (`advisor_id`, `user_profile_id`), `user_profile_roles` and `user_profiles`. The order is dictated by foreign keys, and not all tables delete by database cascade (companies cascade only through JPA). **The SQL has not been tested against the live database.** Connect with `psql` as the user `ajm` to the database `ajm` (see "Connecting to the database" above). Work in a transaction, look at the foreign keys with `\d <table>`, and run `ROLLBACK` first before you set `COMMIT`.
5. **Object storage.** In `ajm-pilot-documents`, delete all objects under the prefix `<userId>/`. The keys have the form `<userId>/<type>/<uuid>.<extension>`. The lifecycle rule removes old versions after 7 days. The documents are encrypted with SSE-C; deleting does not need the key.
6. **Pocket ID.** In the interface under *Users*, delete the account.
7. **Confirm.** Reply to the person, state the period of 7 days (old object versions) and record that and when the deletion happened. Note only the date, not a name.

If such requests come up more often, a "delete account" function in the app is worthwhile. It would handle steps 3 to 5 in one go.

## 9. Open points

- CV matching has not yet been tested live with a CV.
- The "location" field is often not found when reading postings (quality of model and prompt).
- The Oracle HCM posting has shown no success so far; the cause is not conclusively clarified.
- Scaleway's answer on backups and deletion periods (see "Deletion and retention"); afterwards review the privacy policy.
- Look at `tofu plan` after the new lifecycle rule: an `update in-place` on the bucket `documents` is expected, no `replace`.
