# AJM auf Scaleway: Pilotplattform

Anleitung, wie der Accessible Job Manager (AJM) als Pilot auf Scaleway betrieben wird:
was angelegt wird, in welcher Reihenfolge, und welche Fehler dabei aufgetreten sind.
Alles ist als OpenTofu-Konfiguration beschrieben (`deploy/scaleway/bootstrap/` und `deploy/scaleway/pilot/`).

## 1. Überblick

| Baustein | Scaleway-Dienst | Zweck |
|---|---|---|
| App | Serverless Container | Spring-Boot-Backend mit der Angular-Oberfläche, Port 8060 |
| Anmeldung | Serverless Container (Pocket ID) | Passkey-Anmeldung per OIDC, Domain `id.<basis>` |
| PDF-Erzeugung | Serverless Container (Gotenberg), **privat** | HTML/URL → PDF, nur mit IAM-Token erreichbar |
| Datenbank | Serverless SQL Database (PostgreSQL) | Daten der App und von Pocket ID |
| Dokumente | Object Storage, Bucket `ajm-pilot-documents` | PDFs und Vorlagen, verschlüsselt per SSE-C |
| Sprachmodell | Generative APIs (OpenAI-kompatibel) | Einlesen von Stellenanzeigen, CV-Abgleich |
| E-Mail | Transactional Email (TEM) | SMTP für Pocket ID |
| Images | Container Registry, Namespace `ajm-pilot` | App-, Gotenberg- und Pocket-ID-Image |
| State | Object Storage, Bucket `ajm-pilot-tofu-state` | OpenTofu-State, verschlüsselt |

Domains: `app.<basis>` (Anwendung) und `id.<basis>` (Anmeldung). Der DNS-Anbieter ist
unabhängig von Scaleway (hier Hetzner), die Einträge setzt man von Hand.

## 2. Voraussetzungen

- OpenTofu ≥ 1.11, Docker, `jq`, `curl`, optional die Scaleway-CLI `scw`.
- Ein Scaleway-Konto mit Organisations-Rechten, eine eigene Domain mit Zugriff auf deren DNS.
- Zwei API-Schlüssel-Paare, die **nicht** verwechselt werden dürfen:
  - `SCW_ACCESS_KEY` / `SCW_SECRET_KEY` für den Provider.
  - `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` für das State-Backend (derselbe Schlüssel ist möglich,
    der Backend-Teil liest aber nur die `AWS_*`-Variablen).
- **Der Schlüssel braucht `default-project-id` = Projekt `ajm-pilot`.** Object Storage ordnet Anfragen dem
  Standardprojekt des Schlüssels zu. Ohne diese Angabe kommt `403` beim Zugriff auf den State-Bucket.
- Geheimnisse nie in Dateien, nur als `TF_VAR_...` oder Umgebungsvariable. Beim Kopieren auf **Leerzeichen am Ende**
  achten (ein Secret mit 37 statt 36 Zeichen erzeugte `SignatureDoesNotMatch`).

## 3. Ablauf in Phasen

### Phase 0: Bootstrap (einmalig, `deploy/scaleway/bootstrap/`)

Legt das Projekt `ajm-pilot` und den State-Bucket an. Wegen des Henne-Ei-Problems arbeitet dieser Ordner mit
lokalem State (`terraform.tfstate` liegt im Ordner, nicht einchecken).

```sh
cd deploy/scaleway/bootstrap   # vom Repository-Wurzelverzeichnis aus
export SCW_ACCESS_KEY=... SCW_SECRET_KEY=... SCW_DEFAULT_ORGANIZATION_ID=...
tofu init && tofu apply
```

Danach einen API-Schlüssel mit Standardprojekt `ajm-pilot` anlegen (siehe Voraussetzungen).

### Phase 1: Registry, Pocket ID, E-Mail

1. `deploy/scaleway/pilot/pilot.tfvars` aus `pilot.tfvars.example` anlegen (`project_id`, `operator_principal`, `base_domain`, Image-Tags).
   Die Datei steht in `.gitignore`.
2. `tofu init` im Ordner `deploy/scaleway/pilot/` (mit den `AWS_*`-Variablen).
3. Registry-Namespace anlegen. Zu kurze Namen lehnt Scaleway ab (HTTP 400), deshalb `ajm-pilot`.
4. Images in die eigene Registry spiegeln:

   ```sh
   printf '%s' "$SCW_SECRET_KEY" | docker login rg.fr-par.scw.cloud/ajm-pilot -u nologin --password-stdin

   docker pull --platform linux/amd64 gotenberg/gotenberg:8
   docker tag  gotenberg/gotenberg:8 rg.fr-par.scw.cloud/ajm-pilot/gotenberg:8
   docker push rg.fr-par.scw.cloud/ajm-pilot/gotenberg:8
   # entsprechend für pocket-id:v1
   ```

