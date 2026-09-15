#!/usr/bin/env bash
# One-off helper: checks which OpenAI chat models have deployable capacity in
# a given region. The modelCapacities API requires modelName+modelVersion per
# call, so this loops over a candidate list of models suited for structured
# JSON output extraction. Run as a script (not pasted line-by-line) to avoid
# terminal line-wrap mangling long az commands.
#
# Usage: ./check-openai-capacity.sh [region]
set -euo pipefail

REGION="${1:-germanywestcentral}"
SUB_ID=$(az account show --query id -o tsv)

# name:version pairs - broadly available chat models that support structured
# outputs (response_format json_schema).
CANDIDATES=(
  "gpt-4o-mini:2024-07-18"
  "gpt-4o:2024-08-06"
  "gpt-4o:2024-11-20"
  "gpt-4.1-mini:2025-04-14"
  "gpt-4.1:2025-04-14"
)

echo "Checking OpenAI model capacity in region: $REGION"
printf "%-16s %-14s %-18s %s\n" "MODEL" "VERSION" "SKU" "AVAILABLE"

for pair in "${CANDIDATES[@]}"; do
  name="${pair%%:*}"
  version="${pair##*:}"

  result=$(az rest --method get \
    --url "https://management.azure.com/subscriptions/${SUB_ID}/providers/Microsoft.CognitiveServices/locations/${REGION}/modelCapacities?api-version=2024-06-01-preview&modelFormat=OpenAI&modelName=${name}&modelVersion=${version}" \
    -o json 2>/dev/null) || continue

  echo "$result" | python3 -c "
import json, sys
data = json.load(sys.stdin)
for item in data.get('value', []):
    p = item.get('properties', {})
    avail = p.get('availableCapacity', 0)
    if avail and avail > 0:
        print(f\"{'$name':<16} {'$version':<14} {p.get('skuName',''):<18} {avail}\")
"
done
