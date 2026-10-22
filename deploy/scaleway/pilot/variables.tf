# --- Grundlagen -------------------------------------------------------------

variable "project_id" {
  description = "ID des Projekts ajm-pilot (Ausgabe von bootstrap)"
  type        = string
}

variable "region" {
  description = "Scaleway-Region"
  type        = string
  default     = "fr-par"
}

variable "prefix" {
  description = "Präfix für alle Ressourcennamen"
  type        = string
  default     = "ajm-pilot"
}

variable "state_passphrase" {
  description = "Passphrase für die State-Verschlüsselung (min. 16 Zeichen)"
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.state_passphrase) >= 16
    error_message = "Die Passphrase muss mindestens 16 Zeichen lang sein."
  }
}

variable "operator_principal" {
  description = <<-EOT
    Wer OpenTofu ausführt, im Format der Bucket-Policy, z. B.
    "user_id:<uuid>" oder "application_id:<uuid>". Ohne diesen Eintrag
    sperrt die Bucket-Policy dich selbst aus dem Dokumenten-Bucket aus.
  EOT
  type        = string
}

# --- Domains ----------------------------------------------------------------

variable "base_domain" {
  description = "Basisdomain, z. B. ajm.example"
  type        = string
}

variable "enable_id_domain" {
  description = "Erst true, wenn der CNAME id.<domain> gesetzt ist (Output dns_id)"
  type        = bool
  default     = false
}

variable "enable_app_domain" {
  description = "Erst true, wenn der CNAME app.<domain> gesetzt ist (Output dns_app)"
  type        = bool
  default     = false
}

# --- Images -----------------------------------------------------------------

variable "app_image" {
  description = "AJM-Pilot-Image in der Scaleway-Registry, mit festem Tag"
  type        = string
}

variable "gotenberg_image" {
  description = "Gotenberg-Image, in die eigene Registry gespiegelt"
  type        = string
  default     = "rg.fr-par.scw.cloud/ajm-pilot/gotenberg:8"
}

variable "pocketid_image" {
  description = "Pocket-ID-Image, in die eigene Registry gespiegelt"
  type        = string
  default     = "rg.fr-par.scw.cloud/ajm-pilot/pocket-id:v1"
}

# --- Anwendung ----------------------------------------------------------------

variable "llm_model" {
  description = "Modellname bei Scaleway Generative APIs"
  type        = string
  default     = "mistral-small-3.2-24b-instruct-2506"
}

variable "app_min_scale" {
  description = "0 = Scale-to-Zero; 1 = immer warm"
  type        = number
  default     = 0
}

# --- Geheimnisse (nie in Dateien, nur per TF_VAR_...) -------------------------

variable "sse_c_key" {
  description = "AES-256-Schlüssel für SSE-C, Base64 (openssl rand -base64 32)"
  type        = string
  sensitive   = true
}

variable "pocketid_encryption_key" {
  description = "ENCRYPTION_KEY für Pocket ID (openssl rand -base64 32)"
  type        = string
  sensitive   = true
}

variable "oidc_client_id" {
  description = "Client-ID aus der Pocket-ID-Oberfläche (Phase 2)"
  type        = string
  default     = ""
}

variable "oidc_client_secret" {
  description = "Client-Secret aus der Pocket-ID-Oberfläche (Phase 2)"
  type        = string
  sensitive   = true
  default     = ""
}
