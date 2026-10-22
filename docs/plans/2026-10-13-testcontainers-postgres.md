# Plan: Testcontainers-Postgres für die JPA-Schematests

Stand: develop, 13.10.2026 – umgesetzt, Befunde eingetragen.

## Ziel

`ApplicationLifecycleTest` und `CompanyDeletionSchemaTest` laufen gegen ein echtes PostgreSQL 16 statt gegen H2. Alle anderen Tests bleiben auf H2. Unterschiede zwischen H2 und Postgres, die dabei sichtbar werden, werden festgehalten – als Befund im Plan und als Geschichte fürs Gespräch.

## Ausgangslage (gelesen auf develop)

- `build.gradle`: Spring Boot 4.1.1, Java 26. Die Boot-BOM ist per `implementation platform(...)` eingebunden; `testImplementation` erbt das. Testdatenbank ist `testRuntimeOnly 'com.h2database:h2'`, `org.postgresql:postgresql` ist bereits `runtimeOnly` vorhanden.
- `src/test/resources/application.yml`: H2 in-memory, `ddl-auto: create-drop`, ausdrücklich gesetzt `driver-class-name: org.h2.Driver` und `database-platform: org.hibernate.dialect.H2Dialect`. Die Datei dient auch `bootTestRun`.
- Beide Tests sind `@DataJpaTest` (`org.springframework.boot.data.jpa.test.autoconfigure`).
  - `ApplicationLifecycleTest` importiert zusätzlich `JpaAuditingConfig` und `OidcAuditorAware`, läuft mit `Propagation.NOT_SUPPORTED`, schreibt also wirklich und räumt in `@AfterEach` mit `deleteAll()` auf.
  - `CompanyDeletionSchemaTest` läuft in der normalen Test-Transaktion mit Rollback und prüft den Fremdschlüssel zwischen Dokument und `CompanyPosition`.
- Kein Flyway/Liquibase. In Produktion: `ddl-auto: update` (`src/main/resources/application.yml`).
- Weitere `@DataJpaTest`s, die auf H2 bleiben: `SuggestionAuditingTest`, `CompanyAuditingTest`, `AuditingListenerGuardTest`, `ApplicationAuditingTest`, `HtmlLetterTemplatePersistenceTest`, `CompanyPositionAuditingTest`, `DefaultDocumentSchemaTest`.

## Entscheidungen

- Container als Spring-Bean in einer gemeinsamen `@TestConfiguration` mit `@ServiceConnection`, nicht als JUnit-Extension: Der Container lebt so lange wie der gecachte Kontext.
- Image `postgres:16-alpine`, konfiguriert als `testing.postgres.image` in der Test-`application.yml` (Override per `POSTGRES_TEST_IMAGE`). **Korrektur:** Produktion ist *nicht* auf 16 festgelegt. `deploy/helm/accessible-job-manager/values.yaml` setzt `postgres.imageName: ""`, also das Default-Image des CNPG-Operators. Auf 16 laufen nur die Compose-Stacks (`dev/`, `deploy/docker-compose.yml`). Der Test bildet also die Compose-Version ab; siehe offene Punkte.
- Meta-Annotation `@PostgresDataJpaTest`, damit niemand `replace = NONE` vergisst.
- Ohne Docker **scheitern** die Tests, statt sich zu überspringen. Das ist bewusst anders als bei `DevServices` (Gotenberg/Ollama): Ein stiller Lauf auf H2 wäre genau die Lücke, die diese Tests schließen sollen.

## Umsetzung

1. `build.gradle`: `spring-boot-testcontainers` und `org.testcontainers:testcontainers-postgresql`. Aus der BOM kommen **Testcontainers 2.0.5**, kein Konflikt mit dem jaxb-Force.
2. `src/test/java/de/ajm/manager/testing/PostgresTestcontainersConfiguration.java`: Bean `PostgreSQLContainer` (`org.testcontainers.postgresql`), typisierter Rückgabetyp für `@ServiceConnection`.
3. `src/test/java/de/ajm/manager/testing/PostgresDataJpaTest.java`: `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` (`org.springframework.boot.jdbc.test.autoconfigure`) + `@Import(PostgresTestcontainersConfiguration.class)`.
4. Test-`application.yml`: H2-Treiber- und Dialektzeile entfernt. Die Autodetektion greift; die übrigen JPA-Tests laufen weiter auf H2.
5. Beide Tests auf `@PostgresDataJpaTest` umgestellt; der Javadoc-Satz zur H2-Konfiguration in `CompanyDeletionSchemaTest` ist angepasst.

## Nachweis

- `./gradlew test --tests '*ApplicationLifecycleTest' --tests '*CompanyDeletionSchemaTest' --info`:
  ```
  tc.postgres:16-alpine : Container postgres:16-alpine started in PT1.89S
  Database dialect: PostgreSQLDialect
  Database version: 16.15
  ```
  Erwartungsgemäß zwei Container (zwei Kontexte, weil `ApplicationLifecycleTest` zusätzlich importiert), je ca. 2 s Startzeit.
