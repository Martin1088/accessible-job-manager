#!/usr/bin/env bash
# Copies an AJM image from GHCR into the Scaleway registry, sets it as app_image
# in pilot/pilot.tfvars and shows the plan.
#
#   deploy/scaleway/push-image.sh sha-a69c2ed          # copy, update tfvars, plan
#   deploy/scaleway/push-image.sh sha-a69c2ed --apply  # ... and apply the plan
set -euo pipefail

tag="${1:?usage: push-image.sh <tag, e.g. sha-a69c2ed> [--apply]}"
apply="${2:-}"

src="ghcr.io/martin1088/accessible-job-manager:${tag}"
dst="rg.fr-par.scw.cloud/ajm-pilot/accessible-job-manager-pilot:${tag}"
pilot="$(cd "$(dirname "$0")/pilot" && pwd)"

docker pull --platform linux/amd64 "$src"
docker tag "$src" "$dst"
docker push "$dst"

sed -i -E "s|^(app_image[[:space:]]*=[[:space:]]*).*|\1\"${dst}\"|" "$pilot/pilot.tfvars"
grep -n '^app_image' "$pilot/pilot.tfvars"

cd "$pilot"
tofu plan -var-file=pilot.tfvars -out=plan.tfplan

if [ "$apply" = "--apply" ]; then
  tofu apply plan.tfplan
else
  echo "Plan saved. Apply with: cd $pilot && tofu apply plan.tfplan"
fi
