package net.minecraft.world.level.levelgen;

import java.math.BigInteger;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.Registry;
import net.minecraft.core.Int1088;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.synth.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.block.Blocks;

/** Read-only seed-zero density probe; all horizontal inputs remain exact integers. */
public final class TerrainTransitionProbe {
    private static boolean endDimension;

    /** Reuse one NoiseChunk for a full 16-column scan strip. */
    private static java.util.BitSet[] solidStrip(RandomState state, long origin, long other, boolean alongX) {
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(
                endDimension ? NoiseGeneratorSettings.END : NoiseGeneratorSettings.OVERWORLD);
        int w = settings.noiseSettings().getCellWidth(), h = settings.noiseSettings().getCellHeight();
        int min = settings.noiseSettings().minY(), countY = settings.noiseSettings().height() / h;
        long ox = alongX ? origin : Math.floorDiv(other, 16) * 16;
        long oz = alongX ? Math.floorDiv(other, 16) * 16 : origin;
        int fixed = Math.floorMod(other, 16);
        var chunk = new NoiseChunk(16 / w, state, ox, oz, settings.noiseSettings(),
                DensityFunctions.BeardifierMarker.INSTANCE, settings,
                (px, py, pz) -> endDimension ? new Aquifer.FluidStatus(settings.seaLevel(), settings.defaultFluid())
                        : py < -54 ? new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState())
                        : new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState()), Blender.empty());
        var result = new java.util.BitSet[16];
        for (int i = 0; i < 16; i++) result[i] = new java.util.BitSet(384);
        chunk.initializeForFirstCellX();
        int lastX = alongX ? 16 / w - 1 : fixed / w;
        for (int cx = 0; cx <= lastX; cx++) {
            chunk.advanceCellX(cx);
            if (alongX || cx == lastX) {
                for (int cz = alongX ? fixed / w : 0; cz <= (alongX ? fixed / w : 16 / w - 1); cz++) {
                    for (int cy = countY - 1; cy >= 0; cy--) {
                        chunk.selectCellYZ(cy, cz);
                        for (int dy = h - 1; dy >= 0; dy--) {
                            int y = min + cy * h + dy;
                            chunk.updateForY(y, dy / (double) h);
                            for (int dx = alongX ? 0 : fixed % w; dx <= (alongX ? w - 1 : fixed % w); dx++) {
                                chunk.updateForX(ox + cx * w + dx, dx / (double) w);
                                for (int dz = alongX ? fixed % w : 0; dz <= (alongX ? fixed % w : w - 1); dz++) {
                                    chunk.updateForZ(oz + cz * w + dz, dz / (double) w);
                                    var block = chunk.getInterpolatedState();
                                    if (block == null) block = settings.defaultBlock();
                                    if (!block.isAir() && block.getFluidState().isEmpty())
                                        result[alongX ? cx * w + dx : cz * w + dz].set(y + 64);
                                }
                            }
                        }
                    }
                }
            }
            if (cx < lastX) chunk.swapSlices();
        }
        chunk.stopInterpolation();
        return result;
    }
    private static DensityFunction.SinglePointContext point(BigInteger x, int y, BigInteger z) {
        return new DensityFunction.SinglePointContext(Int1088.parseDecimal(x.toString()), y,
                Int1088.parseDecimal(z.toString()));
    }

    private static ResourceKey<DensityFunction> key(String name) {
        return ResourceKey.create(Registry.DENSITY_FUNCTION_REGISTRY, new ResourceLocation("overworld/" + name));
    }

    /** Noise-fill materials, before surface rules, structures, and carvers. */
    private static int highestSolid(RandomState state, BigInteger x, BigInteger z) {
        return solidColumn(state, x, z).length() - 65;
    }

    private static java.util.BitSet solidColumn(RandomState state, BigInteger x, BigInteger z) {
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(
                endDimension ? NoiseGeneratorSettings.END : NoiseGeneratorSettings.OVERWORLD);
        int width = settings.noiseSettings().getCellWidth();
        int height = settings.noiseSettings().getCellHeight();
        int minY = settings.noiseSettings().minY();
        int maxY = minY + settings.noiseSettings().height() - 1;
        BigInteger ox = x.shiftRight(4).shiftLeft(4), oz = z.shiftRight(4).shiftLeft(4);
        var chunk = new NoiseChunk(16 / width,state,Int1088.parseDecimal(ox.toString()),Int1088.parseDecimal(oz.toString()),
                settings.noiseSettings(),DensityFunctions.BeardifierMarker.INSTANCE,settings,
                (px,py,pz) -> endDimension ? new Aquifer.FluidStatus(settings.seaLevel(), settings.defaultFluid())
                        : py < -54 ? new Aquifer.FluidStatus(-54,Blocks.LAVA.defaultBlockState())
                        : new Aquifer.FluidStatus(63,Blocks.WATER.defaultBlockState()),Blender.empty());
        int lx = x.subtract(ox).intValueExact(), lz = z.subtract(oz).intValueExact();
        chunk.initializeForFirstCellX();
        for (int cell = 0; cell <= lx/width; cell++) {
            chunk.advanceCellX(cell);
            if (cell < lx/width) chunk.swapSlices();
        }
        var solids = new java.util.BitSet(384);
        for (int y = maxY; y >= minY; y--) {
            chunk.selectCellYZ((y-minY)/height,lz/width);
            chunk.updateForY(y,Math.floorMod(y, height)/(double)height);
            chunk.updateForX(Int1088.parseDecimal(x.toString()),(lx%width)/(double)width);
            chunk.updateForZ(Int1088.parseDecimal(z.toString()),(lz%width)/(double)width);
            var block = chunk.getInterpolatedState();
            if (block == null) block = settings.defaultBlock();
            if (!block.isAir() && block.getFluidState().isEmpty()) solids.set(y + 64);
        }
        chunk.stopInterpolation();
        return solids;
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        GeneratorMode mode = switch (args.length > 0 ? args[0] : "64bit-stable-shifts") {
            case "ordinary", "64bit" -> GeneratorMode.BIT_64;
            case "32bit" -> GeneratorMode.BIT_32;
            case "32bit-stable-shifts" -> GeneratorMode.BIT_32_STABLE_SHIFTS;
            case "64bit-stable-shifts" -> GeneratorMode.BIT_64_STABLE_SHIFTS;
            default -> throw new IllegalArgumentException("Expected 32bit, 64bit, or a stable-shifts equivalent");
        };
        endDimension = args.length > 1 && args[1].equals("catalog-end-walls");
        try (var ignored = GenerationContext.open(new GeneratorConfig(mode, CoordinatePrecision.PRECISE))) {
            var state = RandomState.create(BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(
                    endDimension ? NoiseGeneratorSettings.END : NoiseGeneratorSettings.OVERWORLD), BuiltinRegistries.NOISE, 0L);
            var caves = state.debugCaves(key("sloped_cheese"));
            if (args.length > 1 && args[1].equals("verify-strip")) {
                for (long x : new long[] {12_550_823, 4_218_498_805L, 67_495_977_369L, 67_495_977_370L, -67_495_991_781L}) {
                    for (boolean axis : new boolean[] {true, false}) {
                        var strip = solidStrip(state, Math.floorDiv(x, 16) * 16, 64, axis)[Math.floorMod(x, 16)];
                        var column = solidColumn(state, BigInteger.valueOf(axis ? x : 64), BigInteger.valueOf(axis ? 64 : x));
                        if (!strip.equals(column)) throw new AssertionError("Strip/column mismatch x=" + x + " axis=" + axis);
                    }
                }
                System.out.println("Strip/independent-column checks passed");
                return;
            }
            if (args.length > 1 && (args[1].equals("catalog-walls") || endDimension)) {
                try {
                    System.out.println("| Input / stack | Scale | Direction | Reference input | Strongest local edge | Other axis | Top before → after | Changed solid blocks | Status |");
                    System.out.println("| --- | ---: | --- | ---: | ---: | ---: | --- | ---: | --- |");
                    for (String line : Files.readAllLines(Path.of(args[2]))) {
                        line = line.replaceAll("\\u001B\\[[0-9;]*m", "");
                        if (line.contains("[STDOUT]: |")) line = line.substring(line.indexOf("|"));
                        if (!line.startsWith("| ") || line.contains("---")) continue;
                        String[] cols = line.split("\\|");
                        if (cols.length != 7 || !cols[3].trim().matches("[0-9,]+")) continue;
                        String name = cols[1].trim();
                        for (int axis = 0; axis < 2; axis++) for (int sign : new int[] {1, -1}) {
                            long reference = Long.parseLong(cols[3 + axis * 2 + (sign < 0 ? 1 : 0)].trim().replace(",", ""));
                            long magnitude = Math.abs(reference);
                            long start = magnitude - 32, end = magnitude + 256;
                            long other = 64;
                            java.util.BitSet previous = null;
                            int previousTop = -65, bestChanged = -1, bestScore = -1, bestBefore = 0, bestAfter = 0;
                            long bestAt = 0;
                            long cachedOrigin = Long.MIN_VALUE;
                            java.util.BitSet[] strip = null;
                            for (long n = start; n <= end; n++) {
                                long along = sign * n, origin = Math.floorDiv(along, 16) * 16;
                                if (origin != cachedOrigin) {
                                    strip = solidStrip(state, origin, other, axis == 0);
                                    cachedOrigin = origin;
                                }
                                var solid = strip[Math.floorMod(along, 16)];
                                int top = solid.length() - 65;
                                if (previous != null) {
                                    var difference = (java.util.BitSet) solid.clone();
                                    difference.xor(previous);
                                    int changed = difference.cardinality();
                                    int score = Math.max(changed, Math.abs(top - previousTop));
                                    if (score > bestScore) {
                                        bestScore = score;
                                        bestChanged = changed; bestAt = sign * n;
                                        bestBefore = previousTop; bestAfter = top;
                                    }
                                }
                                previous = solid; previousTop = top;
                            }
                            System.out.printf("| %s | %s | %s%s | %,d | %s | %d | %d → %d | %d | %s |%n",
                                    name, cols[2].trim(), sign > 0 ? "+" : "−", axis == 0 ? "X" : "Z", reference,
                                    bestScore >= 32 ? String.format(Locale.ROOT, "%,d", bestAt) : "—", other,
                                    bestBefore, bestAfter, bestChanged,
                                    bestScore >= 32 ? "local material edge; attribution unverified" : "no abrupt material edge in window");
                            System.out.flush();
                        }
                    }
                } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                return;
            }
            BigInteger z = args.length > 2 ? new BigInteger(args[2]) : BigInteger.valueOf(655);
            if (args.length > 3 && args[3].equals("wall-scan")) {
                BigInteger start = new BigInteger(args[1]), end = new BigInteger(args[5]);
                int cutoff = Integer.parseInt(args[4]);
                int direction = end.compareTo(start);
                if (direction == 0) throw new IllegalArgumentException("Empty scan");
                BigInteger step = BigInteger.valueOf(direction);
                int previous = highestSolid(state, start, z);
                if (previous < cutoff) throw new IllegalArgumentException("Start already below cutoff: " + previous);
                for (BigInteger x = start.add(step); x.compareTo(end) * direction <= 0; x = x.add(step)) {
                    int top = highestSolid(state, x, z);
                    if (top < cutoff) {
                        System.out.printf("WALL mode=%s seed=0 precision=precise z=%s cutoff=%d scannedFrom=%s beforeX=%s beforeTop=%d afterX=%s afterTop=%d%n",
                                mode, z, cutoff, start, x.subtract(step), previous, x, top);
                        return;
                    }
                    previous = top;
                }
                System.out.printf("NO_WALL mode=%s z=%s cutoff=%d from=%s through=%s%n", mode, z, cutoff, start, end);
                return;
            }
            if (args.length > 3 && args[3].equals("rough-edge")) {
                BigInteger lo=new BigInteger(args[1]),hi=new BigInteger(args[5]);
                int y=Integer.parseInt(args[4]);
                DensityFunction rough=state.debugFunction(key("caves/spaghetti_roughness_function"));
                while(hi.subtract(lo).compareTo(BigInteger.ONE)>0) {
                    BigInteger mid=lo.add(hi).shiftRight(1);
                    if(rough.compute(point(mid,y,z))<0)hi=mid;else lo=mid;
                }
                System.out.printf("ROUGH_EDGE z=%s y=%d beforeX=%s before=%g afterX=%s after=%g%n",z,y,lo,rough.compute(point(lo,y,z)),hi,rough.compute(point(hi,y,z)));
                return;
            }
            if (args.length > 3 && args[3].equals("profile")) {
                for (String value : args[1].split(",")) {
                    BigInteger x = new BigInteger(value);
                    System.out.printf("PROFILE x=%s",x);
                    for(int dz=0;dz<=1024;dz+=32) System.out.printf(" %d:%d",dz,highestSolid(state,x,z.add(BigInteger.valueOf(dz))));
                    System.out.println();
                }
                return;
            }
            if (args.length > 3 && args[3].equals("corners")) {
                for (String value : args[1].split(",")) {
                    BigInteger x = new BigInteger(value);
                    for(int y=-64;y<=256;y+=8) {
                        var p=point(x,y,z);
                        System.out.printf("CORNER x=%s z=%s y=%d pillars=%g candidate=%g selected=%g rough=%g spaghetti=%g entrance=%g%n",x,z,y,
                            state.debugFunction(key("caves/pillars")).compute(p),caves.get("candidate").compute(p),caves.get("selected").compute(p),
                            state.debugFunction(key("caves/spaghetti_roughness_function")).compute(p),state.debugFunction(key("caves/spaghetti_2d")).compute(p),
                            state.debugFunction(key("caves/entrances")).compute(p));
                    }
                }
                return;
            }
            if (args.length > 3 && args[3].equals("transect")) {
                for (String value : args[1].split(",")) {
                    BigInteger x = new BigInteger(value);
                    int previous = Integer.MIN_VALUE, start = 0;
                    for (int offset = 0; offset <= 1024; offset++) {
                        int top = highestSolid(state,x,z.add(BigInteger.valueOf(offset)));
                        if (top != previous) {
                            if (offset > 0) System.out.printf("TOP x=%s z=%s..%s y=%d%n",x,z.add(BigInteger.valueOf(start)),z.add(BigInteger.valueOf(offset-1)),previous);
                            start=offset;
                            previous=top;
                        }
                    }
                    System.out.printf("TOP x=%s z=%s..%s y=%d%n",x,z.add(BigInteger.valueOf(start)),z.add(BigInteger.valueOf(1024)),previous);
                }
                return;
            }
            if (args.length > 3 && args[3].equals("region")) {
                for (String value : args[1].split(",")) {
                    BigInteger x = new BigInteger(value);
                    int[] histogram = new int[385];
                    int count = 0, tall = 0, middle = 0, low = 0, empty = 0;
                    for (int dx = 0; dx < 64; dx += 16) {
                        for (int dz = -512; dz <= 512; dz += 16) {
                            int top = highestSolid(state,x.add(BigInteger.valueOf(dx)),z.add(BigInteger.valueOf(dz)));
                            histogram[top+65]++;
                            count++;
                            if (top >= 200) tall++;
                            else if (top >= 100) middle++;
                            else if (top >= -64) low++;
                            else empty++;
                        }
                    }
                    System.out.printf("REGION x=%s centerZ=%s n=%d tall=%d middle=%d low=%d empty=%d tops=",x,z,count,tall,middle,low,empty);
                    for (int i=0;i<histogram.length;i++) if(histogram[i]>0) System.out.printf("%d:%d,",i-65,histogram[i]);
                    System.out.println();
                }
                return;
            }
            if (args.length > 3 && args[3].equals("height-search")) {
                BigInteger lo = new BigInteger(args[1]), hi = new BigInteger(args[5]);
                int cutoff = Integer.parseInt(args[4]);
                int lowTop = highestSolid(state,lo,z), highTop = highestSolid(state,hi,z);
                if (lowTop < cutoff || highTop >= cutoff)
                    throw new IllegalArgumentException("Height predicate is not bracketed: " + lowTop + " -> " + highTop);
                while (hi.subtract(lo).compareTo(BigInteger.ONE) > 0) {
                    BigInteger mid = lo.add(hi).shiftRight(1);
                    if (highestSolid(state,mid,z) < cutoff) hi = mid;
                    else lo = mid;
                }
                System.out.printf("HEIGHT_EDGE z=%s cutoff=%d beforeX=%s beforeTop=%d afterX=%s afterTop=%d%n",
                        z,cutoff,lo,highestSolid(state,lo,z),hi,highestSolid(state,hi,z));
                return;
            }
            if (args.length > 3 && args[3].equals("search")) {
                DensityFunction pillars = args.length > 4 && args[4].equals("selected") ? caves.get("selected") : state.debugFunction(key("caves/pillars"));
                BigInteger base = new BigInteger("2319144370667437144697");
                for (int y = -64; y <= 256; y += 8) {
                    BigInteger lo = base, hi = base;
                    for (int power = 0; power <= 13; power++) {
                        hi = base.add(BigInteger.TEN.pow(power));
                        double v = pillars.compute(point(hi,y,z));
                        if (!Double.isFinite(v)) break;
                        lo = hi;
                    }
                    if (Double.isFinite(pillars.compute(point(hi,y,z)))) continue;
                    while (hi.subtract(lo).compareTo(BigInteger.ONE) > 0) {
                        BigInteger mid = lo.add(hi).shiftRight(1);
                        if (!Double.isFinite(pillars.compute(point(mid,y,z)))) hi = mid;
                        else lo = mid;
                    }
                    System.out.printf("LOCAL_INFINITY y=%d z=%s x=%s before=%g after=%g%n",y,z,hi,
                            pillars.compute(point(hi.subtract(BigInteger.ONE),y,z)),pillars.compute(point(hi,y,z)));
                }
                return;
            }
            for (String value : (args.length > 1 ? args[1] : "4638288741334874281276").split(",")) {
                BigInteger x = new BigInteger(value);
                System.out.println("POINT x=" + x + " z=" + z + " mode=" + mode);
                boolean blocks = args.length > 3 && args[3].equals("blocks");
                boolean column = blocks || args.length > 3 && args[3].equals("column");
                if (!column) {
                for (int y : new int[]{-64,-60,-56,0,64,128,132,134,136,144,192,200,232,240,248,252,255,256,264,312,320}) {
                    var p = point(x,y,z);
                    System.out.printf("y=%d cont=%g eros=%g depth=%g sloped=%g pillar=%g layer=%g candidate=%g underground=%g selected=%g final=%g%n", y,
                            state.router().continents().compute(p), state.router().erosion().compute(p), state.router().depth().compute(p),
                            state.debugFunction(key("sloped_cheese")).compute(p), state.debugFunction(key("caves/pillars")).compute(p),
                            caves.get("cheese_layer").compute(p), caves.get("candidate").compute(p), caves.get("underground").compute(p),
                            caves.get("selected").compute(p), state.router().finalDensity().compute(p));
                }
                }
                BigInteger originX = x.shiftRight(4).shiftLeft(4), originZ = z.shiftRight(4).shiftLeft(4);
                var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
                var chunk = new NoiseChunk(4, state, Int1088.parseDecimal(originX.toString()), Int1088.parseDecimal(originZ.toString()),
                        settings.noiseSettings(), DensityFunctions.BeardifierMarker.INSTANCE, settings,
                         (px,py,pz) -> py < -54 ? new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState())
                                 : new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState()), Blender.empty());
                DensityFunction interpolated = state.router().finalDensity().mapAll(chunk::wrap);
                chunk.initializeForFirstCellX();
                int localX = x.subtract(originX).intValueExact(), localZ = z.subtract(originZ).intValueExact();
                 for (int cell = 0; cell <= localX/4; cell++) {
                     chunk.advanceCellX(cell);
                     if (cell < localX/4) chunk.swapSlices();
                 }
                int lastKind = -1, start = -64;
                String lastBlock = null;
                for (int y = -64; y <= 319; y++) {
                    chunk.selectCellYZ((y+64)/8,localZ/4);
                    chunk.updateForY(y,(y&7)/8.0);
                    chunk.updateForX(Int1088.parseDecimal(x.toString()),(localX&3)/4.0);
                    chunk.updateForZ(Int1088.parseDecimal(z.toString()),(localZ&3)/4.0);
                    double density = interpolated.compute(chunk);
                    if (blocks) {
                        var material = chunk.getInterpolatedState();
                        if (material == null) material = settings.defaultBlock();
                        String name = material.toString();
                        if (!name.equals(lastBlock)) {
                            if (lastBlock != null) System.out.printf("BLOCK y=%d..%d %s%n",start,y-1,lastBlock);
                            start = y;
                            lastBlock = name;
                        }
                        continue;
                    }
                    int kind = Double.isNaN(density) ? 2 : density > 0 ? 1 : 0;
                    if (column) {
                        if (kind != lastKind && lastKind != -1) System.out.printf("BAND y=%d..%d %s%n", start,y-1,new String[]{"empty","solid","NaN"}[lastKind]);
                        if (kind != lastKind) start = y;
                        lastKind = kind;
                    } else System.out.printf("INTERPOLATED y=%d density=%g%n", y, density);
                }
                if (blocks) System.out.printf("BLOCK y=%d..319 %s%n",start,lastBlock);
                else if (column) System.out.printf("BAND y=%d..319 %s%n", start,new String[]{"empty","solid","NaN"}[lastKind]);
                chunk.stopInterpolation();
            }
        }
    }
}
