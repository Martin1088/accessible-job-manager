# Eine Managed Database for PostgreSQL (DB-DEV-S) mit den Datenbanken ajm und pocketid.
# Sie läuft immer, zum Festpreis, ohne Kaltstart, und ist nur im Private Network erreichbar.

resource "random_password" "db_admin" {
  length      = 32
  special     = true
  min_upper   = 1
  min_lower   = 1
  min_numeric = 1
  min_special = 1
  # Nur Zeichen, die in einer URL ohne Ärger durchgehen; urlencode() deckt den Rest.
  override_special = "-_.~"
}

resource "random_password" "db_ajm" {
  length           = 32
  special          = true
  min_upper        = 1
  min_lower        = 1
  min_numeric      = 1
  min_special      = 1
  override_special = "-_.~"
}

resource "random_password" "db_pocketid" {
  length           = 32
  special          = true
  min_upper        = 1
  min_lower        = 1
  min_numeric      = 1
  min_special      = 1
  override_special = "-_.~"
}

resource "scaleway_rdb_instance" "main" {
  name               = var.prefix
  node_type          = "DB-DEV-S"
  engine             = "PostgreSQL-16" # wie dev/ und Testcontainers
  is_ha_cluster      = false
  disable_backup     = true # bewusst, siehe docs/scaleway.md "Löschen und Aufbewahrung"
  encryption_at_rest = true

  volume_type       = "sbs_5k"
  volume_size_in_gb = 10

  user_name = "admin"
  password  = random_password.db_admin.result

  # Nur privat: Ohne load_balancer {} gibt es keinen öffentlichen Endpunkt.
  private_network {
    pn_id       = scaleway_vpc_private_network.main.id
    enable_ipam = true
  }

  tags = local.tags

  lifecycle {
    prevent_destroy = true
  }
}

resource "scaleway_rdb_database" "ajm" {
  instance_id = scaleway_rdb_instance.main.id
  name        = "ajm"
}

resource "scaleway_rdb_database" "pocketid" {
  instance_id = scaleway_rdb_instance.main.id
  name        = "pocketid"
}

resource "scaleway_rdb_user" "ajm" {
  instance_id = scaleway_rdb_instance.main.id
  name        = "ajm"
  password    = random_password.db_ajm.result
  is_admin    = false
}

resource "scaleway_rdb_user" "pocketid" {
  instance_id = scaleway_rdb_instance.main.id
  name        = "pocketid"
  password    = random_password.db_pocketid.result
  is_admin    = false
}

# "all": Hibernate (ddl-auto: update) und Pocket ID (Migrationen) legen Tabellen an.
# Jeder Nutzer bekommt nur seine eigene Datenbank.
resource "scaleway_rdb_privilege" "ajm" {
  instance_id   = scaleway_rdb_instance.main.id
  user_name     = scaleway_rdb_user.ajm.name
  database_name = scaleway_rdb_database.ajm.name
  permission    = "all"
}

resource "scaleway_rdb_privilege" "pocketid" {
  instance_id   = scaleway_rdb_instance.main.id
  user_name     = scaleway_rdb_user.pocketid.name
  database_name = scaleway_rdb_database.pocketid.name
  permission    = "all"
}

locals {
  db_ip   = scaleway_rdb_instance.main.private_network[0].ip
  db_port = scaleway_rdb_instance.main.private_network[0].port

  ajm_jdbc_url = "jdbc:postgresql://${local.db_ip}:${local.db_port}/ajm?sslmode=require"

  pocketid_db_url = format(
    "postgres://%s:%s@%s:%d/pocketid?sslmode=require",
    scaleway_rdb_user.pocketid.name,
    urlencode(random_password.db_pocketid.result),
    local.db_ip,
    local.db_port,
  )
}