5. Pocket ID, Datenbank, IAM und TEM anlegen. Der Transactional-Email-Dienst muss in der Konsole **vorher
   aktiviert** sein (sonst `403 No active offer subscription`).
6. DNS für den E-Mail-Versand eintragen (`tofu output dns_email`): SPF, DKIM, DMARC. Danach in der
   TEM-Oberfläche „Check DNS“ drücken, bis alles grün ist.

### Phase 1b: Domain für Pocket ID

1. `tofu output dns_id` zeigt den CNAME. Eintragen (Ziel **mit Punkt am Ende**).
2. `dig +short id.<basis> CNAME @8.8.8.8` prüfen.
3. `enable_id_domain = true` in `pilot.tfvars`, planen, anwenden.
4. `https://id.<basis>` öffnen, Administrator-Konto mit Passkey anlegen, SMTP in Pocket ID einstellen
   (Werte aus `tofu output smtp` und `tofu output -raw smtp_password`).

### Phase 2: OIDC-Client und App

1. In Pocket ID einen OIDC-Client anlegen. Callback-URL: `https://app.<basis>/login/oauth2/code/authentik`.
2. Gruppen `User`, `Advisor`, `Reviewer` anlegen, sich selbst in `User` aufnehmen und die Gruppen beim Client
   erlauben. Ohne Gruppe lehnt die App den Login ab.
3. In `pilot.tfvars`: `oidc_client_id`, `app_image` (mit festem Tag), `app_min_scale = 1` zum Testen.
   Secret per `export TF_VAR_oidc_client_secret='...'`.
4. App-Image bereitstellen. Die CI baut es nach GHCR. Das Skript `deploy/scaleway/push-image.sh` holt es
   von dort, taggt es um, schiebt es in die eigene Registry, trägt `app_image` in `pilot.tfvars` ein und
   zeigt den Plan (einmalig vorher `docker login`, siehe Phase 1):

   ```sh
   deploy/scaleway/push-image.sh sha-<commit>            # Plan ansehen
   deploy/scaleway/push-image.sh sha-<commit> --apply    # und direkt anwenden
   ```

   Von Hand sind es diese Schritte:

   ```sh
   SRC=ghcr.io/martin1088/accessible-job-manager:sha-<commit>
   IMG=rg.fr-par.scw.cloud/ajm-pilot/accessible-job-manager-pilot:sha-<commit>
   docker pull --platform linux/amd64 $SRC && docker tag $SRC $IMG && docker push $IMG
   ```

   Das Image muss den Stand des Backends enthalten, der den Provider `openai` und den Gotenberg-Token kennt.
5. `tofu plan -var-file=pilot.tfvars -out=plan.tfplan`, Plan prüfen (**kein `destroy`, kein `replace`** an
   Bucket, Datenbank oder Pocket ID), dann `tofu apply plan.tfplan`. Das Skript macht den Plan schon
   mit; `--apply` sollte man erst nach dem Lesen des Plans nehmen.

### Phase 3: Domain für die App

1. `tofu output dns_app` → CNAME bei Hetzner eintragen (Ziel mit Punkt am Ende).
2. `enable_app_domain = true`, planen, anwenden. Das Zertifikat stellt Scaleway aus, das dauert einige Minuten.
3. `https://app.<basis>` öffnen und anmelden.

## 4. Wie die App konfiguriert ist

Alles, was die App braucht, setzt `deploy/scaleway/pilot/containers.tf`. Die wichtigsten Punkte:

- **Sprachmodell:** `JOB_POSTING_LLM_PROVIDER=openai`, Modell `mistral-small-3.2-24b-instruct-2506`
  (`llm_model`). Der Schlüssel der App dient als `OPENAI_API_KEY`.
  **`OPENAI_BASE_URL` enthält die Projekt-ID:** `https://api.scaleway.ai/<projekt-id>/v1`. Ohne sie antwortet
  Scaleway mit `403 insufficient permissions to access the resource`, auch bei gültigem Schlüssel.
  Für den CV-Abgleich kann ein anderes Modell gesetzt werden: `cv_matching_model` in `pilot.tfvars`
  (wird zu `CV_MATCHING_OPENAI_MODEL`). Leer heißt: wie `llm_model`. Das Einlesen von Anzeigen bleibt beim
  kleineren Modell. Die Variable darf nicht leer an die App gehen, ein leerer Wert zählt als gesetzt und hebelt
  den Rückfall aus, deshalb kommt sie nur mit Wert in die Umgebung.
