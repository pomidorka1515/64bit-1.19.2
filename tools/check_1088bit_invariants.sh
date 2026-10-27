#!/usr/bin/env bash
# 1088-bit coordinate invariant audit.
#
# Default mode prints counts and exits 0. It is the V0 check and is not a gate.
# --strict lists findings and exits 1 while any gating finding remains.
# Strict mode is the S11 cleanup gate.
#
set -u

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"
mkdir -p docs/1088bit
exec > >(tee docs/1088bit/invariant-report.txt)

STRICT=0
case "${1:-}" in
  "") ;;
  --strict) STRICT=1 ;;
  -h|--help)
    echo "usage: bash tools/check_1088bit_invariants.sh [--strict]"
    exit 0
    ;;
  *)
    echo "unknown argument: $1" >&2
    echo "usage: bash tools/check_1088bit_invariants.sh [--strict]" >&2
    exit 2
    ;;
esac

if ! command -v rg >/dev/null 2>&1; then
  echo "rg is required" >&2
  exit 2
fi

SCAN=(src/main/java src/test/java)
WORK=$(mktemp -d "${TMPDIR:-/tmp/opencode}/1088bit-audit.XXXXXXXX")
trap 'rm -rf "$WORK"' EXIT

# Drop comment-only lines. Mid-line comments are left intact.
strip_comments() {
  awk -F: 'NF < 3 { next }
    {
      text = $0
      sub(/^[^:]+:[0-9]+:/, "", text)
      if (text ~ /^[[:space:]]*(\/\/|\*|\/\*)/) next
      print
    }'
}

