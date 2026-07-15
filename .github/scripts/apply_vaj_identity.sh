#!/usr/bin/env bash
# Apply VAJ edition Android package identity (io.vaj.tl) for CI builds.
# Safe to re-run: no-ops when already on VAJ identity.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PACKAGE_NAME="${VAJ_PACKAGE_NAME:-io.vaj.tl}"
CONSTANTS="$ROOT/termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java"
APP_GRADLE="$ROOT/app/build.gradle"

if [[ ! -f "$CONSTANTS" || ! -f "$APP_GRADLE" ]]; then
  echo "error: expected TermuxConstants.java and app/build.gradle under $ROOT" >&2
  exit 1
fi

if grep -qE "TERMUX_PACKAGE_NAME = \"${PACKAGE_NAME}\"" "$CONSTANTS"; then
  echo "VAJ identity already present in TermuxConstants (${PACKAGE_NAME})"
else
  echo "Patching TermuxConstants.TERMUX_PACKAGE_NAME -> ${PACKAGE_NAME}"
  sed -i -E \
    "s/(public static final String TERMUX_PACKAGE_NAME = \")[^\"]+(\";)/\1${PACKAGE_NAME}\2/" \
    "$CONSTANTS"
fi

if grep -qE "applicationId[[:space:]]+\"${PACKAGE_NAME}\"" "$APP_GRADLE"; then
  echo "applicationId already ${PACKAGE_NAME}"
elif grep -qE 'applicationId[[:space:]]+"' "$APP_GRADLE"; then
  echo "Rewriting applicationId -> ${PACKAGE_NAME}"
  sed -i -E "s/applicationId[[:space:]]+\"[^\"]+\"/applicationId \"${PACKAGE_NAME}\"/" "$APP_GRADLE"
else
  echo "Inserting applicationId \"${PACKAGE_NAME}\" into defaultConfig"
  python3 - <<PY
from pathlib import Path
path = Path(${APP_GRADLE@Q})
text = path.read_text()
needle = "defaultConfig {"
idx = text.find(needle)
if idx < 0:
    raise SystemExit("defaultConfig { not found in app/build.gradle")
insert_at = idx + len(needle)
addition = "\n        applicationId \"${PACKAGE_NAME}\""
path.write_text(text[:insert_at] + addition + text[insert_at:])
PY
fi

echo "Setting manifestPlaceholders TERMUX_PACKAGE_NAME -> ${PACKAGE_NAME}"
sed -i -E \
  "s/(TERMUX_PACKAGE_NAME[[:space:]]*:[[:space:]]*\")[^\"]+(\")/\1${PACKAGE_NAME}\2/" \
  "$APP_GRADLE"

grep -qE "TERMUX_PACKAGE_NAME = \"${PACKAGE_NAME}\"" "$CONSTANTS" || {
  echo "error: TermuxConstants patch failed" >&2
  exit 1
}
grep -qE "applicationId[[:space:]]+\"${PACKAGE_NAME}\"" "$APP_GRADLE" || {
  echo "error: applicationId patch failed" >&2
  exit 1
}
grep -qE "TERMUX_PACKAGE_NAME[[:space:]]*:[[:space:]]*\"${PACKAGE_NAME}\"" "$APP_GRADLE" || {
  echo "error: manifestPlaceholders patch failed" >&2
  exit 1
}

echo "VAJ identity ready: ${PACKAGE_NAME}"
