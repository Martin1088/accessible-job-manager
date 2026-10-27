# E-Mail-Versand von der eigenen Domain (Einladungen, Anmelde-Codes).
# Die DNS-Einträge (SPF, DKIM, DMARC, MX) gibt outputs.tf aus; sie werden
# beim DNS-Anbieter eingetragen. Erst danach wird die Domain "checked".

resource "scaleway_tem_domain" "main" {
  name       = var.base_domain
  accept_tos = true
}