- **Gotenberg:** privat. Die App sendet den Secret Key einer eigenen IAM-Anwendung als `X-Auth-Token`
  (`GOTENBERG_AUTH_TOKEN`). Rechte: Policy `gotenberg_caller`.
- **Dokumente:** S3 mit Path-Style, SSE-C (`S3_SSE_C_MODE=sse-c`, Schlüssel `S3_SSE_C_KEY`).
- **Datenbank:** zwei getrennte Serverless-SQL-Datenbanken (`ajm`, `pocketid`), beide mit `min_cpu = 1` und
  `max_cpu = 1`, also bewusst immer warm. Benutzer ist die ID der IAM-Anwendung, Passwort ist deren Secret Key.
  Mit `min_cpu = 0` dauerte der Kaltstart über eine Minute, siehe Stolpersteine.
- **Anmeldung:** `OIDC_ISSUER_URI=https://id.<basis>`, Gruppen `User`/`Advisor`/`Reviewer`.
- **Zeitlimits:** App-Container `timeout = 300` (der CV-Abgleich darf bis zu 300 s auf das Modell warten),
  Gotenberg `--api-timeout=120s` und Container-`timeout = 180`, App → Gotenberg
  `GOTENBERG_READ_TIMEOUT_SECONDS=150` (länger als Gotenberg, damit dessen Fehlertext ankommt).
- **Gotenberg-Skalierung:** `scaling_option { concurrent_requests_threshold = 1 }`, `max_scale = 3`. Ein Import
  startet mehrere Renderings gleichzeitig. Der Schwellwert ist nur ein Hinweis zum Hochskalieren, keine harte
  Grenze: Kommen zwei Anfragen gleichzeitig an einen kalten Container, landen beide auf der ersten Instanz und
  teilen sich die vCPU (`context deadline exceeded`, HTTP 503). Deshalb die längeren Zeitlimits. Ein Kaltstart
  kostet rund 15 s zusätzlich. `gotenberg_min_scale = 2` hält zwei Instanzen warm (kostet Dauerbetrieb).
- **Diagnose:** `DIAGNOSTICS_LOG_LEVEL=INFO` schreibt pro Einlesen eine Zeile `DIAG|host|kategorie|status|…` auf stdout.

## 5. Logs lesen

Container-Logs liegen in Scaleway Cockpit (Loki). In der Konsole beim Container unter **Logs** →
**Open Grafana logs dashboard**. Fehlen Daten, dort **Trigger synchronization** drücken. Grafana → **Explore**,
Datenquelle `Scaleway Logs – fr-par`, Code-Modus:

```
# Fehler der App
{resource_type="serverless_container", resource_name=~".*-app"} |~ "ERROR|Exception|DIAG\\|"

# Anfragen der App an Gotenberg (Status, Dauer)
{resource_type="serverless_container", resource_name=~".*gotenberg"} |= "request handled" != "curl"
```

Zeitraum eng wählen (zum Beispiel die letzten 15 Minuten) und direkt nach dem Versuch nachsehen.

## 6. Bekannte Stolpersteine

