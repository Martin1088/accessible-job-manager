resource "scaleway_container_namespace" "main" {
  name        = var.prefix
  description = "AJM-Pilotplattform"
  tags        = local.tags
}

# --- Gotenberg: privat, nur mit IAM-Token erreichbar ---------------------------

resource "scaleway_container" "gotenberg" {
  name         = "gotenberg"
  namespace_id = scaleway_container_namespace.main.id
  image        = var.gotenberg_image
  port         = 3000
  privacy      = "private"

  cpu_limit          = 500 # mvCPU, also 0,5 vCPU
  memory_limit_bytes = 1 * local.gib
  min_scale          = 0
  max_scale          = 1
  timeout            = 120

  https_connections_only = true

  command = ["gotenberg"]
  args    = ["--chromium-deny-private-ips", "--api-timeout=30s"]
}

# --- Pocket ID: Anmeldung per Passkey -------------------------------------------

resource "scaleway_container" "pocketid" {
  name         = "pocketid"
  namespace_id = scaleway_container_namespace.main.id
  image        = var.pocketid_image
  port         = 1411
  privacy      = "public"

  cpu_limit          = 250
  memory_limit_bytes = 256 * local.mib
  min_scale          = 0
  max_scale          = 1
  timeout            = 60

  https_connections_only = true

  environment_variables = {
    APP_URL            = "https://${local.id_host}"
    TRUST_PROXY        = "true"
    FILE_BACKEND       = "database"
    ANALYTICS_DISABLED = "true"

    # Das Image-Tag v1 schwebt. Ältere 1.x-Versionen nehmen SQLite, solange
    # DB_PROVIDER fehlt, und lesen die URL dann als Dateipfad.
    DB_PROVIDER = "postgres"
  }

  secret_environment_variables = {
    DB_CONNECTION_STRING = local.pocketid_db_url
    ENCRYPTION_KEY       = var.pocketid_encryption_key
  }

  # Der Container nutzt den Schlüssel, aber nicht die Policy direkt.
  # Ohne depends_on könnte er starten, bevor seine Rechte existieren.
  depends_on = [scaleway_iam_policy.pocketid]
}

# --- AJM-Anwendung ----------------------------------------------------------------

resource "scaleway_container" "app" {
  name         = "app"
  namespace_id = scaleway_container_namespace.main.id
  image        = var.app_image
  port         = 8060
  privacy      = "public"

  cpu_limit          = 1000
  memory_limit_bytes = 2 * local.gib
  min_scale          = var.app_min_scale
  max_scale          = 2
  timeout            = 120

  https_connections_only = true

  environment_variables = {
    MANAGER_DB_URL  = local.ajm_jdbc_url
    MANAGER_DB_USER = scaleway_iam_application.app.id

    STORAGE_PROVIDER = "s3"
    S3_ENDPOINT      = "https://s3.${var.region}.scw.cloud"
    REGION           = var.region
    S3_BUCKET        = scaleway_object_bucket.documents.name
    S3_SSE_C_MODE    = "sse-c"

    GOTENBERG_URL = scaleway_container.gotenberg.public_endpoint

    JOB_POSTING_LLM_PROVIDER = "openai"
    OPENAI_BASE_URL          = "https://api.scaleway.ai/v1"
    OPENAI_MODEL             = var.llm_model

    OIDC_ISSUER_URI   = "https://${local.id_host}"
    OIDC_AUTH_URI     = "https://${local.id_host}/authorize"
    OIDC_REDIRECT_URI = "https://${local.app_host}/login/oauth2/code/authentik"
    OIDC_SCOPE        = "openid, email, profile, groups"
    OIDC_CLIENT_ID    = var.oidc_client_id
    GROUP_USER        = "User"
    GROUP_ADVISOR     = "Advisor"
    GROUP_REVIEWER    = "Reviewer"

    COOKIE_SECURE         = "true"
    DIAGNOSTICS_LOG_LEVEL = "OFF"
  }

  secret_environment_variables = {
    MANAGER_DB_PASSWORD  = scaleway_iam_api_key.app.secret_key
    ACCESS_KEY           = scaleway_iam_api_key.app.access_key
    SECRET_KEY           = scaleway_iam_api_key.app.secret_key
    S3_SSE_C_KEY         = var.sse_c_key
    OPENAI_API_KEY       = scaleway_iam_api_key.app.secret_key
    GOTENBERG_AUTH_TOKEN = scaleway_iam_api_key.gotenberg_caller.secret_key
    OIDC_CLIENT_SECRET   = var.oidc_client_secret
  }

  depends_on = [
    scaleway_iam_policy.app,
    scaleway_iam_policy.gotenberg_caller,
    scaleway_object_bucket_policy.documents,
  ]

  lifecycle {
    # Ohne OIDC-Client startet Spring Boot nicht sinnvoll.
    precondition {
      condition     = local.oidc_ready || var.app_min_scale == 0
      error_message = "Vor app_min_scale > 0 erst oidc_client_id und oidc_client_secret setzen (Phase 2)."
    }
  }
}