- Gegenprobe ohne Docker: Den Socket in einem eigenen Mount-Namespace zu verdecken (`unshare -m` + `mount --bind /dev/null /var/run/docker.sock`) führt zu `IllegalStateException: Could not find a valid Docker environment` beim Erzeugen der Bean `postgresContainer`, ohne Rückfall auf H2.
  Achtung: `DOCKER_HOST=…` allein reicht für diese Probe nicht. Testcontainers merkt sich in `~/.testcontainers.properties` die zuletzt erfolgreiche Strategie (`UnixSocketClientProviderStrategy`) und findet den Socket trotzdem.
- Gesamtsuite: 517 Tests, 1 übersprungen, 1 rot: `CoverLetterPdfUaTest`. Dieser Test scheitert auch ohne diese Änderung (gegen die laufende Dev-Gotenberg) und hat mit diesem Plan nichts zu tun.
- CI (`build.yml`, `ubuntu-latest`) und der Devcontainer (`docker-in-docker`) haben Docker; keine Änderung nötig.

## Befunde

Methode: eine temporäre Probe-Klasse (danach gelöscht) mit identischem Ablauf einmal unter `@DataJpaTest` (H2) und einmal unter `@PostgresDataJpaTest`, mit `org.hibernate.SQL=DEBUG` und `org.hibernate.orm.jdbc.bind=TRACE`.

### 1. Abgebrochene Transaktion – bestätigt, mit Pointe

**Ursache:** Postgres markiert die Transaktion nach dem FK-Fehler als abgebrochen.
**Beleg (Postgres):** Die nächste Abfrage in derselben Test-Transaktion:
```
PSQLException sqlState=25P02 :: ERROR: current transaction is aborted, commands ignored until end of transaction block
```
**Pointe:** Auf JPA-Ebene läuft die Folgeabfrage auch auf H2 nicht. Das gescheiterte `delete from company_positions` steht noch in Hibernates Action-Queue, und der Auto-Flush vor der nächsten Abfrage führt es erneut aus. H2 meldet dann wieder `23503`, Postgres `25P02`. „H2 macht weiter“ stimmt nur auf JDBC-Ebene.
**Konsequenz:** Nach einem Flush-Fehler ist die Session in beiden Fällen unbrauchbar. Wer in `CompanyDeletionSchemaTest` nach dem `assertThatThrownBy` noch etwas prüfen will, braucht eine neue Transaktion. Der Test tut das heute nicht und ist deshalb auf Postgres grün.

### 2. Exception-Typ – gleich auf beiden, Text und Groß-/Kleinschreibung verschieden

| | H2 | Postgres |
|---|---|---|
| über `entityManager.flush()` | `org.hibernate.exception.ConstraintViolationException` (`kind=FOREIGN_KEY`) | gleich |
| über `repository.flush()` | `DataIntegrityViolationException` → Hibernate-CVE | gleich |
| SQLState | `23503` | `23503` |
| Vendor-Errorcode | `23503` | `0` |
| Constraint-Name | `FKJJ0GDXNH8LMFBRWUHOGKXH4FK` | `fkjj0gdxnh8lmfbrwuhogkxh4fk` |

**Konsequenz:** Die Spring-Übersetzung zu `DataIntegrityViolationException` greift nur über das Repository-Proxy, nicht über den geteilten `EntityManager`. `GlobalExceptionHandler` behandelt keine der beiden Klassen, deshalb kam der Fehler als 500 an. Eine künftige Behandlung sollte auf SQLState oder `ConstraintKind` schauen. Vendor-Errorcode, Constraint-Name als String oder Meldungstext unterscheiden sich je Datenbank.

### 3. ID-Erzeugung – Hypothese widerlegt

`Application`, `Company` und `CompanyPosition` nutzen `GenerationType.IDENTITY`. Beide Datenbanken erzeugen `id bigint generated by default as identity`; das INSERT läuft beim `save()`. Der Kommentar (2) in `ApplicationLifecycleTest` stimmt.
`Document` nutzt `GenerationType.UUID` und bekommt auf beiden Datenbanken eine `uuid`-Spalte.

### 4. Sequenzen und Rollback – bestätigt, folgenlos

Nach dem Rollback des ersten Tests bekommt der zweite auf beiden Datenbanken `company=2`; Identity-Werte werden nicht zurückgegeben. Keiner der JPA-Tests erwartet konkrete IDs. Die Treffer für `isEqualTo(1L)` & Co. stehen ausschließlich in Mockito-Tests.

### 5. Enums – größter Unterschied

