# Accessible Job Manager

A full-stack job application tracking portal built with accessibility as a first-class requirement. Users can manage companies, positions, job applications and documents. Advisors guide users through the process; reviewers can access documents shared with them.

---

## Tech Stack

| Layer      | Technology                                          |
|------------|-----------------------------------------------------|
| Backend    | Spring Boot 3.5.16, Java 26, Spring Security OAuth2 |
| Persistence| Spring Data JPA, PostgreSQL 16                      |
| Document   | docx4j (mail merge), Gotenberg (PDF via LibreOffice)|
| Frontend   | Angular 22 (standalone components)                  |
| Auth       | OIDC via Authentik                                  |
| Storage    | Garage (S3-compatible)                              |
| Proxy      | Traefik v2                                          |
| Build      | Gradle 9 (Groovy DSL), Angular CLI 22               |
| Job import | Ollama (local LLM, structured-output extraction)    |
| i18n       | `@ngx-translate/core` (English, German, Dutch)      |

---

## Roles

| Role       | Description                                                                      |
|------------|----------------------------------------------------------------------------------|
| `USER`     | Job seeker — manages companies, applications, documents, generates cover letters |
| `ADVISOR`  | Career advisor — sees assigned users, creates position suggestions               |
| `REVIEWER` | Document reviewer — sees users who shared documents, can download them           |

Roles are assigned via Authentik `groups` claim. The `GroupsGrantedAuthoritiesMapper` normalises group names to uppercase and maps them to Spring Security `ROLE_*` authorities. Create Authentik groups named exactly `Advisor` and `Reviewer` (any casing works — the mapper normalises them).

---

## Features

### User
- **Home** — profile card (name, email, roles), quick navigation; advisors/reviewers are redirected to their own dashboard automatically
  - **Job posting import** — paste a job posting URL; the backend fetches the page and asks a local Ollama LLM to extract company, position, contact and location fields into a ready-to-review form
  - **Personalized cover letter template** — fill in your name, address and email once to download a `.docx` template pre-filled with your sender details, ready to use as a mail-merge source for future applications
- **Applications** — table of all job applications with status labels; per-row template dropdown, one-click PDF/Word cover letter download, and a "send as email" button that opens a `mailto:` link pre-filled with subject and body extracted from the generated cover letter
- **Companies** — manage companies (per-user ownership) with nested locations (street, city, postcode, country) and positions (contact details, gender, email, website)
- **Documents** — upload cover letter templates (`.docx`) with a custom label; label is editable before upload
- **User Guide** — role-aware walkthrough of the app's features, linked from the account menu
- **Profile** — view own account details (name, email, roles)

### Advisor
- **My Users** — table of all users assigned to this advisor
- **Suggestions** — create position suggestions for a user (company + position + optional message), view all past suggestions with status

### Reviewer
- **Dashboard** — shows all users who have shared documents, grouped as cards with name, email and a document table; one-click download per document

### Cover Letter Generation
- User selects an uploaded `.docx` template and a target application
- Backend fills mail-merge fields: `company`, `street`, `city`, `position`, `contact` (salutation, language-aware), `date`
- Gotenberg (LibreOffice headless) converts the filled `.docx` to PDF
- Three output formats from the same template + application pairing:
  - `POST /api/cover-letter/{applicationId}/fill/{documentId}` — PDF download
  - `POST /api/cover-letter/{applicationId}/fill/{documentId}/word` — `.docx` download
  - `POST /api/cover-letter/{applicationId}/fill/{documentId}/email` — returns `{to, subject, body}` with the cover letter's plain text extracted for use in a `mailto:` link
- `POST /api/cover-letter/personalize` — generates a blank template pre-filled with the caller's own sender header (name, street, postal code, city, email)

### Job Posting Import
- `POST /api/posting/overview?url=...` fetches the given URL's visible text (via Jsoup) and sends it to a local Ollama model (`qwen2.5:3b` by default) with a structured-output prompt to extract job posting fields
- Requires a locally running Ollama instance (`OLLAMA_URL`, default `http://localhost:11434`) — not part of the Docker Compose dev stack, must be started separately

