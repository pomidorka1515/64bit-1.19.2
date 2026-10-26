#!/usr/bin/env bash
# Isolated negative fixtures: never mutate the real source tree.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORK=$(mktemp -d "${TMPDIR:-/tmp/opencode}/128bit-audit-test.XXXXXXXX")
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/tools" "$WORK/src/main/java/net/minecraft/core" "$WORK/src/test/java"
cp "$ROOT/tools/check_128bit_invariants.sh" "$ROOT/tools/128bit_audit_allowlist.tsv" "$WORK/tools/"
audit() { bash "$WORK/tools/check_128bit_invariants.sh" --strict > "$WORK/result"; }
reject() {
  if audit; then echo "audit accepted forbidden fixture: $1" >&2; exit 1; fi
  grep -q "$1" "$WORK/result"
}
audit
fixture="$WORK/src/main/java/net/minecraft/core/Fixture.java"
for code in \
  'public long asLong() { return 0; }' \
  'void use() { pPos.asLong(); }' \
  'double d = (double) pos.getX();' \
  'double d = pos.getX().toDoubleSaturated();' \
  'java.math.BigInteger hot;' \
  'long missing = Long.MIN_VALUE;' \
  'public long chunkX;' \
  'class SpatialLongSet {}' \
  'String name = "r." + x + "." + z + ".mca";'; do
  printf '%s\n' "$code" > "$fixture"
  reject 'gating-findings [1-9]'
done
printf '// pPos.asLong();\n' > "$fixture"
audit
# A reviewed file must reject new hits, even when the old fingerprint remains.
cp "$ROOT/src/main/java/net/minecraft/core/Int128.java" "$WORK/src/main/java/net/minecraft/core/Int128.java"
audit
printf '\nclass Bad { long missing = Long.MIN_VALUE; }\n' >> "$WORK/src/main/java/net/minecraft/core/Int128.java"
reject 'long-min-max-sentinels [1-9]'
echo '128bit invariant audit regression fixtures passed'