H2 bekommt einen nativen Enum-Typ mit alphabetisch sortierten Werten, Postgres `varchar(255)` mit Check-Constraint in Deklarationsreihenfolge:
```
H2:  status enum ('ACCEPTED','DRAFT',…,'WITHDRAWN') not null
PG:  status varchar(255) not null check ((status in ('DRAFT','SENT',…,'WITHDRAWN')))
```
Das gilt für `Application.status`, `Document.type`/`language` und die drei Enums an `CompanyPosition`.
**Konsequenz (abgeleitet, nicht gemessen):** Produktion läuft mit `ddl-auto: update`. Hibernates Update legt fehlende Spalten an, ändert aber keine bestehenden Check-Constraints. Ein neu hinzugefügter Enum-Wert würde in Produktion dann mit `23514` (Check-Verletzung) abgewiesen, während H2-Tests und frische Testcontainers-Schemata grün bleiben. Das spricht für Flyway. Vor einer Aussage im Gespräch gezielt nachprüfen: Schema mit altem Enum anlegen, Wert ergänzen, `update` laufen lassen.

### 6. `@Lob` außerhalb der Transaktion – nicht zutreffend

Die beteiligten Entities haben keine `@Lob`-Felder.

### 7. Reservierte Namen – kein Befund

Die Tabellen heißen im Plural (`applications`, `companies`, `company_positions`, `documents`). Die Spalten `type` und `language` sind in Postgres nicht reserviert. Das DDL läuft auf PostgreSQL 16 ohne Warnung durch.

## Gesprächsvorbereitung: „Warum nicht nur H2?“

**Kurzfassung (30 Sekunden):** Produktion läuft auf PostgreSQL. H2 ist eine andere Datenbank mit Kompatibilitätsschicht. Für Tests, die genau das Verhalten der Datenbank prüfen – Fremdschlüssel, Transaktionen, Schema –, will ich die echte Datenbank. Für alles andere bleibt H2, weil es schnell ist und kein Docker braucht.

**Die Geschichte dahinter:** Ein Fremdschlüsselfehler beim Löschen einer Firma kam als 500 beim Nutzer an. Die Suite war grün, weil alle Tests das Repository gemockt hatten. `CompanyDeletionSchemaTest` war der erste Test gegen ein echtes Schema. Der nächste Schritt war, dieses Schema auch von der echten Datenbank prüfen zu lassen. Dabei zeigte sich: Hibernate erzeugt für Enums auf H2 einen nativen Enum-Typ, auf Postgres eine Textspalte mit Check-Constraint. H2 prüft also ein Schema, das es in Produktion gar nicht gibt. Und nach einem Constraint-Fehler bricht Postgres die ganze Transaktion ab (`25P02`), H2 nicht.

**Abwägung:**

- Kosten: Docker nötig (lokal, Devcontainer, CI), ca. 2 s Containerstart pro Kontext.
- Deshalb nur dort, wo die Datenbank selbst getestet wird – nicht pauschal.
- `@ServiceConnection` ersetzt das manuelle Durchreichen von URL und Zugangsdaten.

**Mögliche Nachfragen:**

- „Warum nicht H2 im PostgreSQL-Modus?“ – Der Modus übernimmt Syntax, aber nicht Transaktionssemantik, Typsystem und Fehlercodes. Das Enum-DDL hängt am Dialekt, nicht am Modus.
- „Und Schema-Migrationen?“ – Heute `ddl-auto: update` in Produktion; Befund 5 zeigt, wo das bricht. Mit Flyway liefen dieselben Migrationen in Test und Produktion, und Testcontainers ist dafür die Voraussetzung.
- „Warum nicht alle Tests auf Postgres?“ – Kosten-Nutzen: Service- und Controller-Tests prüfen Logik, nicht die Datenbank.

## Offene Punkte

- Welche PostgreSQL-Version liefert der CNPG-Operator in Produktion? Empfehlung: `postgres.imageName` im Chart pinnen (z. B. `ghcr.io/cloudnative-pg/postgresql:16`), damit Test, Compose und Cluster dieselbe Major-Version haben.
- Befund 5 (Check-Constraint unter `ddl-auto: update`) gezielt nachprüfen.
- `GlobalExceptionHandler`: FK-Verletzungen (SQLState `23503`) auf eine 409 abbilden – eigenes Ticket.
- Bei mehr Postgres-Tests die `@Import`s vereinheitlichen, damit sich die Tests Kontext und Container teilen.

## Checkliste

- [x] Testcontainers-Postgres-Modul und `spring-boot-testcontainers` in `build.gradle`
- [x] `PostgresTestcontainersConfiguration` und `@PostgresDataJpaTest` anlegen
- [x] H2-Treiber- und Dialektzeile aus der Test-`application.yml` entfernen, gesamte Suite grün (bis auf das vorbestehende `CoverLetterPdfUaTest`)
- [x] Beide Tests auf `@PostgresDataJpaTest` umstellen
- [x] Nachweis, dass Postgres läuft, und Gegenprobe ohne Docker
- [x] H2/Postgres-Vergleich, Befunde eintragen
- [ ] Laut üben: „Warum nicht nur H2?“
