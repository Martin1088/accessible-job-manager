# accessible-job-manager – Status Audit

**Audit date:** _______________
**Branch reviewed:** _______________
**Estimated completion after audit:** _____ %

-----

## 1. Project Metadata

### Build & Versions

- [X] **Gradle version:** Gradle 9.4.1 (`./gradlew --version`)
  - Current stable: Gradle 8.x – ideally 8.10+
  - Red flag: < 8.0, or `build.gradle` (Groovy) instead of `build.gradle.kts`
- [X] **Build script type:** Groovy (`build.gradle`) / Kotlin DSL (`build.gradle.kts`)
  - Recommended for new projects: Kotlin DSL (type-safe, better IDE support, IDE-agnostic)
- [X] **Java version:** 21 (in `build.gradle.kts` under `java { toolchain { ... } }`)
  - Expected: Java 21 LTS (Spring Boot 3.5+ supports 17, 21, 23; 21 is the market standard)
- [X] **Spring Boot version:** 3.4.4
  - Current stable: 3.5.x (as of May 2026)
- [X] **Spring Framework version (transitive):** _______________
  - Expected: 6.2+ with Spring Boot 3.5

### Plugins

- [ ] `org.springframework.boot` – version?
- [ ] `io.spring.dependency-management` – enabled?
- [ ] `org.gradle.toolchains.foojay-resolver-convention` for automatic JDK provisioning?
- [ ] Code quality: `checkstyle`, `spotless`, `detekt`? (not required, but signals maturity)

### Dependency Hygiene

- [ ] `./gradlew dependencies | grep -i deprecated` – outdated libraries?
- [ ] `./gradlew dependencyUpdates` (plugin: `com.github.ben-manes.versions`) – if missing, add later
- [ ] Versions explicitly pinned or managed via BOM? (BOM = Spring Boot recommended)

-----

## 2. Architecture Overview

- [ ] Package structure documented? (`tree -L 3 src/main/java`)
  - Red flag: everything in a single package; “controller / service / repository / entity” layering without domain boundaries
  - Better: feature-based or clear hexagonal/clean-architecture structure
- [ ] Number of classes: _____
- [ ] Production LOC (`find src/main -name "*.java" | xargs wc -l`): _____
- [ ] Test LOC (`find src/test -name "*.java" | xargs wc -l`): _____
- [ ] Test-to-production ratio – critical if < 0.5

### Architecture Diagram

- [ ] PlantUML diagram available? → required for README later
- [ ] ERD available? → required before JPA refactoring

-----

## 3. Web / REST Layer

**Status:** open / partial / done

### Audit Questions

- [ ] How many controllers? Which endpoints? (`grep -r "@RequestMapping\|@GetMapping\|@PostMapping" src/main`)
- [ ] DTOs cleanly separated from entities? → red flag: entities returned directly from controller
- [ ] Bean Validation active (`@Valid`, `@NotNull`, `@Email`)?
- [ ] Centralized exception handling via `@ControllerAdvice`?
- [ ] Using `ProblemDetail` (RFC 7807) or a custom error DTO?
  - Red flag: hand-rolled `ErrorResponse` class instead of Spring 6 `ProblemDetail`
- [ ] OpenAPI/Swagger integrated? (`springdoc-openapi-starter-webmvc-ui`)
- [ ] Pagination via `Pageable`?
- [ ] HTTP status codes correct? (201 for Create, 204 for Delete, 200 for Update, 404 with ProblemDetail)

### Red Flags (must modernize)

- [ ] `RestTemplate` for outbound calls (→ `RestClient` since Spring 6.1)
- [ ] `ResponseEntity<Object>` instead of typed responses
- [ ] Missing validation on POST/PUT
- [ ] No global exception handling

**Curriculum mapping:** Week 2

-----

## 4. JPA / Persistence

**Status:** open / partial / done

### Audit Questions