### Legal Pages
- `/impressum` and `/datenschutz` — Impressum (§5 DDG) and GDPR-compliant Datenschutzerklärung, publicly reachable without login (including from the login page footer)
- Both pages are trilingual (en/de/nl) and describe this app's actual data processing (Authentik login data, user-entered application data, uploaded documents, document-sharing grants, session cookie)

### Internationalisation
- Language picker (English/German/Dutch) visible on every page, including the login page, via `LanguageService` + `@ngx-translate/core`
- Initial translation file is loaded via an Angular `APP_INITIALIZER` before first render, avoiding a flash of untranslated i18n keys

### Access Control
- Routes `/companies`, `/applications`, `/documents` are guarded — advisors and reviewers are redirected to `/forbidden`
- Companies are per-user: each user only sees companies they created
- Document access is explicitly granted by the user via `POST /api/documents/{documentId}/access`

---

## Local Development

### Prerequisites

- Java 26
- Node 24+
- Docker + Docker Compose
- A running Authentik instance
- Ollama running locally with a pulled model (only needed for job posting import, e.g. `ollama pull qwen2.5:3b`)

### 1. Start dev infrastructure

```bash
cd dev
docker compose up -d
```

This starts:

| Service      | Port | Description                    |
|--------------|------|--------------------------------|
| PostgreSQL   | 5432 | Main database                  |
| Garage       | 3900 | S3-compatible object storage   |
| Garage Admin | 3901 | Garage admin API               |
| Garage WebUI | 3909 | Storage browser UI             |
| Gotenberg    | 3000 | LibreOffice PDF conversion     |
| Traefik      | 80   | Reverse proxy                  |

### 2. Configure OIDC

Edit `src/main/resources/application.yml` and set your Authentik client credentials, or pass them as environment variables:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          authentik:
            client-id: <your-client-id>
            client-secret: <your-client-secret>
        provider:
          authentik:
            issuer-uri: http://localhost:9000/application/o/<your-app>/
```

The Authentik application must expose a `groups` claim on the OIDC token. Create groups named `Advisor` and `Reviewer`; users in neither group are treated as regular users (`USER`).

### 3. Configure document storage (S3 or Azure)

Set `STORAGE_PROVIDER` to `s3` (default, Garage) or `azure`.

For S3/Garage:

```
S3_ENDPOINT=http://localhost:3900
S3_BUCKET=job-manager
ACCESS_KEY=<garage-access-key>
SECRET_KEY=<garage-secret-key>
```

For Azure Blob Storage:

```
STORAGE_PROVIDER=azure
AZURE_STORAGE_CONNECTION_STRING=<your-connection-string>
```

### 4. Run the backend

```bash
./gradlew bootRun
```

Backend starts on `http://localhost:8060`.

### 5. Run the frontend (dev mode)

```bash
cd AppClient
npm install
npm start
```

Frontend dev server starts on `http://localhost:4200`. The proxy config forwards `/api`, `/login`, `/logout` and `/oauth2` to the backend.

### 6. Build for production (embedded frontend)

```bash
./gradlew bootRun   # triggers npmBuild + copyFrontend automatically
```

The Angular build output is copied into `src/main/resources/static/` and served by Spring Boot at `http://localhost:8060`.

---

## Key API Endpoints

