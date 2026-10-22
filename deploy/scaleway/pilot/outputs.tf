# DNS-Ausgaben sind bewusst getrennt: So gibt es dns_id und dns_email schon,
# bevor der App-Container existiert (Phase 1).

output "dns_id" {
  description = "CNAME für die Anmeldung, beim DNS-Anbieter einzutragen"
  value = {
    name   = local.id_host
    type   = "CNAME"
    target = trimprefix(scaleway_container.pocketid.public_endpoint, "https://")
  }
}

output "dns_app" {
  description = "CNAME für die Anwendung, beim DNS-Anbieter einzutragen"
  value = {
    name   = local.app_host
    type   = "CNAME"
    target = trimprefix(scaleway_container.app.public_endpoint, "https://")
  }
}

output "dns_email" {
  description = "SPF, DKIM, DMARC für den E-Mail-Versand"
  value = {
    spf = {
      name  = var.base_domain
      type  = "TXT"
      value = scaleway_tem_domain.main.spf_value
    }
    dkim = {
      name  = trimsuffix(scaleway_tem_domain.main.dkim_name, ".")
      type  = "TXT"
      value = scaleway_tem_domain.main.dkim_config
    }
    dmarc = {
      name  = trimsuffix(scaleway_tem_domain.main.dmarc_name, ".")
      type  = "TXT"
      value = scaleway_tem_domain.main.dmarc_config
    }
  }
}

output "urls" {
  value = {
    app      = "https://${local.app_host}"
    login    = "https://${local.id_host}"
    redirect = "https://${local.app_host}/login/oauth2/code/authentik"
  }
}

output "smtp" {
  description = "Für die SMTP-Einstellungen in der Pocket-ID-Oberfläche"
  value = {
    host = scaleway_tem_domain.main.smtp_host
    port = scaleway_tem_domain.main.smtp_port
    user = scaleway_tem_domain.main.smtps_auth_user
  }
}

output "smtp_password" {
  description = "tofu output -raw smtp_password"
  value       = scaleway_iam_api_key.pocketid.secret_key
  sensitive   = true
}

output "registry_endpoint" {
  value = scaleway_registry_namespace.ajm.endpoint
}

output "phase" {
  value = local.oidc_ready ? "Phase 2: OIDC-Client gesetzt" : "Phase 1: OIDC-Client in Pocket ID anlegen, dann oidc_client_id/secret setzen"
}
