#!/bin/bash
# Generate a signing keystore if none exists at KEYSRC, write to DEST (base64).
# Usage:
#   ./scripts/gen-keystore.sh [keystore.jks] [dest-base64.txt]
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
KS=${1:-$HERE/../keystore/opxoss-release.jks}
DEST=${2:-}
ALIAS=${OPXOSS_RELEASE_KEY_ALIAS:-opxoss}
# For PKCS12 (the keytool default on recent JDK), store and key passwords must match.
# Use a single password for both to avoid "Given final block not properly padded".
PASS=${OPXOSS_RELEASE_STORE_PASSWORD:-$(openssl rand -base64 16)}
STOREPASS="$PASS"
KEYPASS="$PASS"
STORAGE="$PASS"

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