# search OUT [ROOT...] -- PATTERNS...
# Roots default to src/main/java and src/test/java. Patterns follow a lone --.
search() {
  local out=$1
  shift
  local roots=()
  while [[ $# -gt 0 && $1 != -- ]]; do
    roots+=("$1")
    shift
  done
  [[ ${1:-} == -- ]] && shift
  if [[ ${#roots[@]} -eq 0 ]]; then
    roots=("${SCAN[@]}")
  fi
  : > "$out"
  local root
  for root in "${roots[@]}"; do
    [[ -d $root ]] || continue
    rg -n --glob '*.java' "$@" "$root" 2>/dev/null | strip_comments >> "$out" || true
  done
}

# packed world APIs: definitions, static packed call sites, and pack masks.
# \b keeps pBlockPos.getX() out of this check. Section-local short packing
# (sectionRelative*) is intentionally not included. A single-argument
# new ChunkPos(...) is not listed: it is ambiguous with new ChunkPos(BlockPos).
search "$WORK/packed.raw" -- \
  -e '\b(asPackedLong|toPackedLong)[[:space:]]*\(' \
  -e '\b(BlockPos|ChunkPos|SectionPos)\.asLong[[:space:]]*\(' \
  -e '\b(block[Pp]os|chunk[Pp]os|section[Pp]os|pPos)\.asLong[[:space:]]*\(' \
  -e '\b(public|protected)[^;{]*\blong[[:space:]]+asLong[[:space:]]*\(' \
  -e '\bSectionPos\.(of|x|y|z)[[:space:]]*\([[:space:]]*(pPacked|packed)' \
  -e '\bBlockPos\.(getX|getY|getZ|getFlatIndex|of)\(' \
  -e 'getX\(long pPackedPos\)' \
  -e 'getY\(long pPackedPos\)' \
  -e 'getZ\(long pPackedPos\)' \
  -e 'of\(long pPackedPos\)' \
  -e 'getFlatIndex\(long pPackedPos\)' \
  -e 'set\(long pPackedPos\)' \
  -e 'ChunkPos\(long pPackedPos\)' \
  -e 'getX\(long pChunkAsLong\)' \
  -e 'getZ\(long pChunkAsLong\)' \
  -e 'blockToSection\(long' \
  -e 'aroundAndAtBlockPos\(long pPos,' \
  -e 'PACKED_(X|Y|Z)_(LENGTH|MASK)'
sort -u "$WORK/packed.raw" > "$WORK/packed"

# (double) casts whose operand is an X/Z accessor, or a parenthesized expression
# that reads one. Y-only casts and GUI/vector .x() fields are not this check.
search "$WORK/double.raw" -- \
  -e '\(double\)[[:space:]]*\(?[[:space:]]*([A-Za-z_][A-Za-z0-9_]*\.)*(getX|getZ|blockX|blockZ|minBlockX|maxBlockX|minBlockZ|maxBlockZ)[[:space:]]*\(' \
  -e '\(double\)[[:space:]]*\([^)\n]{0,80}(\.getX|\.getZ|\.blockX|\.blockZ)[[:space:]]*\('
sort -u "$WORK/double.raw" > "$WORK/double"

# Lossy conversion sites are reviewed by exact fingerprint, not file exemptions.
search "$WORK/saturated" src/main/java -- -e 'toDoubleSaturated[[:space:]]*\('

# Every production BigInteger/BigDecimal mention is gated unless its exact
# matched text has a reviewed cold-boundary fingerprint below.
search "$WORK/big.raw" src/main/java -- -e 'BigInteger' -e 'BigDecimal'
: > "$WORK/big"
: > "$WORK/big.gate"
while IFS= read -r hit || [[ -n "${hit:-}" ]]; do
  [[ -z "$hit" ]] && continue
  printf '%s\n' "$hit" >> "$WORK/big"
  printf '%s\n' "$hit" >> "$WORK/big.gate"
done < "$WORK/big.raw"

# Coordinate sentinels under net.minecraft. Time, UUID, profiler, brigadier,
# narration, and climate search distances are not coordinate sentinels.
# Overflow ceilings (Long.MAX_VALUE as a saturating product cap) and bit masks
# are not sentinels either. A wrapped "? Long.MAX_VALUE" continuation of such
# a ceiling is excluded; a ternary that itself chooses MIN/MAX is kept.
search "$WORK/sentinel.raw" src/main/java/net/minecraft -- \
  -e 'Long\.(MIN_VALUE|MAX_VALUE)'
: > "$WORK/sentinel"
while IFS= read -r hit || [[ -n "${hit:-}" ]]; do
  [[ -z "$hit" ]] && continue
  text=${hit#*:*:}
  case "$text" in
    *'Long.MAX_VALUE /'*|*' > Long.MAX_VALUE'*|*'>= Long.MAX_VALUE'*|*'>= (double) Long.MAX_VALUE'*)
      continue
      ;;
  esac
  if [[ "$text" =~ ^[[:space:]]*\?[[:space:]]*Long\.(MIN_VALUE|MAX_VALUE) ]]; then
    continue
  fi
  if [[ "$text" == *'& Long.MIN_VALUE'* || "$text" == *'& Long.MAX_VALUE'* \
     || "$text" == *'| Long.MIN_VALUE'* || "$text" == *'| Long.MAX_VALUE'* ]]; then
    continue
  fi
  printf '%s\n' "$hit" >> "$WORK/sentinel"
done < "$WORK/sentinel.raw"

# Imports of long maps/sets in files that also name a world position type.
# Direction's unused import keys normals, not chunks, so it is excluded.
search "$WORK/longmap.raw" -- \
  -e 'import it\.unimi\.dsi\.fastutil\.longs\.Long2Object' \
  -e 'import it\.unimi\.dsi\.fastutil\.longs\.LongOpenHashSet'
: > "$WORK/longmap"
while IFS= read -r hit || [[ -n "${hit:-}" ]]; do
  [[ -z "$hit" ]] && continue
  path=${hit%%:*}
  if rg -q 'BlockPos|ChunkPos|SectionPos|ColumnPos' "$path"; then
    printf '%s\n' "$hit" >> "$WORK/longmap"
  fi
done < "$WORK/longmap.raw"

search "$WORK/region.raw" -- \
  -e '"r\."[[:space:]]*\+' \
  -e '"c\."[[:space:]]*\+' \
  -e 'r\.%d[^"]*\.mca' \
  -e 'c\.%d[^"]*\.mcc' \
  -e '\(-\?\[0-9\]\+\)'
sort -u "$WORK/region.raw" > "$WORK/region"

# The declaration and every use of the obsolete spatial container are findings.
search "$WORK/spatial" src/main/java -- -e '\bSpatialLongSet\b'

# Suspicious authoritative coordinate mirrors and low-only key construction.
# Hi/Lo fields are intentional; bare X/Z fields must not silently become longs.
search "$WORK/mirror" src/main/java/net/minecraft -- \
  -e '(private|protected|public)[[:space:]]+(static[[:space:]]+)?(final[[:space:]]+)?long[[:space:]]+(x|z|blockX|blockZ|chunkX|chunkZ|sectorX|sectorZ)[[:space:]]*[;=]' \
  -e '(new[[:space:]]+(BlockPos|ChunkPos|SectionPos)|\.put)[[:space:]]*\([^;]*\.toLongLow[[:space:]]*\('

# Legacy types and coordinate accessors must disappear after the type flip.
search "$WORK/legacy" src/main/java -- -e '\bInt128[A-Za-z0-9_]*\b'
search "$WORK/hilo" src/main/java -- \
  -e '\b(xHi|xLo|zHi|zLo|getXHi|getXLo|getZHi|getZLo)\b' \
  -e '\.(hi|lo)[[:space:]]*\('

# Narrowing is suspicious when used for a key, bounds, or persisted value.
search "$WORK/low" src/main/java -- \
  -e '(put|add|contains|remove|key|bound|write|save|serialize|setLong|LongArray)[^;]*(\.lowLimb|\.toLongLow)[[:space:]]*\(' \
  -e '(\.lowLimb|\.toLongLow)[[:space:]]*\([^;]*(key|bound|write|save|serialize)'
# Match explicit Int1088 receivers, including variables declared as Int1088.
search "$WORK/wrapping" src/main/java -- \
  -e '\bInt1088\.(add|subtract)[[:space:]]*\('
while IFS= read -r path; do
  [[ -z "$path" ]] && continue
  names=$(sed -nE 's/.*\bInt1088[[:space:]]+([A-Za-z_][A-Za-z0-9_]*).*/\1/p' "$path" | sort -u)
  while IFS= read -r name; do
    [[ -z "$name" ]] && continue
    rg -n -- "\b${name}\.(add|subtract)[[:space:]]*\(" "$path" | sed "s#^#$path:#" | strip_comments >> "$WORK/wrapping" || true
  done <<< "$names"
done < <(rg -l --glob '*.java' '\bInt1088[[:space:]]+[A-Za-z_]' src/main/java || true)
sort -u "$WORK/wrapping" -o "$WORK/wrapping"

# Reviewed exceptions are fingerprints of the *matched source text*, not file
# exemptions. A new/changed hit in the same file invalidates the whole exception.
# Line movement alone is harmless. Keep reasons in the committed manifest and
# keep exempted hits visible in strict output. Never auto-refresh this manifest.
reviewed_filter() {
  local category=$1 file=$2 path fingerprint reason actual
  while IFS=$'\t' read -r entry path fingerprint reason; do
    [[ $entry == "$category" ]] || continue
    awk -F: -v path="$path" '$1 == path { sub(/^[^:]+:[0-9]+:/, ""); print }' "$file" \
      | LC_ALL=C sort -u > "$WORK/review-text"
    [[ -s "$WORK/review-text" ]] || continue
    actual=$(sha256sum "$WORK/review-text")
    [[ ${actual%% *} == "$fingerprint" ]] || continue
    printf '%s: %s\n' "$category/$path" "$reason" >> "$WORK/reviewed"
    awk -F: -v path="$path" '$1 != path' "$file" > "$WORK/filtered"
    mv "$WORK/filtered" "$file"
  done < tools/1088bit_audit_allowlist.tsv
}
: > "$WORK/reviewed"
reviewed_filter double "$WORK/double"
reviewed_filter saturated "$WORK/saturated"
# BigInteger/BigDecimal are unconditionally gated.
reviewed_filter sentinel "$WORK/sentinel"
reviewed_filter region "$WORK/region"
reviewed_filter mirror "$WORK/mirror"
reviewed_filter legacy "$WORK/legacy"
reviewed_filter hilo "$WORK/hilo"
reviewed_filter low "$WORK/low"
reviewed_filter wrapping "$WORK/wrapping"
reviewed_filter packed "$WORK/packed"
reviewed_filter longmap "$WORK/longmap"
reviewed_filter spatial "$WORK/spatial"

count() {
  if [[ -s $1 ]]; then
    wc -l < "$1" | tr -d ' '
  else
    echo 0
  fi
}

PACKED=$(count "$WORK/packed")
DOUBLE=$(count "$WORK/double")
SATURATED=$(count "$WORK/saturated")
BIG=$(count "$WORK/big")
SENTINEL=$(count "$WORK/sentinel")
LONGMAP=$(count "$WORK/longmap")
REGION=$(count "$WORK/region")
SPATIAL=$(count "$WORK/spatial")
BIG_GATE=$(count "$WORK/big.gate")
MIRROR=$(count "$WORK/mirror")
LEGACY=$(count "$WORK/legacy")
HILO=$(count "$WORK/hilo")
LOW=$(count "$WORK/low")
WRAPPING=$(count "$WORK/wrapping")

COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo unknown)
MODE=report
[[ "$STRICT" -eq 1 ]] && MODE=strict

echo "1088bit-invariant-audit"
echo "commit $COMMIT"
echo "mode $MODE"
echo "legacy-coordinate-residue $LEGACY"
echo "legacy-hi-lo-access $HILO"
echo "low-limb-identity $LOW"
echo "wide-wrapping-arithmetic $WRAPPING"
echo "packed-world-apis $PACKED"
echo "double-xz-casts $DOUBLE"
echo "to-double-saturated $SATURATED"
echo "biginteger-bigdecimal $BIG"
echo "long-min-max-sentinels $SENTINEL"
echo "coordinate-long-maps $LONGMAP"
echo "decimal-region-names $REGION"
echo "spatial-long-set $SPATIAL"
echo "coordinate-long-mirrors $MIRROR"

list_section() {
  local title=$1
  local file=$2
  echo "-- $title"
  if [[ -s $file ]]; then
    cat "$file"
  else
    echo "(none)"
  fi
}

if [[ "$STRICT" -eq 1 ]]; then
  list_section reviewed-exceptions "$WORK/reviewed"
  list_section legacy-coordinate-residue "$WORK/legacy"
  list_section legacy-hi-lo-access "$WORK/hilo"
  list_section low-limb-identity "$WORK/low"
  list_section wide-wrapping-arithmetic "$WORK/wrapping"
  list_section packed-world-apis "$WORK/packed"
  list_section double-xz-casts "$WORK/double"
  list_section to-double-saturated "$WORK/saturated"
  list_section biginteger-bigdecimal "$WORK/big"
  list_section long-min-max-sentinels "$WORK/sentinel"
  list_section coordinate-long-maps "$WORK/longmap"
  list_section decimal-region-names "$WORK/region"
  list_section spatial-long-set "$WORK/spatial"
  list_section coordinate-long-mirrors "$WORK/mirror"
fi

# Gate every category, including all production BigInteger/BigDecimal mentions.
GATE=$((LEGACY + HILO + LOW + WRAPPING + PACKED + DOUBLE + SATURATED + BIG_GATE + SENTINEL + LONGMAP + REGION + SPATIAL + MIRROR))
echo "gating-findings $GATE"

if [[ "$STRICT" -eq 1 && "$GATE" -ne 0 ]]; then
  exit 1
fi
exit 0
