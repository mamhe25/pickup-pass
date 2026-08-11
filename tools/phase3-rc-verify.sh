#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fails=0
warns=0
passes=0

pass(){ echo "[PASS] $*"; passes=$((passes+1)); }
fail(){ echo "[FAIL] $*" >&2; fails=$((fails+1)); }
warn(){ echo "[WARN] $*" >&2; warns=$((warns+1)); }

contains(){
  local f="$ROOT/$1"; local needle="$2"; local msg="$3"
  if [[ -f "$f" ]] && grep -Fq -- "$needle" "$f"; then pass "$msg"; else fail "$msg"; fi
}
not_contains(){
  local f="$ROOT/$1"; local needle="$2"; local msg="$3"
  if [[ -f "$f" ]] && ! grep -Fq -- "$needle" "$f"; then pass "$msg"; else fail "$msg"; fi
}

contains "pickup-pass-system/firestore.rules" "affectedKeys().hasOnly(['photoUrl'])" "User self-write is avatar-only"
contains "pickup-pass-system/firestore.rules" "match /pickupTokens/{tokenId}" "Pickup token rules present"
contains "pickup-pass-system/firestore.rules" "allow read, write: if false;" "Pickup tokens are backend-only"
contains "pickup-pass-system/backend/src/main/resources/application.yml" 'enabled: ${BOOTSTRAP_ENABLED:false}' "Bootstrap defaults OFF"
contains "production.env.example" "BOOTSTRAP_ENABLED=false" "Production example keeps bootstrap OFF"
not_contains "pickup-pass-android/app/src/main/res/xml/network_security_config.xml" 'cleartextTrafficPermitted="true"' "Release Android config rejects cleartext"
contains "pickup-pass-android/app/src/debug/res/xml/network_security_config.xml" 'cleartextTrafficPermitted="true"' "Debug local HTTP override present"
contains "pickup-pass-system/backend/Dockerfile" "mvn clean verify -B" "Docker production build runs tests"
not_contains "pickup-pass-system/backend/Dockerfile" "-DskipTests" "Docker production build does not skip tests"

if grep -Eq 'buildConfigField\("String", "API_BASE_URL", "\\"https://' "$ROOT/pickup-pass-android/app/build.gradle.kts"; then
  pass "Default/release API base URL is HTTPS"
else
  fail "Default/release API base URL is not verifiably HTTPS"
fi

python3 - "$ROOT" <<'PY'
import json, pathlib, sys, yaml, xml.etree.ElementTree as ET
root=pathlib.Path(sys.argv[1])
for rel in ["pickup-pass-system/firestore.indexes.json","pickup-pass-system/firebase/firestore.indexes.json"]:
    json.loads((root/rel).read_text())
for rel in ["pickup-pass-system/backend/src/main/resources/application.yml","pickup-pass-system/backend/src/main/resources/application-prod.yml"]:
    yaml.safe_load((root/rel).read_text())
for rel in ["pickup-pass-android/app/src/main/res/xml/network_security_config.xml","pickup-pass-android/app/src/debug/res/xml/network_security_config.xml"]:
    ET.parse(root/rel)
PY
pass "JSON/YAML/XML configuration parses"

dangerous="$(find "$ROOT" -type f \( -name '*.pem' -o -name '*.p12' -o -name '*.jks' -o -name '*.keystore' \) -size +0c -print -quit || true)"
secretfile="$(find "$ROOT/pickup-pass-system/backend/secrets" -type f -size +0c -print -quit 2>/dev/null || true)"
if [[ -n "$dangerous$secretfile" ]]; then
  fail "Non-empty credential/signing material exists in the project tree"
else
  pass "No non-empty credential/signing material found"
fi

if grep -RIl --exclude-dir=build --exclude-dir=target --exclude="phase3-rc-verify.sh" --exclude="phase3-rc-verify.ps1" -- "BEGIN PRIVATE KEY" "$ROOT" >/dev/null 2>&1; then
  fail "Embedded private-key block detected"
else
  pass "No embedded private-key block detected"
fi

echo
echo "PASS=$passes WARN=$warns FAIL=$fails"
if (( fails > 0 )); then exit 1; fi
echo "Release-candidate static gate passed."
