#!/usr/bin/env bash
# 128-bit coordinate invariant audit (S0.04).
#
# Default mode prints counts and exits 0. It is the V0 check and is not a gate.
# --strict lists findings and exits 1 while any gating finding remains.
# Strict mode is the S10.08 cleanup gate.
#
# Source this file to use compile_files. Sourcing defines that function only;
# it does not run the audit and does not change shell options.

compile_files() {
  mkdir -p ../128bit-migration/logs
  ./gradlew compileJava > ../128bit-migration/logs/compile.log 2>&1 || true
  sed -nE 's#^.*(src/main/java/[^:]+\.java):[0-9]+: error:.*#\1#p' \
    ../128bit-migration/logs/compile.log | sort -u
}

if [[ "${BASH_SOURCE[0]}" != "$0" ]]; then
  return 0 2>/dev/null || exit 0
fi

set -u

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT"

STRICT=0
case "${1:-}" in
  "") ;;
  --strict) STRICT=1 ;;
  -h|--help)
    echo "usage: bash tools/check_128bit_invariants.sh [--strict]"
    exit 0
    ;;
  *)
    echo "unknown argument: $1" >&2
    echo "usage: bash tools/check_128bit_invariants.sh [--strict]" >&2
    exit 2
    ;;
esac

if ! command -v rg >/dev/null 2>&1; then
  echo "rg is required" >&2
  exit 2
fi

SCAN=(src/main/java src/test/java)
WORK=$(mktemp -d "${TMPDIR:-/tmp/opencode}/128bit-audit.XXXXXXXX")
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

# toDoubleSaturated() outside debug, F3, and render-mirror files.
# Tests deliberately exercise lossy conversion and legacy boundaries.
search "$WORK/saturated.raw" src/main/java -- -e 'toDoubleSaturated[[:space:]]*\('
: > "$WORK/saturated"
while IFS= read -r hit || [[ -n "${hit:-}" ]]; do
  [[ -z "$hit" ]] && continue
  path=${hit%%:*}
  case "$path" in
    *DebugScreenOverlay.java|*client/renderer/debug/*|*RenderMirror*|*render/mirror/*) ;;
    *) printf '%s\n' "$hit" >> "$WORK/saturated" ;;
  esac
done < "$WORK/saturated.raw"

# Every production BigInteger/BigDecimal mention is gated unless its exact
# matched text has a reviewed cold-boundary fingerprint below.
search "$WORK/big.raw" src/main/java/net/minecraft -- -e 'BigInteger' -e 'BigDecimal'
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
  path=${hit%%:*}
  text=${hit#*:*:}
  case "$path" in
    src/main/java/net/minecraft/util/Mth.java|\
    src/main/java/net/minecraft/util/profiling/ActiveProfiler.java|\
    src/main/java/net/minecraft/commands/synchronization/brigadier/LongArgumentInfo.java|\
    src/main/java/net/minecraft/client/renderer/RunningTrimmedMean.java|\
    src/main/java/net/minecraft/world/entity/AnimationState.java|\
    src/main/java/net/minecraft/world/entity/ai/memory/ExpirableValue.java|\
    src/main/java/net/minecraft/client/gui/screens/Screen.java|\
    src/main/java/net/minecraft/server/level/progress/LoggerChunkProgressListener.java|\
    src/main/java/net/minecraft/world/level/biome/Climate.java|\
    src/main/java/net/minecraft/world/ticks/LevelTicks.java)
      continue
      ;;
  esac
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
  case "$path" in
    src/main/java/net/minecraft/core/Direction.java) continue ;;
  esac
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

# S10.08 also requires SpatialLongSet gone or local-only. The class declaration
# and any use outside that file are findings; self-references are not.
search "$WORK/spatial.raw" src/main/java -- -e 'SpatialLongSet'
: > "$WORK/spatial"
while IFS= read -r hit || [[ -n "${hit:-}" ]]; do
  [[ -z "$hit" ]] && continue
  path=${hit%%:*}
  text=${hit#*:*:}
  if [[ $path == src/main/java/net/minecraft/world/level/lighting/SpatialLongSet.java ]]; then
    [[ $text == *'class SpatialLongSet'* ]] || continue
  fi
  printf '%s\n' "$hit" >> "$WORK/spatial"
done < "$WORK/spatial.raw"

# Suspicious authoritative coordinate mirrors and low-only key construction.
# Hi/Lo fields are intentional; bare X/Z fields must not silently become longs.
search "$WORK/mirror" src/main/java/net/minecraft -- \
  -e '(private|protected|public)[[:space:]]+(static[[:space:]]+)?(final[[:space:]]+)?long[[:space:]]+(x|z|blockX|blockZ|chunkX|chunkZ|sectorX|sectorZ)[[:space:]]*[;=]' \
  -e '(new[[:space:]]+(BlockPos|ChunkPos|SectionPos)|\.put)[[:space:]]*\([^;]*\.toLongLow[[:space:]]*\('

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
  done < tools/128bit_audit_allowlist.tsv
}
: > "$WORK/reviewed"
reviewed_filter double "$WORK/double"
reviewed_filter saturated "$WORK/saturated"
reviewed_filter big "$WORK/big.gate"
reviewed_filter sentinel "$WORK/sentinel"
reviewed_filter region "$WORK/region"
reviewed_filter mirror "$WORK/mirror"

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

COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo unknown)
MODE=report
[[ "$STRICT" -eq 1 ]] && MODE=strict

echo "128bit-invariant-audit"
echo "commit $COMMIT"
echo "mode $MODE"
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

# Gate: every listed category except cold BigInteger/BigDecimal notes.
GATE=$((PACKED + DOUBLE + SATURATED + BIG_GATE + SENTINEL + LONGMAP + REGION + SPATIAL + MIRROR))
echo "gating-findings $GATE"

if [[ "$STRICT" -eq 1 && "$GATE" -ne 0 ]]; then
  exit 1
fi
exit 0
