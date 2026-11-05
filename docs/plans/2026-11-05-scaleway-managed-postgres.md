# Plan: Managed PostgreSQL statt Serverless SQL im Scaleway-Pilot

Stand: 05.11.2026, nur Plan. Es ist noch nichts umgesetzt.

## Ausgangslage

Laut Rechnung kostet die Datenbank fast das gesamte Geld:

| Posten | Betrag |
|---|---|
| Serverless SQL Database, CPU | €45,81 |
| Serverless SQL Database, Storage | €0,21 |
| Serverless Containers, CPU + Memory abzüglich Free Tier | ca. €1,30 |
| Object Storage, Bandbreite | ≈ €0 |

Der Grund steht in `deploy/scaleway/pilot/database.tf`. Beide Serverless-SQL-Datenbanken (`ajm`,
`pocketid`) laufen mit `min_cpu = 1`, also rund um die Uhr. Das war eine bewusste Entscheidung: Mit
`min_cpu = 0` dauerte der Kaltstart über eine Minute (SQLSTATE 57P03). Pocket ID lieferte dann 500, und die
App stürzte beim OIDC-Discovery ab.

Hochgerechnet: 2 DBs × 730 h × €0,13572/vCPU-h ≈ **€195 pro Monat**, auch wenn niemand die App benutzt.
Die App ist dabei nicht schuld. Hikari steht schon auf `minimum-idle: 0`, und `@Scheduled`-Jobs gibt es
keine.

## Ziel

- Eine **Managed Database for PostgreSQL** (`DB-DEV-S`) mit zwei Datenbanken, `ajm` und `pocketid`.
- Sie läuft immer, kostet aber einen festen kleinen Betrag. Einen Kaltstart gibt es nicht, damit ist auch
  das 57P03-Problem erledigt.
- Erreichbar nur über ein **Private Network**. Die DB bekommt keinen öffentlichen Endpunkt.
- **Keine Backups** (`disable_backup = true`).
- **Neuanfang ohne Datenmigration.** Die Serverless-DBs werden danach gelöscht.

Erwartete Kosten: `DB-DEV-S` plus ein kleines Volume, nach meinem Stand rund €12–20 pro Monat. Das Private
Network kostet nichts. **Den aktuellen Preis vor dem Umsetzen in der Konsole bestätigen** (Managed
Databases → Create → DB-DEV-S, Region fr-par).

## Entscheidungen (mit dem Nutzer abgestimmt)

| Frage | Entscheidung | Folge |
|---|---|---|
| Daten | Neu anfangen | Pocket ID wird neu eingerichtet: Admin, OIDC-Client, Gruppen, alle Passkeys. Die App-Tabellen legt Hibernate (`ddl-auto: update`) neu an. |
| Netzwerk | Private Network | App und Pocket ID bekommen VPC-Integration (`private_network_id`, ab Provider 2.87 verfügbar, Lockfile hat 2.87.0). |
| Größe | DB-DEV-S | Reicht für den Pilot. Später lässt sich der `node_type` ohne Neuaufbau ändern. |
| Backups | Keine | Gelöschte Daten sind sofort weg. Das passt zur Datenschutzerklärung („Es werden keine Sicherungskopien angelegt“), die bei Serverless SQL eigentlich nicht stimmte, weil sich dessen Sicherung nicht abschalten lässt. Risiko: Nach einem Fehler gibt es keine Wiederherstellung. |

## Änderungen

### 1. `versions.tf`: Provider `random`

```hcl
random = {
  source  = "hashicorp/random"
  version = "~> 3.6"
}
```

Danach `tofu init -upgrade`. Die neuen Einträge in `.terraform.lock.hcl` kommen mit in den Commit.

Die Passwörter liegen damit im State. Das ist nicht anders als heute: Der State enthält bereits alle
IAM-Secret-Keys und ist deshalb verschlüsselt (`encryption`-Block).

### 2. `network.tf` (neu)

```hcl
resource "scaleway_vpc" "main" {
  name = var.prefix
  tags = local.tags
}

resource "scaleway_vpc_private_network" "main" {
  name   = var.prefix
  vpc_id = scaleway_vpc.main.id
  tags   = local.tags
}
```

### 3. `database.tf`: komplett ersetzen

