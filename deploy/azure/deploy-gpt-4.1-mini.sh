#!/usr/bin/env bash
# One-off helper: deploys gpt-4.1-mini on ajm-openai. gpt-4o-mini was rejected
# with ServiceModelDeprecating (no new deployments allowed); gpt-4.1-mini is
# Legacy (not Deprecating) and has capacity on GlobalStandard in
# germanywestcentral - see check-openai-capacity.sh output.
set -euo pipefail

az cognitiveservices account deployment create \
  -n ajm-openai \
  -g ajm-demo \
  --deployment-name gpt-4.1-mini \
  --model-name gpt-4.1-mini \
  --model-version 2025-04-14 \
  --model-format OpenAI \
  --sku-capacity 10 \
  --sku-name GlobalStandard
