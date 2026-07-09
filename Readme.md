# Accessible Job Manager

A full-stack job application tracking portal built with accessibility as a first-class requirement. Users can manage companies, positions, job applications and documents. Advisors guide users through the process; reviewers can access documents shared with them.

---

## Tech Stack

| Layer      | Technology                                          |
|------------|-----------------------------------------------------|
| Backend    | Spring Boot 3.4.4, Java 26, Spring Security OAuth2  |
| Persistence| Spring Data JPA, PostgreSQL 16                      |
| Document   | docx4j (mail merge), Gotenberg (PDF via LibreOffice)|
| Frontend   | Angular 19 (standalone components)                  |
| Auth       | OIDC via Authentik                                  |
| Storage    | Garage (S3-compatible)                              |
| Proxy      | Traefik v2                                          |
| Build      | Gradle 9 (Groovy DSL), Angular CLI 19               |

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
- **Applications** — table of all job applications with status labels; per-row template dropdown + one-click PDF cover letter download
- **Companies** — manage companies (per-user ownership) with nested locations (street, city, postcode, country) and positions (contact details, gender, email, website)
- **Documents** — upload cover letter templates (`.docx`) with a custom label; label is editable before upload

### Advisor
- **My Users** — table of all users assigned to this advisor
- **Suggestions** — create position suggestions for a user (company + position + optional message), view all past suggestions with status

### Reviewer
- **Dashboard** — shows all users who have shared documents, grouped as cards with name, email and a document table; one-click download per document

### Cover Letter Generation
- User selects an uploaded `.docx` template and a target application
- Backend fills mail-merge fields: `company`, `street`, `city`, `position`, `contact` (salutation), `date`
- Gotenberg (LibreOffice headless) converts the filled `.docx` to PDF
- Endpoints: `POST /api/cover-letter/{applicationId}/fill/{documentId}` (PDF) and `.../word` (.docx)

### Access Control
- Routes `/companies`, `/applications`, `/documents` are guarded — advisors and reviewers are redirected to `/forbidden`
- Companies are per-user: each user only sees companies they created
- Document access is explicitly granted by the user via `POST /api/documents/{documentId}/access`

---

## Local Development

### Prerequisites

- Java 26 (or 21+)
- Node 20+
- Docker + Docker Compose
- A running Authentik instance

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

### 3. Configure S3 (Garage)

Set the following environment variables (or edit `application.yml`):

```
S3_ENDPOINT=http://localhost:3900
S3_BUCKET=job-manager
ACCESS_KEY=<garage-access-key>
SECRET_KEY=<garage-secret-key>
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
