#!/usr/bin/env bash
# Isolated fixtures; never mutate the real source tree.
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORK=$(mktemp -d "${TMPDIR:-/tmp/opencode}/1088bit-audit-test.XXXXXXXX")
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/tools" "$WORK/src/main/java/net/minecraft/core" "$WORK/src/test/java"
cp "$ROOT/tools/check_1088bit_invariants.sh" "$ROOT/tools/1088bit_audit_allowlist.tsv" "$WORK/tools/"
audit() { bash "$WORK/tools/check_1088bit_invariants.sh" --strict > "$WORK/result"; }
fixture="$WORK/src/main/java/net/minecraft/core/Fixture.java"
reject() {
  printf '%s\n' "$2" > "$fixture"
  if audit; then echo "audit accepted forbidden fixture: $2" >&2; exit 1; fi
  grep -Eq "^$1 [1-9][0-9]*$" "$WORK/result"
}
audit
reject legacy-coordinate-residue 'Int128Map value;'
reject legacy-hi-lo-access 'long n = pos.getXHi();'
reject legacy-hi-lo-access 'long n = pos.hi();'
reject packed-world-apis 'public long asLong() { return 0; }'
reject double-xz-casts 'double d = (double) pos.getX();'
reject to-double-saturated 'double d = pos.getX().toDoubleSaturated();'
reject biginteger-bigdecimal 'java.math.BigInteger hot;'
reject long-min-max-sentinels 'long missing = Long.MIN_VALUE;'
reject coordinate-long-maps 'import it.unimi.dsi.fastutil.longs.Long2ObjectMap; // BlockPos'
reject decimal-region-names 'String name = "r." + x + "." + z + ".mca";'
reject spatial-long-set 'class SpatialLongSet {}'
reject coordinate-long-mirrors 'public long chunkX;'
reject low-limb-identity 'map.put(pos.lowLimb(), value);'
reject wide-wrapping-arithmetic 'Int1088 x; void f() { x.add(other); }'
printf '// pPos.asLong();\n' > "$fixture"
audit
# A fingerprint covers all matched lines in a file, not the file itself.
printf 'long missing = Long.MIN_VALUE;\n' > "$fixture"
fingerprint=$(printf 'long missing = Long.MIN_VALUE;\n' | sha256sum)
printf 'sentinel\tsrc/main/java/net/minecraft/core/Fixture.java\t%s\treviewed fixture\n' "${fingerprint%% *}" >> "$WORK/tools/1088bit_audit_allowlist.tsv"
audit
printf '\nlong another = Long.MAX_VALUE;\n' >> "$fixture"
if audit; then echo 'changed reviewed fixture accepted' >&2; exit 1; fi
grep -Eq '^long-min-max-sentinels [1-9]' "$WORK/result"
# The command-only big-number exception is also an exact matched-text set.
printf 'java.math.BigInteger commandValue;\n' > "$fixture"
fingerprint=$(printf 'java.math.BigInteger commandValue;\n' | sha256sum)
printf 'big\tsrc/main/java/net/minecraft/core/Fixture.java\t%s\treviewed command fixture\n' "${fingerprint%% *}" >> "$WORK/tools/1088bit_audit_allowlist.tsv"
audit
printf 'java.math.BigDecimal unreviewed;\n' >> "$fixture"
if audit; then echo 'changed reviewed big-number fixture accepted' >&2; exit 1; fi
grep -Eq '^biginteger-bigdecimal [1-9]' "$WORK/result"
# Report mode is non-gating and saves its report.
bash "$WORK/tools/check_1088bit_invariants.sh" > "$WORK/result"
grep -q '^1088bit-invariant-audit' "$WORK/docs/1088bit/invariant-report.txt"
echo '1088bit invariant audit regression fixtures passed'
