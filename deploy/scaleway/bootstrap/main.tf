# Bootstrap: legt einmalig an, was die eigentliche Konfiguration voraussetzt.
#
#   - das Scaleway-Projekt "ajm-pilot"
#   - den Bucket, in dem der State der Pilotplattform liegt
#
# Henne-Ei-Problem: Der State-Bucket kann seinen eigenen State nicht enthalten.
# Dieser Ordner arbeitet deshalb mit lokalem State (terraform.tfstate hier im
# Ordner). Er enthält keine Geheimnisse, trotzdem nicht ins Repo einchecken.
#
# Ausführen mit dem persönlichen API-Key (Organisations-Rechte nötig):
#   export SCW_ACCESS_KEY=... SCW_SECRET_KEY=... SCW_DEFAULT_ORGANIZATION_ID=...
#   tofu init && tofu apply

terraform {
  required_version = ">= 1.11"

  required_providers {
    scaleway = {
      source  = "scaleway/scaleway"
      version = "~> 2.84"
    }
  }
}

provider "scaleway" {
  region = "fr-par"
  zone   = "fr-par-1"
}

variable "state_bucket_name" {
  description = "Weltweit eindeutiger Name für den State-Bucket"
  type        = string
  default     = "ajm-pilot-tofu-state"
}

resource "scaleway_account_project" "pilot" {
  name        = "ajm-pilot"
  description = "AJM-Pilotplattform (Zugang auf Einladung)"
}

resource "scaleway_object_bucket" "state" {
  name       = var.state_bucket_name
  project_id = scaleway_account_project.pilot.id

  # Alte State-Versionen behalten: Rettungsanker bei einem kaputten Apply.
  versioning {
    enabled = true
  }

  lifecycle {
    prevent_destroy = true
  }
}

output "project_id" {
  description = "In pilot/pilot.tfvars als project_id eintragen"
  value       = scaleway_account_project.pilot.id
}

output "state_bucket" {
  value = scaleway_object_bucket.state.name
}
