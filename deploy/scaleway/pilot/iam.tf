# Ein Schlüssel pro Zweck. Wird einer kompromittiert, ist der Schaden begrenzt,
# und er lässt sich einzeln erneuern.
#
# Die Datenbanktrennung leisten die Postgres-Nutzer in database.tf (jeder sieht nur
# seine eigene Datenbank), nicht IAM. Die Bucket-Policy in storage.tf grenzt den
# Dokumenten-Zugriff zusätzlich ein.

# --- AJM-Anwendung: Dokumente, LLM -------------------------------------------

resource "scaleway_iam_application" "app" {
  name        = "${var.prefix}-app"
  description = "AJM-Backend: Object Storage, Generative APIs"
  tags        = local.tags
}

resource "scaleway_iam_policy" "app" {
  name           = "${var.prefix}-app"
  application_id = scaleway_iam_application.app.id

  rule {
    project_ids = [var.project_id]
    permission_set_names = [
      "ObjectStorageObjectsRead",
      "ObjectStorageObjectsWrite",
      "ObjectStorageObjectsDelete",
      "GenerativeApisModelAccess",
    ]
  }
}

resource "scaleway_iam_api_key" "app" {
  application_id = scaleway_iam_application.app.id
  description    = "AJM-Backend"

  # Object Storage ordnet Anfragen dem Standardprojekt des Schlüssels zu. Ohne
  # diese Angabe landet "ajm-pilot-documents" im falschen Projekt: AccessDenied.
  default_project_id = var.project_id

  # Scaleway gives a key without expires_at a one-year expiry; the provider reads
  # that back as a change and would replace the key on every plan.
  lifecycle {
    ignore_changes = [expires_at]
  }
}

# --- Aufruf des privaten Gotenberg-Containers ---------------------------------
# Private Serverless Containers prüfen den Header "X-Auth-Token" gegen IAM.
# Der Secret Key dieser Anwendung ist das GOTENBERG_AUTH_TOKEN der App.

resource "scaleway_iam_application" "gotenberg_caller" {
  name        = "${var.prefix}-gotenberg-caller"
  description = "Darf nur private Container aufrufen"
  tags        = local.tags
}

resource "scaleway_iam_policy" "gotenberg_caller" {
  name           = "${var.prefix}-gotenberg-caller"
  application_id = scaleway_iam_application.gotenberg_caller.id

  rule {
    project_ids          = [var.project_id]
    permission_set_names = ["ContainersPrivateAccess"]
  }
}

resource "scaleway_iam_api_key" "gotenberg_caller" {
  application_id = scaleway_iam_application.gotenberg_caller.id
  description    = "X-Auth-Token für Gotenberg"

  # Scaleway gives a key without expires_at a one-year expiry; the provider reads
  # that back as a change and would replace the key on every plan.
  lifecycle {
    ignore_changes = [expires_at]
  }
}

# --- Pocket ID: E-Mail-Versand -----------------------------------------------

resource "scaleway_iam_application" "pocketid" {
  name        = "${var.prefix}-pocketid"
  description = "Pocket ID: SMTP"
  tags        = local.tags
}

resource "scaleway_iam_policy" "pocketid" {
  name           = "${var.prefix}-pocketid"
  application_id = scaleway_iam_application.pocketid.id

  rule {
    project_ids          = [var.project_id]
    permission_set_names = ["TransactionalEmailEmailSmtpCreate"]
  }
}

resource "scaleway_iam_api_key" "pocketid" {
  application_id = scaleway_iam_application.pocketid.id
  description    = "Pocket ID"

  # Scaleway gives a key without expires_at a one-year expiry; the provider reads
  # that back as a change and would replace the key on every plan.
  lifecycle {
    ignore_changes = [expires_at]
  }
}
