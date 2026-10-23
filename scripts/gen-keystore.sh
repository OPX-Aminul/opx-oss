#!/bin/bash
# Generate a signing keystore if none exists at KEYSRC, write to DEST (base64).
# Usage:
#   ./scripts/gen-keystore.sh [keystore.jks] [dest-base64.txt]
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
KS=${1:-$HERE/../keystore/opxoss-release.jks}
DEST=${2:-}
STOREPASS=${OPXOSS_RELEASE_STORE_PASSWORD:-$(openssl rand -base64 16)}
KEYPASS=${OPXOSS_RELEASE_KEY_PASSWORD:-$(openssl rand -base64 16)}
ALIAS=${OPXOSS_RELEASE_KEY_ALIAS:-opxoss}
STORAGE=${OPXOSS_RELEASE_STORE_PASSWORD:-"$STOREPASS"}

mkdir -p "$(dirname "$KS")"
if [ ! -f "$KS" ]; then
  keytool -genkeypair \
    -keystore "$KS" \
    -storepass "$STOREPASS" \
    -keypass "$KEYPASS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias "$ALIAS" \
    -dname "CN=opxOSS, OU=Dev, O=OPX, L=Dhaka, ST=Dhaka, C=BD"
  echo "Generated $KS"
fi

if [ -n "$DEST" ]; then
  base64 -w0 "$KS" > "$DEST"
  echo "Wrote base64 to $DEST"
fi

echo "KS_ALIAS=$ALIAS"
echo "KS_PASSWORD=$KEYPASS"
echo "STORE_PASSWORD=$STOREPASS"
