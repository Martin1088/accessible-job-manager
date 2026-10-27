provider "scaleway" {
  region     = var.region
  zone       = "${var.region}-1"
  project_id = var.project_id
}
