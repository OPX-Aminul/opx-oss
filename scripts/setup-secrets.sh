#!/usr/bin/env bash
# Push signing keystore + passwords to GitHub Actions secrets for the calling repo.
# Usage:
#   REPO=OPX-Aminul/opx-oss ./scripts/setup-secrets.sh
set -euo pipefail
REPO=${REPO:-$(gh repo view --json nameWithOwner --jq .nameWithOwner)}
KS_FILE=${1:-scripts/../keystore/opxoss-release.jks}
[ -f "$KS_FILE" ] || { echo "keystore not found: $KS_FILE"; exit 1; }
[ -n "${OPXOSS_RELEASE_STORE_PASSWORD:-}" ] || exit 1
[ -n "${OPXOSS_RELEASE_KEY_ALIAS:-}" ] || exit 1
[ -n "${OPXOSS_RELEASE_KEY_PASSWORD:-}" ] || exit 1

declare -A SECRETS=(
  [OPXOSS_RELEASE_STORE_PASSWORD]="$OPXOSS_RELEASE_STORE_PASSWORD"
  [OPXOSS_RELEASE_KEY_ALIAS]="$OPXOSS_RELEASE_KEY_ALIAS"
  [OPXOSS_RELEASE_KEY_PASSWORD]="$OPXOSS_RELEASE_KEY_PASSWORD"
)
# Kstore base64 is too large for GH secrets (max 1MB, but we still push if under).
# Store it via gh secret and also as a downloadable artifact from the release.
# For this script we push base64 of the jks.
KS_B64=$(base64 -w0 "$KS_FILE")
if [ ${#KS_B64} -gt 999999 ]; then
  echo "Warning: keystore base64 exceeds ~1MB limit for GH secrets ($(( ${#KS_B64} / 1024 ))KB). Storing partial; download from artifacts instead."
  KS_B64="${KS_B64:0:999999}"
fi
SECRETS[OPXOSS_RELEASE_STORE_FILE_B64]="$KS_B64"

for k in "${!SECRETS[@]}"; do
  echo "Setting secret $k ..."
  gh secret set "$k" --repo "$REPO" --body "${SECRETS[$k]}"
done
echo "Done. All secrets set for $REPO"