```hcl
# Managed PostgreSQL statt Serverless SQL. Serverless SQL musste mit min_cpu = 1 warm
# bleiben (Kaltstart > 1 min, SQLSTATE 57P03) und kostete so ~€195/Monat. Eine
# DB-DEV-S läuft immer, zum Festpreis, und ist nur im Private Network erreichbar.

resource "random_password" "db_admin" {
  length      = 32
  special     = true
  min_upper   = 1
  min_lower   = 1
  min_numeric = 1
  min_special = 1
  # Nur Zeichen, die in einer URL ohne Ärger durchgehen; urlencode() deckt den Rest.
  override_special = "-_.~"
}

resource "random_password" "db_ajm"      { /* wie db_admin */ }
resource "random_password" "db_pocketid" { /* wie db_admin */ }

resource "scaleway_rdb_instance" "main" {
  name           = var.prefix
  node_type      = "DB-DEV-S"
  engine         = "PostgreSQL-16" # wie dev/ und Testcontainers
  is_ha_cluster  = false
  disable_backup = true            # bewusst, siehe docs/scaleway.md "Löschen und Aufbewahrung"
  encryption_at_rest = true

  volume_type       = "sbs_5k"
  volume_size_in_gb = 10

  user_name = "admin"
  password  = random_password.db_admin.result

  # Nur privat: Ohne load_balancer {} gibt es keinen öffentlichen Endpunkt.
  private_network {
    pn_id       = scaleway_vpc_private_network.main.id
    enable_ipam = true
  }

  tags = local.tags

  lifecycle {
    prevent_destroy = true
  }
}

resource "scaleway_rdb_database" "ajm" {
  instance_id = scaleway_rdb_instance.main.id
  name        = "ajm"
}

resource "scaleway_rdb_database" "pocketid" {
  instance_id = scaleway_rdb_instance.main.id
  name        = "pocketid"
}

resource "scaleway_rdb_user" "ajm" {
  instance_id = scaleway_rdb_instance.main.id
  name        = "ajm"
  password    = random_password.db_ajm.result
  is_admin    = false
}

resource "scaleway_rdb_user" "pocketid" {
  instance_id = scaleway_rdb_instance.main.id
  name        = "pocketid"
  password    = random_password.db_pocketid.result
  is_admin    = false
}

# "all": Hibernate (ddl-auto: update) und Pocket ID (Migrationen) legen Tabellen an.
# Jeder Nutzer bekommt nur seine eigene Datenbank.
resource "scaleway_rdb_privilege" "ajm" {
  instance_id   = scaleway_rdb_instance.main.id
  user_name     = scaleway_rdb_user.ajm.name
  database_name = scaleway_rdb_database.ajm.name
  permission    = "all"
}

resource "scaleway_rdb_privilege" "pocketid" {
  instance_id   = scaleway_rdb_instance.main.id
  user_name     = scaleway_rdb_user.pocketid.name
  database_name = scaleway_rdb_database.pocketid.name
  permission    = "all"
}

locals {
  db_ip   = scaleway_rdb_instance.main.private_network[0].ip
  db_port = scaleway_rdb_instance.main.private_network[0].port

  ajm_jdbc_url = "jdbc:postgresql://${local.db_ip}:${local.db_port}/ajm?sslmode=require"

  pocketid_db_url = format(
    "postgres://%s:%s@%s:%d/pocketid?sslmode=require",
    scaleway_rdb_user.pocketid.name,
    urlencode(random_password.db_pocketid.result),
    local.db_ip,
    local.db_port,
  )
}
```

Die IP statt des Hostnamens zu nehmen, ist Absicht. Sie hängt nicht davon ab, dass die interne DNS-Auflösung
im Container funktioniert. Über IPAM bleibt sie stabil.

### 4. `containers.tf`

- `scaleway_container.app` und `scaleway_container.pocketid` bekommen beide
  `private_network_id = scaleway_vpc_private_network.main.id`.
- App: `MANAGER_DB_USER = scaleway_rdb_user.ajm.name` und
  `MANAGER_DB_PASSWORD = random_password.db_ajm.result` statt IAM-Anwendung und Secret Key.
- `depends_on` von App und Pocket ID ergänzen um `scaleway_rdb_privilege.ajm` bzw. `.pocketid`. Sonst
  startet der Container womöglich, bevor sein Nutzer Rechte hat.
- Gotenberg bleibt unverändert, es braucht die DB nicht.

### 5. `iam.tf`

- `ServerlessSQLDatabaseReadWrite` aus den Policies `app` und `pocketid` streichen.
- Der IAM-Schlüssel von Pocket ID bleibt, er wird weiter für SMTP gebraucht.
- Den Kommentar „Regeln gelten pro Projekt, nicht pro Datenbank“ anpassen. Die DB-Trennung leisten jetzt die
  Postgres-Nutzer.

### 6. Doku

- `docs/scaleway.md`: Überblickstabelle, Abschnitt „Datenbank“, „Betrieb und Kosten“, die Tabelle „Löschen
  und Aufbewahrung“ (Managed DB ohne Backup: nach dem Löschen sofort weg) und den offenen Punkt zur
  Support-Anfrage wegen Serverless-SQL-Sicherungen (erledigt). Die Stolpersteine zu 57P03 bleiben als
  Geschichte stehen, mit dem Hinweis, dass sie mit der Managed DB nicht mehr auftreten.
