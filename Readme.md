# Accessible Job Manager

A full-stack job application tracking portal built with accessibility as a first-class requirement. Users can manage companies, positions, job applications and documents. Advisors guide users through the process; reviewers provide oversight.

---

## Tech Stack

| Layer      | Technology                                        |
|------------|---------------------------------------------------|
| Backend    | Spring Boot 3.4.4, Java 21, Spring Security OAuth2 |
| Persistence| Spring Data JPA, PostgreSQL 16                    |
| Document   | docx4j (cover letter generation)                  |
| Frontend   | Angular 19 (standalone components)                |
| Auth       | OIDC via Authentik                                |
| Storage    | Garage (S3-compatible)                            |
| Proxy      | Traefik v2                                        |
| Build      | Gradle 9 (Groovy DSL), Angular CLI 19             |

---

## Roles

| Role       | Description                                                              |
|------------|--------------------------------------------------------------------------|
| `USER`     | Job seeker — manages companies, applications, documents                  |
| `ADVISOR`  | Career advisor — sees assigned users, creates position suggestions       |
| `REVIEWER` | Reviewer — oversight dashboard                                           |

Roles are assigned via Authentik `groups` claim and mapped to Spring Security authorities by `GroupsGrantedAuthoritiesMapper`.

---

## Features

### User
- **Home** — profile card (name, email, assigned roles), quick navigation
- **Applications** — table of all job applications with status labels
- **Companies** — manage companies with nested locations and positions
- **Documents** — upload and list cover letter templates (`.docx`)

### Advisor
- **My Users** — table of all users assigned to this advisor
- **Suggestions** — create position suggestions for a user (company + position + message), view all past suggestions

### Reviewer
- **Dashboard** — reviewer overview (in progress)

---

## Project Structure

```
accessible-job-manager/
├── src/main/java/de/samply/manager/
│   ├── controller/          REST controllers (Api, Advisor, Reviewer, Document, …)
│   ├── dto/                 Request/response DTOs (SuggestionDto, CompanyDto, …)
│   ├── model/               JPA entities (Company, Application, Document, Suggestion, …)
│   ├── repository/          Spring Data repositories
│   ├── security/            SecurityConfig, GroupsGrantedAuthoritiesMapper, RoleCheckSuccessHandler
│   └── services/            Business logic (CompanyService, CoverLetterService, …)
├── src/main/resources/
│   └── application.properties
├── AppClient/               Angular 19 frontend
│   └── src/app/
│       ├── advisor/         Advisor views
│       ├── reviewer/        Reviewer views
│       ├── user/            User views (home, applications, companies, documents)
│       ├── shared/          Reusable components (DataTableComponent)
│       ├── core/            AuthService, route guards
│       ├── model/           TypeScript interfaces
│       └── login/           Login page
├── dev/
│   ├── docker-compose.yml   Development infrastructure
│   └── garage/garage.toml   Garage S3 config
├── docker-compose.yml       Production stack (Traefik + app)
└── Dockerfile
```

---

## Local Development

### Prerequisites

- Java 21
- Node 20+
- Docker + Docker Compose
- A running Authentik instance (see below)

### 1. Start dev infrastructure

```bash
cd dev
docker compose up -d
```

This starts:

| Service      | Port | Description                  |
|--------------|------|------------------------------|
| PostgreSQL   | 5432 | Main database                |
| Garage       | 3900 | S3-compatible object storage |
| Garage Admin | 3901 | Garage admin API             |
| Garage WebUI | 3909 | Storage browser UI           |
| Traefik      | 80   | Reverse proxy                |

### 2. Configure OIDC

Edit `src/main/resources/application.properties` and set your Authentik client credentials:

```properties
spring.security.oauth2.client.registration.authentik.client-id=<your-client-id>
spring.security.oauth2.client.registration.authentik.client-secret=<your-client-secret>
spring.security.oauth2.client.provider.authentik.issuer-uri=http://localhost:9000/application/o/<your-app>/
```

The Authentik application must expose a `groups` claim on the OIDC token so that role-based access works. Create groups named `ADVISOR` and `REVIEWER`; users in neither group are treated as regular users.

### 3. Run the backend

```bash
./gradlew bootRun
```

Backend starts on `http://localhost:8060`.

### 4. Run the frontend

```bash
cd AppClient
npm install
npm start
```

Frontend dev server starts on `http://localhost:4200`. The proxy config forwards `/api`, `/login`, `/logout` and `/oauth2` to the backend.

### 5. Build for production (embedded frontend)

```bash
cd AppClient
npm run build
```

The Angular build output is copied into `src/main/resources/static/` and served by the Spring Boot app.

---

## Key API Endpoints

| Method | Path                        | Role     | Description                            |
|--------|-----------------------------|----------|----------------------------------------|
| GET    | `/api/me`                   | any      | Current user profile + groups          |
| GET    | `/api/applications`         | USER     | List job applications                  |
| GET    | `/api/companies`            | USER     | List companies with locations/positions|
| POST   | `/api/companies`            | USER     | Create company                         |
| GET    | `/api/documents`            | USER     | List documents (filter by `?type=`)    |
| GET    | `/api/advisor/my-users`     | ADVISOR  | Users assigned to this advisor         |
| POST   | `/api/advisor/suggestions`  | ADVISOR  | Create a position suggestion           |
| GET    | `/api/advisor/suggestions`  | ADVISOR  | All suggestions created by this advisor|

---

## Accessibility

Accessibility is a core requirement, not an afterthought:

- **Skip link** — "Skip to main content" visible on keyboard focus
- **Landmark regions** — `<header>`, `<main id="main-content">`, `<nav aria-label="…">`
- **Active page** — `aria-current="page"` on the active nav link (via Angular `routerLinkActive`)
- **Role-aware navigation** — separate `<nav>` blocks per role, hidden when not applicable
- **Data tables** — `<caption>`, `scope="col"` on all `<th>`, three-state sort with `aria-sort`, `role="status"` on empty state
- **Forms** — `aria-required`, `aria-describedby`, `<label>` for every input
- **Alerts** — `role="alert"` on error messages for live-region announcement
- **Definition lists** — `<dl>`/`<dt>`/`<dd>` for key-value profile data

---

## Deployment

```bash
docker compose up -d --build
```

The production compose file builds the app (including frontend), starts the Spring Boot container behind Traefik, and serves it at `http://login.localhost`.

---

## Database

The database URL, username and password are read from environment variables with local defaults:

```properties
spring.datasource.url=${MANAGER_DB_URL:jdbc:postgresql://localhost:5432/manager}
spring.datasource.username=${MANAGER_DB_USER:exporter}
spring.datasource.password=${MANAGER_DB_PASSWORD:exporter}
```

Schema is managed by Hibernate (`ddl-auto=update`). For production, switch to Flyway with `ddl-auto=validate`.