- [ ] Which entities? (`grep -r "@Entity" src/main`)
- [ ] Relationships cleanly modeled? (`@OneToMany`, `@ManyToOne`, `@ManyToMany`)
- [ ] **FetchType** on ToMany: explicitly `LAZY`? → red flag: `EAGER`
- [ ] **equals/hashCode** on entities: ID-based? → red flag: causes Set/HashMap bugs (see Vlad Mihalcea)
- [ ] **N+1 test:** with `spring.jpa.show-sql=true` and Hibernate Statistics against a list endpoint – how many statements fire?
- [ ] Cascade behavior documented? What happens on delete?
- [ ] Custom queries: JPQL, Specifications, or native SQL?
- [ ] Database: H2 (dev), Postgres (prod)?
- [ ] Migrations: Flyway or Liquibase? → none = red flag for production
- [ ] `@Transactional` applied intentionally, or “sprayed everywhere”?
- [ ] Read-only transactions marked (`@Transactional(readOnly = true)`)?

### Red Flags

- [ ] Lombok `@Data` on entities (breaks equals/hashCode/toString → cyclic-reference crash with `@ManyToOne`)
- [ ] Connection pool: HikariCP default without tuning for production?
- [ ] `spring.jpa.hibernate.ddl-auto=create`/`update` in production (must be `validate` or `none`)

**Curriculum mapping:** Weeks 3–4 (HIGHEST PRIORITY for interviews)

-----

## 5. Security

**Status:** open / partial / done

### Audit Questions

- [ ] Is Spring Security included at all? (`spring-boot-starter-security`)
- [ ] Configuration as a `SecurityFilterChain` bean (lambda DSL)?
- [ ] Red flag: `WebSecurityConfigurerAdapter` extension – deprecated since Spring Security 5.7, removed since 6.0
- [ ] Authentication mechanism: Basic Auth, Form Login, JWT, OAuth2 Resource Server?
- [ ] Password hashing: BCrypt, Argon2? No plaintext storage?
- [ ] CSRF strategy: enabled (form-based), disabled (stateless API)?
- [ ] CORS configured, if a separate frontend is planned?
- [ ] Method security: `@PreAuthorize` used?
- [ ] Authorities: role-based or permission-based?

### Red Flags

- [ ] `.permitAll()` on endpoints that should be protected
- [ ] CSRF disabled without justification in code/docs
- [ ] Hardcoded secrets in `application.yaml`

**Curriculum mapping:** Weeks 5–6
**Bonus:** Your Authentik / secret-sync experience applies directly here – on OAuth2 Resource Server you have a real-world edge.

-----

## 6. Testing

**Status:** open / partial / done

### Audit Questions

- [ ] Test framework: JUnit 5? AssertJ?
- [ ] Test coverage measured? (Jacoco plugin – `./gradlew test jacocoTestReport`)
- [ ] Coverage value: _____ % (for a CV demo, ideally > 70%)
- [ ] Slice tests used? `@WebMvcTest`, `@DataJpaTest`?
- [ ] Integration tests via Testcontainers (Postgres), or still H2?
  - Red flag: all tests against H2 – produces false negatives for Postgres-specific SQL
- [ ] `@SpringBootTest` only where necessary? (slow, should be the minority)
- [ ] Security tests present? (`@WithMockUser`, JWT mocking?)
- [ ] Mocking framework: Mockito or MockK?

### Red Flags

- [ ] No tests at all = fix immediately, this is a hiring killer
- [ ] Only happy paths tested, no error cases
- [ ] Tests that contain `Thread.sleep()`

**Curriculum mapping:** Week 7 (highest interview ROI – do not skip)

-----

## 7. CI / CD & Build

**Status:** open / partial / done

### Audit Questions

- [ ] GitHub Actions or GitLab CI configured?
- [ ] Pipeline steps: build, test, lint, security scan?
- [ ] Docker image build: via `bootBuildImage` (Buildpacks) or a custom `Dockerfile`?
- [ ] Multi-stage Dockerfile (small, secure)?
- [ ] Image scanning (Trivy, Snyk)?
- [ ] Dependency vulnerability check in CI?

