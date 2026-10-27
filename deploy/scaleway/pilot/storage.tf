# Dokumente der Nutzer*innen. Inhalt verschlüsselt die App per SSE-C,
# der Schlüssel liegt nicht bei Scaleway.

resource "scaleway_object_bucket" "documents" {
  name = "${var.prefix}-documents"
  tags = { project = "ajm", stage = "pilot" }

  versioning {
    enabled = true
  }

  # Echte Bewerberdaten: ein versehentliches "tofu destroy" darf sie nicht löschen.
  lifecycle {
    prevent_destroy = true
  }
}

# Nur die App und der Betreiber dürfen an den Bucket.
# Achtung: Sobald eine Bucket-Policy existiert, hat niemand sonst Zugriff,
# auch nicht der Projekt-Owner über die Konsole. Deshalb operator_principal.
resource "scaleway_object_bucket_policy" "documents" {
  bucket = scaleway_object_bucket.documents.id

  policy = jsonencode({
    Version = "2023-04-17"
    Statement = [
      {
        Sid       = "AppObjects"
        Effect    = "Allow"
        Principal = { SCW = "application_id:${scaleway_iam_application.app.id}" }
        Action    = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:ListBucket"]
        Resource = [
          scaleway_object_bucket.documents.name,
          "${scaleway_object_bucket.documents.name}/*",
        ]
      },
      {
        Sid       = "Operator"
        Effect    = "Allow"
        Principal = { SCW = var.operator_principal }
        Action    = ["s3:*"]
        Resource = [
          scaleway_object_bucket.documents.name,
          "${scaleway_object_bucket.documents.name}/*",
        ]
      },
    ]
  })
}

# Private Registry für die eigenen Images.
resource "scaleway_registry_namespace" "ajm" {
  name        = "ajm-pilot"
  description = "AJM-Images (App, Gotenberg- und Pocket-ID-Spiegel)"
  is_public   = false
}
