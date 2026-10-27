terraform {
  # 1.11: write-only-Attribute; 1.10: natives S3-Locking (use_lockfile).
  required_version = ">= 1.11"

  required_providers {
    scaleway = {
      source  = "scaleway/scaleway"
      version = "~> 2.84"
    }
  }

  # State in Scaleway Object Storage, mit Locking über eine Lock-Datei im Bucket.
  # Zugangsdaten NICHT hier eintragen, sondern per Umgebung:
  #   export AWS_ACCESS_KEY_ID=$SCW_ACCESS_KEY AWS_SECRET_ACCESS_KEY=$SCW_SECRET_KEY
  backend "s3" {
    bucket = "ajm-pilot-tofu-state"
    key    = "pilot/terraform.tfstate"
    region = "fr-par"

    endpoints = {
      s3 = "https://s3.fr-par.scw.cloud"
    }
    use_path_style = true
    use_lockfile   = true

    # Scaleway ist nicht AWS: diese Prüfungen abschalten.
    skip_credentials_validation = true
    skip_region_validation      = true
    skip_requesting_account_id  = true
    skip_metadata_api_check     = true
    skip_s3_checksum            = true
  }

  # Der State enthält IAM-Secret-Keys und alle Container-Secrets.
  # Deshalb wird er (und jeder gespeicherte Plan) verschlüsselt, bevor er
  # den Rechner verlässt. Passphrase per Umgebung:
  #   export TF_VAR_state_passphrase='...mindestens 16 Zeichen...'
  encryption {
    key_provider "pbkdf2" "main" {
      passphrase = var.state_passphrase
    }

    method "aes_gcm" "main" {
      keys = key_provider.pbkdf2.main
    }

    state {
      method   = method.aes_gcm.main
      enforced = true
    }

    plan {
      method   = method.aes_gcm.main
      enforced = true
    }
  }
}