### Red Flags

- [ ] No CI on a public GitHub repo (visible to recruiters!)
- [ ] Docker image tagged `latest` only, no version tag

**Curriculum mapping:** Week 8 (observability) + cross-cutting

-----

## 8. Observability

**Status:** open / partial / done

### Audit Questions

- [ ] Spring Boot Actuator included?
- [ ] Endpoints exposed: `health`, `info`, `prometheus`, `metrics`?
- [ ] Liveness / Readiness probes correctly mapped?
- [ ] Logging: structured JSON for prod, plain for dev?
- [ ] Micrometer + Prometheus endpoint exposed?
- [ ] Tracing: OpenTelemetry prepared (nice to have)?

**Curriculum mapping:** Week 8

-----

## 9. Documentation & Recruiter Optics

This is the part that gets neglected in a learning lab but is **decisive for a CV asset**. Recruiters and tech leads spend about 30 seconds here – either it convinces them immediately or the repo is closed.

### Audit Questions

- [ ] README.md present and meaningful?
  - [ ] Project title + tagline (one sentence: what and for whom)
  - [ ] Motivation: why does this exist (e.g. accessible job tracking for screen reader users)
  - [ ] Tech stack clearly listed
  - [ ] Local setup in 5 commands
  - [ ] Architecture diagram (PlantUML / Mermaid) embedded
  - [ ] Screenshots or demo GIF? (especially valuable given the screen reader use case)
- [ ] LICENSE file?
- [ ] CONTRIBUTING.md (if open source)?
- [ ] API documentation generated (Swagger UI screenshot in README)?
- [ ] Badges: build, coverage, Java version, Spring Boot version
- [ ] Clean commit history? (Squash merges, meaningful messages – not “wip wip fix” spam)

### Red Flags

- [ ] README is still the Spring Initializr default
- [ ] Last commit > 6 months ago
- [ ] Branch chaos (lots of open feature branches)

-----

## 10. Curriculum Mapping Overview

For each area, fill in what is already done and what remains as curriculum work:

|Week|Topic                     |Project status |Action         |
|----|--------------------------|---------------|---------------|
|1   |Spring Core & Internals   |_______________|_______________|
|2   |Web / REST                |_______________|_______________|
|3   |JPA fundamentals          |_______________|_______________|
|4   |Spring Data & transactions|_______________|_______________|
|5   |Spring Security basics    |_______________|_______________|
|6   |OAuth2 Resource Server    |_______________|_______________|
|7   |Testing                   |_______________|_______________|
|8   |Observability & production|_______________|_______________|

-----

## 11. Prioritization for Remaining Runway

With 5–7 weeks until end of contract:

### Immediate (week 1, in parallel with ongoing applications)

1. -----
1. -----
1. -----

### Mid-term (weeks 2–4)

1. -----
1. -----

### Nice to have (if time permits)

1. -----
1. -----

### Application Triggers

- [ ] Repo published on GitHub (even unfinished, with a clear “WIP, see roadmap” section)
- [ ] CV entry “Spring Boot 3.5, JPA, Security, Tests” with a link to the repo
- [ ] LinkedIn update with project pinned

-----

## 12. Modernization Quick Wins

If the audit reveals that much of this is still on older versions, do these three things **before everything else** – otherwise you will be learning outdated patterns:

1. **Bump Spring Boot to 3.5.x:** `id("org.springframework.boot") version "3.5.x"` – run `./gradlew build`, work through compile errors
1. **Java 21:** `java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }`
1. **Modernize Spring Security DSL:** replace `WebSecurityConfigurerAdapter` with `@Bean SecurityFilterChain`

Only then move on to feature work.

-----

## 13. Next Concrete Step

After completing the audit (about 1–2 hours of work):

1. Commit this file into the repo (e.g. `docs/STATUS.md`) → externally visible signal of structured work
1. Create top-3 priorities as GitHub Issues → public roadmap
1. Extend the README with a “Roadmap” section linking to those issues → recruiters see structured engineering work, not just code dumps