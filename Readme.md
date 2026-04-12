docker run -d \
--name exporter-postgres \
-e POSTGRES_DB=exporter \
-e POSTGRES_USER=exporter \
-e POSTGRES_PASSWORD=exporter \
-p 5432:5432 \
postgres:16