| Symptom | Ursache | Lösung |
|---|---|---|
| `tofu init`: `No valid credential sources found` | `AWS_*`-Variablen fehlen | `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` setzen |
| `403 Forbidden` on `HeadObject` for the state bucket | Schlüssel hat anderes Standardprojekt | Neuen Schlüssel mit `default-project-id` anlegen |
| `SignatureDoesNotMatch` | Leerzeichen am Ende des Secrets | Secret ohne Leerzeichen setzen |
| `docker push`: `denied` | Namespace fehlt oder Name zu kurz | Namespace `ajm-pilot` anlegen |
| TEM: `403 No active offer subscription` | Dienst nicht aktiviert | Transactional Email in der Konsole aktivieren |
| Doppelte Domain in der TEM-Oberfläche | Der Assistent hat eine zweite angelegt | Die Domain mit abweichender ID löschen |
| Hetzner: `TXT records must be fully escaped` | TXT-Werte müssen in Anführungszeichen stehen, in Gruppen zu je 255 Zeichen | Wert aufteilen und quoten |
| Hetzner: CNAME wird an die Zone angehängt | Ziel ohne Punkt am Ende | Ziel mit abschließendem Punkt eintragen |
| DKIM bei Hetzner ungültig | Spezial-Editor lässt `k=rsa` weg | Über das allgemeine TXT-Formular mit richtig geteilten Gruppen anlegen |
| Pocket ID startet nicht, DB-URL wird als Dateipfad gelesen | Floating-Tag `v1` nimmt SQLite, wenn `DB_PROVIDER` fehlt | `DB_PROVIDER=postgres` setzen |
| SMTP-Test in Pocket ID schlägt fehl | TLS-Option passt nicht zum Port | 465 mit TLS, 587 mit StartTLS; nach Schlüsseltausch Passwort erneuern |
| Browser: `DNS_PROBE_FINISHED_NXDOMAIN`, but `dig @8.8.8.8` resolves | Router/Resolver hat die Absage gemerkt | DNS-Cache leeren, öffentlichen DNS-Server nutzen, oder warten |
| App: "The server could not be reached. It may be restarting, or your connection dropped" | Status 0 im Browser: die App hat nicht geantwortet (Verbindung gekappt, Container-Timeout) | `timeout` des App-Containers prüfen, App-Log lesen |
| App: "The job posting page could not be converted to PDF" | Gotenberg antwortet mit HTTP-Fehler (503 `context deadline exceeded`) | Gotenberg-Log lesen, Zeitlimits und `gotenberg_min_scale` siehe oben |
| Pocket ID: `FATAL: Database took more than 1m0s to start, please retry later (SQLSTATE 57P03)` | Datenbank mit `min_cpu = 0` schläft und startet zu langsam | `min_cpu = 1` in `database.tf` |
| App container: `Container crashed with exit code 1`, `java.net.SocketTimeoutException: Read timed out` at startup | OIDC-Discovery gegen Pocket ID hängt, weil dessen Datenbank kalt ist | Datenbanken warm halten; ggf. `app_min_scale = 1` |
| App: "Job posting extraction model returned an error (403)" | `OPENAI_BASE_URL` ohne Projekt-ID | Adresse mit Projekt-ID im Pfad verwenden |
| Upload: `AccessDenied` on the documents bucket | Schlüssel der App ohne Standardprojekt | `default_project_id` am `scaleway_iam_api_key.app` setzen |
| `tofu output urls` fails after `-target` runs | `-target` aktualisiert unbeteiligte Outputs nicht | Einmal vollständig anwenden |

## 7. Geheimnisse sichern

Diese Werte gehören in einen Passwortmanager. Ohne sie ist der Pilot nicht wiederherstellbar:

- State-Passphrase (`state_passphrase`).
- SSE-C-Schlüssel (`sse_c_key`). **Ohne ihn sind hochgeladene Dokumente nicht mehr lesbar.**
- Pocket-ID-Encryption-Key (`pocketid_encryption_key`).
- API-Schlüssel (`SCW_*`, `AWS_*`) und das OIDC-Client-Secret.

Das Bucket mit den Dokumenten hat eine Policy, die nur die App und `operator_principal` zulässt. Ohne den
Eintrag sperrt sich der Betreiber selbst aus. `prevent_destroy` verhindert ein versehentliches Löschen.

## 8. Betrieb und Kosten

- `app_min_scale = 0` fährt die App herunter, wenn niemand sie nutzt. Der erste Aufruf danach braucht einen
  Kaltstart. Für Tests `1`.
- Gotenberg läuft mit 1 vCPU und 2 GiB, aber nur, solange Anfragen laufen (`gotenberg_min_scale = 0`).
- Beide Datenbanken laufen dauerhaft mit 1 vCPU. Das ist der größte Kostenpunkt des Piloten. Serverless SQL
  hat keinen Freibetrag, abgerechnet wird die CPU-Zeit.
- Abgleich und Einlesen kosten Tokens bei den Generative APIs. Das Modell lässt sich pro Aufgabe wählen.
- Änderungen immer mit `tofu plan -out=plan.tfplan` ansehen und prüfen, bevor `apply` läuft.
  `destroy`/`replace` an Bucket, Datenbank oder Pocket ID sind ein Warnsignal.

## 9. Offene Punkte

- Der CV-Abgleich ist mit einem Lebenslauf noch nicht live geprüft.
- Das Feld „Ort“ wird beim Einlesen von Anzeigen oft nicht gefunden (Qualität von Modell und Prompt).
- Die Oracle-HCM-Anzeige hat bislang keinen Erfolg gezeigt; Ursache nicht abschließend geklärt.
