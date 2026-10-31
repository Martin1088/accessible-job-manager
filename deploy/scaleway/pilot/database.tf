# Zwei getrennte Serverless-SQL-Datenbanken, beide mit min_cpu = 1: sie bleiben
# bewusst warm. Mit min_cpu = 0 dauerte der Kaltstart über eine Minute
# ("Database took more than 1m0s to start, please retry later (SQLSTATE 57P03)").
# Pocket ID antwortete dann mit 500, und die App stürzte beim OIDC-Discovery
# gegen Pocket ID ab (SocketTimeoutException: Read timed out). Das kostet Dauerbetrieb.

resource "scaleway_sdb_sql_database" "ajm" {
  name    = "${var.prefix}-ajm"
  min_cpu = 1
  max_cpu = 1 # the pilot does not need 2 vCPU

  lifecycle {
    prevent_destroy = true
  }
}

resource "scaleway_sdb_sql_database" "pocketid" {
  name    = "${var.prefix}-pocketid"
  min_cpu = 1
  max_cpu = 1

  lifecycle {
    prevent_destroy = true
  }
}

locals {
  # endpoint hat die Form postgres://<host>:5432/<db>?sslmode=require
  ajm_db_hostpath      = trimprefix(scaleway_sdb_sql_database.ajm.endpoint, "postgres://")
  pocketid_db_hostpath = trimprefix(scaleway_sdb_sql_database.pocketid.endpoint, "postgres://")

  # Spring Boot erwartet eine JDBC-URL; Benutzer und Passwort separat.
  ajm_jdbc_url = "jdbc:postgresql://${local.ajm_db_hostpath}"

  # Pocket ID erwartet eine vollständige PostgreSQL-URL mit Zugangsdaten.
  pocketid_db_url = format(
    "postgres://%s:%s@%s",
    scaleway_iam_application.pocketid.id,
    scaleway_iam_api_key.pocketid.secret_key,
    local.pocketid_db_hostpath,
  )
}
