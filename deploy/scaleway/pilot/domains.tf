# Eigene Domains binden. count schaltet eine Ressource an (1) oder aus (0).
# Scaleway prüft beim Anlegen, ob der CNAME schon auf den Container zeigt.
# Deshalb je Domain zweistufig:
#   1. CNAME aus dem passenden Output beim DNS-Anbieter eintragen
#   2. Schalter auf true setzen und anwenden
#
# Pocket ID zuerst: Passkeys werden an die Domain aus APP_URL gebunden.
# Das Admin-Konto deshalb erst unter https://id.<domain> anlegen.

resource "scaleway_container_domain" "pocketid" {
  count        = var.enable_id_domain ? 1 : 0
  container_id = scaleway_container.pocketid.id
  hostname     = local.id_host
}

resource "scaleway_container_domain" "app" {
  count        = var.enable_app_domain ? 1 : 0
  container_id = scaleway_container.app.id
  hostname     = local.app_host
}
