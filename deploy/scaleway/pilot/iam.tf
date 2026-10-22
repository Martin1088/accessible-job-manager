# Ein Schlüssel pro Zweck. Wird einer kompromittiert, ist der Schaden begrenzt,
# und er lässt sich einzeln erneuern.
#
# Grenze von Scaleway IAM: Regeln gelten pro Projekt, nicht pro Datenbank.
# Die Bucket-Policy in storage.tf grenzt den Dokumenten-Zugriff zusätzlich ein.

# --- AJM-Anwendung: Datenbank, Dokumente, LLM --------------------------------

resource "scaleway_iam_application" "app" {
  name        = "${var.prefix}-app"
  description = "AJM-Backend: Serverless SQL, Object Storage, Generative APIs"
  tags        = local.tags
}

resource "scaleway_iam_policy" "app" {
  name           = "${var.prefix}-app"
  application_id = scaleway_iam_application.app.id

  rule {
    project_ids = [var.project_id]
    permission_set_names = [
      "ServerlessSQLDatabaseReadWrite",
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
}

# --- Pocket ID: Datenbank und E-Mail-Versand ---------------------------------

resource "scaleway_iam_application" "pocketid" {
  name        = "${var.prefix}-pocketid"
  description = "Pocket ID: Serverless SQL und SMTP"
  tags        = local.tags
}

resource "scaleway_iam_policy" "pocketid" {
  name           = "${var.prefix}-pocketid"
  application_id = scaleway_iam_application.pocketid.id

  rule {
    project_ids = [var.project_id]
    permission_set_names = [
      "ServerlessSQLDatabaseReadWrite",
      "TransactionalEmailEmailSmtpCreate",
    ]
  }
}

resource "scaleway_iam_api_key" "pocketid" {
  application_id = scaleway_iam_application.pocketid.id
  description    = "Pocket ID"
}
