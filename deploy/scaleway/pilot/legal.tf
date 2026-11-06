# Impressum und Datenschutzerklärung des Piloten. Serverless Containers können keine
# Dateien einhängen, deshalb liest die App die Texte aus diesem Bucket
# (LEGAL_DOCUMENTS_S3_BUCKET). Eine Änderung wirkt nach spätestens 30 s, ohne Neustart.
# Die Texte selbst liegen in einem privaten Repo; var.legal_dir zeigt auf dessen Checkout.
# Ohne legal_dir entsteht nichts, und die App zeigt die Texte aus dem Image.

locals {
  legal_enabled = var.legal_dir != ""
  legal_files   = local.legal_enabled ? fileset(var.legal_dir, "*.yaml") : toset([])
}

resource "scaleway_object_bucket" "legal" {
  count = local.legal_enabled ? 1 : 0

  name = "${var.prefix}-legal"
  tags = { project = "ajm", stage = "pilot" }

  versioning {
    enabled = true
  }

  lifecycle {
    precondition {
      condition = alltrue([
        for f in local.legal_files : can(regex("^[a-z0-9][a-z0-9-]*\\.[a-z]{2,3}\\.yaml$", f))
      ])
      error_message = "Every file in legal_dir must be named <slug>.<lang>.yaml, e.g. impressum.de.yaml."
    }
    precondition {
      condition = alltrue([
        for slug in ["impressum", "datenschutz"] :
        length([for f in local.legal_files : f if startswith(f, "${slug}.")]) > 0
      ])
      error_message = "legal_dir must contain at least one impressum.<lang>.yaml and one datenschutz.<lang>.yaml."
    }
  }
}

# Wie beim Dokumenten-Bucket: Nach einer Policy hat niemand sonst Zugriff, darum auch hier
# operator_principal. Die App darf nur lesen.
resource "scaleway_object_bucket_policy" "legal" {
  count = local.legal_enabled ? 1 : 0

  bucket = scaleway_object_bucket.legal[0].id

  policy = jsonencode({
    Version = "2023-04-17"
    Statement = [
      {
        Sid       = "AppRead"
        Effect    = "Allow"
        Principal = { SCW = "application_id:${scaleway_iam_application.app.id}" }
        Action    = ["s3:GetObject", "s3:ListBucket"]
        Resource = [
          scaleway_object_bucket.legal[0].name,
          "${scaleway_object_bucket.legal[0].name}/*",
        ]
      },
      {
        Sid       = "Operator"
        Effect    = "Allow"
        Principal = { SCW = var.operator_principal }
        Action    = ["s3:*"]
        Resource = [
          scaleway_object_bucket.legal[0].name,
          "${scaleway_object_bucket.legal[0].name}/*",
        ]
      },
    ]
  })
}

# Mit file und hash statt content steht der Text nicht im Plan; der Hash löst den Upload aus.
resource "scaleway_object" "legal" {
  for_each = local.legal_files

  bucket       = scaleway_object_bucket.legal[0].name
  key          = each.key
  file         = "${var.legal_dir}/${each.key}"
  hash         = filemd5("${var.legal_dir}/${each.key}")
  content_type = "application/yaml"

  depends_on = [scaleway_object_bucket_policy.legal]
}
