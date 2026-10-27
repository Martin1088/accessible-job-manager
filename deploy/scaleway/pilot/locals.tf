locals {
  app_host = "app.${var.base_domain}"
  id_host  = "id.${var.base_domain}"

  # Phase 2 ist erreicht, sobald der OIDC-Client in Pocket ID angelegt ist.
  # nonsensitive(): Ob das Secret gesetzt ist, verrät nichts über seinen Inhalt.
  oidc_ready = var.oidc_client_id != "" && nonsensitive(var.oidc_client_secret != "")

  # Speichergrößen lesbar halten.
  mib = 1024 * 1024
  gib = 1024 * local.mib

  tags = ["ajm", "pilot", "opentofu"]
}
