# Private Network für die Managed Database. App und Pocket ID hängen sich per
# private_network_id ein; die Datenbank hat keinen öffentlichen Endpunkt.

resource "scaleway_vpc" "main" {
  name = var.prefix
  tags = local.tags
}

resource "scaleway_vpc_private_network" "main" {
  name   = var.prefix
  vpc_id = scaleway_vpc.main.id
  tags   = local.tags
}