| Method | Path                                              | Role     | Description                                  |
|--------|---------------------------------------------------|----------|----------------------------------------------|
| GET    | `/api/me`                                         | any      | Current user profile + normalised groups     |
| GET    | `/api/login/as/{role}`                            | any      | Trigger OAuth login (role hint in session)   |
| GET    | `/api/applications`                               | USER     | List own job applications                    |
| POST   | `/api/applications`                               | USER     | Create job application                       |
| GET    | `/api/companies`                                  | USER     | List own companies with locations/positions  |
| POST   | `/api/companies`                                  | USER     | Create company (owned by caller)             |
| PUT    | `/api/companies/{id}`                             | USER     | Update own company                           |
| DELETE | `/api/companies/{id}`                             | USER     | Delete own company                           |
| GET    | `/api/documents`                                  | USER     | List own documents (filter by `?type=`)      |
| POST   | `/api/documents/upload`                           | USER     | Upload document with label                   |
| POST   | `/api/documents/{id}/access`                      | USER     | Grant reviewer access to a document          |
| DELETE | `/api/documents/{id}/access/{reviewerId}`         | USER     | Revoke reviewer access                       |
| POST   | `/api/cover-letter/{appId}/fill/{docId}`          | USER     | Generate filled PDF cover letter             |
| POST   | `/api/cover-letter/{appId}/fill/{docId}/word`     | USER     | Generate filled .docx cover letter           |
| POST   | `/api/cover-letter/{appId}/fill/{docId}/email`    | USER     | Get `{to, subject, body}` for a mailto link  |
| POST   | `/api/cover-letter/personalize`                   | USER     | Generate a template with sender header filled|
| POST   | `/api/posting/overview?url=`                      | USER     | Extract job posting fields from a URL (LLM)  |
| GET    | `/api/advisor/my-users`                           | ADVISOR  | Users assigned to this advisor               |
| POST   | `/api/advisor/suggestions`                        | ADVISOR  | Create a position suggestion                 |
| GET    | `/api/advisor/suggestions`                        | ADVISOR  | All suggestions by this advisor              |
| GET    | `/api/reviewer/users`                             | REVIEWER | Users who shared documents with this reviewer|
| GET    | `/api/reviewer/documents/{id}/download`           | REVIEWER | Download a shared document                   |

---

## Database

Connection is configured via environment variables:

```
MANAGER_DB_URL=jdbc:postgresql://localhost:5432/manager
MANAGER_DB_USER=
MANAGER_DB_PASSWORD=
```

Schema is managed by Hibernate (`ddl-auto=update`). For production, switch to Flyway with `ddl-auto=validate`.

---

## Deployment

```bash
cd dev
docker compose up -d --build
```

Builds the full app (including Angular frontend), starts it behind Traefik and serves at `http://login.localhost`. Gotenberg runs as a sidecar for PDF generation.

### Azure Deployment

`dev/azure/main.bicep` provisions a demo deployment to Azure Container Apps. It creates:

| Resource                              | Purpose                                                    |
|----------------------------------------|-------------------------------------------------------------|
| Storage Account + blob container       | Document storage (`STORAGE_PROVIDER=azure`)                |
| Log Analytics workspace                | Required by the Container Apps environment                |
| Container Apps environment             | Hosts the app and Gotenberg                                |
| Azure Database for PostgreSQL Flexible Server | Managed Postgres (first deploy takes ~10-15 min)     |
| Container App: `gotenberg`             | Internal-only, PDF conversion sidecar                      |
| Container App: `app`                   | Public ingress, runs the `appImage` container (defaults to `ghcr.io/martin1088/accessible-job-manager:latest`) |

Deploy with:

```bash
az group create -n ajm-demo -l germanywestcentral
az deployment group create -g ajm-demo -f dev/azure/main.bicep \
    --parameters pgPassword='<STRONG_PW>' \
                 oidcClientId='<CLIENT_ID>' \
                 oidcClientSecret='<CLIENT_SECRET>' \
                 oidcIssuerUri='https://.../application/o/<slug>/' \
                 oidcAuthUri='https://.../application/o/authorize/' \
                 oidcRedirectUri='https://<expected-app-fqdn>/login/oauth2/code/authentik' \
                 groupUser='<entraID_GUID>' \
                 groupAdvisor='<entraID_GUID>' \
                 groupReviewer='<entraID_GUID>'
```

Notes:
- `oidcRedirectUri` depends on the app's FQDN, which is only known after the first deployment (see the `oidcRedirectUri` output). Deploy once with a placeholder, register the real value as the redirect URI in your OIDC provider (Authentik or Entra ID), then redeploy with the real `oidcRedirectUri`.
- `groupUser` / `groupAdvisor` / `groupReviewer` must match whatever the OIDC provider puts in the `groups` claim. For Entra ID cloud-only security groups this is the group's **Object ID** (a GUID), not its display name — pass the GUIDs explicitly.
- Postgres is publicly reachable with an `AllowAzureServices` firewall rule rather than a VNet, since Container Apps on the Consumption plan has no fixed outbound IP; consider private networking for real production data.
- `appMinReplicas` defaults to `0` (scale-to-zero, cheaper but cold-starts on first request).