- `deploy/scaleway/legal.example/datenschutz.de.yaml` und die echte Datenschutzerklärung im privaten Repo:
  „Serverless SQL Database“ → „Managed Database for PostgreSQL“.
- `Readme.md:333`: Dienstliste anpassen.
- `CHANGELOG.md` unter `[Unreleased]`: Die Pilot-Infrastruktur nutzt jetzt Managed PostgreSQL im Private
  Network statt Serverless SQL.

## Ablauf beim Umstellen

Es gibt zwei Applies. So sind die alten DBs erst dann weg, wenn die neue nachweislich läuft.

1. **Vorbereitung:** `app_min_scale = 0` in `pilot.tfvars`. `oidc_client_id`/`oidc_client_secret`
   auskommentieren bzw. aus der Umgebung nehmen, weil der OIDC-Client neu angelegt wird. Damit sind wir
   wieder in „Phase 1“.
2. **Apply 1:** Network, RDB-Instanz, Datenbanken, Nutzer und Container-Umstellung. Die alten
   `scaleway_sdb_sql_database` bleiben **vorerst in der Konfiguration**, nur ohne Verweise darauf.
   `tofu plan -out=plan.tfplan` prüfen. Erwartet werden `create` für Network und RDB und `update in-place`
   für die Container. Steht `replace` an einem Container, ist das in Ordnung (keine Daten). An Bucket oder
   TEM-Domain darf es **nicht** stehen.
3. **Pocket ID neu einrichten:** `https://id.<basis>/setup` aufrufen, Admin-Passkey anlegen, SMTP eintragen
   (`tofu output smtp` und `smtp_password`), Gruppen `User`/`Advisor`/`Reviewer` anlegen, OIDC-Client mit
   Redirect `tofu output urls` → `redirect`. Wie Phase 1/2 in `docs/scaleway.md`.
4. **Apply 2:** `oidc_client_id`/`_secret` setzen, bei Bedarf `app_min_scale`. Login testen, ein Unternehmen
   anlegen, ein Dokument hochladen, ein PDF erzeugen.
5. **Alte Dokumente:** Die Objekt-Keys beginnen mit dem alten OIDC-Subject. Die neuen Pocket-ID-Nutzer haben
   andere Subjects, die alten Dateien sind also verwaist. Bucket `ajm-pilot-documents` leeren. Die
   Lifecycle-Regel entfernt die alten Versionen nach 7 Tagen.
6. **Apply 3:** Die beiden `scaleway_sdb_sql_database` aus `database.tf` löschen. Wegen `prevent_destroy`
   muss der ganze Block raus, nicht nur das Attribut. Im Plan dürfen genau diese zwei als `destroy`
   erscheinen.
7. Nach ein paar Tagen in der Rechnung prüfen, dass „Serverless SQL Database“ auf €0 fällt.

## Zu prüfen beim Umsetzen

Diese Punkte konnte ich ohne Zugriff auf das Konto nicht abschließend klären:

- **Ausgehender Verkehr mit VPC-Integration:** Erreichen App und Pocket ID weiterhin das Internet
  (`api.scaleway.ai`, `s3.fr-par.scw.cloud`, die öffentliche Gotenberg-Adresse, OIDC über `id.<basis>`)?
  Laut Scaleway geht nur privater Verkehr ins Private Network. Gleich nach Apply 2 mit einem Import und einer
  PDF-Erzeugung prüfen.
- **`permission = "all"` reicht für `CREATE TABLE` in `public`?** Seit PostgreSQL 15 darf nicht jeder in
  `public` anlegen. Klappt es nicht, zeigt das App-Log beim Start `permission denied for schema public`.
  Ausweg: Dem Nutzer per Admin-Login `ALTER SCHEMA public OWNER TO ajm` geben (bzw. `pocketid`). Ohne
  öffentlichen Endpunkt geht das nur über die Konsole oder einen kurzzeitigen `load_balancer {}` mit ACL auf
  die eigene IP.
- **`sslmode=require` im Private Network:** Wird abgelehnt, auf `sslmode=prefer` gehen. Der Verkehr bleibt
  dann trotzdem im Private Network.
- **Preis von DB-DEV-S** und ob `sbs_5k` mit 10 GB die kleinste zulässige Kombination ist.
- **Pocket ID `v1`** legt sein Schema in einer leeren Datenbank selbst an (erwartet, wie beim ersten Aufbau).

## Rückweg

Bis Apply 3 existieren die alten Serverless-DBs noch. Notfalls `database.tf` und `containers.tf` aus Git
zurückholen und erneut anwenden. Die alten Daten sind dann wieder da, das alte Pocket-ID-Setup auch.
Nach Apply 3 gibt es keinen Rückweg.
