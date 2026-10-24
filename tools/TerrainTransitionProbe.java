package net.minecraft.world.level.levelgen;

import java.math.BigInteger;
import java.util.Locale;
import net.minecraft.core.Registry;
import net.minecraft.core.Int128;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.synth.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.block.Blocks;

/** Read-only seed-zero density probe; all horizontal inputs remain exact integers. */
public final class TerrainTransitionProbe {
    private static DensityFunction.SinglePointContext point(BigInteger x, int y, BigInteger z) {
        return new DensityFunction.SinglePointContext(x.shiftRight(64).longValue(), x.longValue(), y,
                z.shiftRight(64).longValue(), z.longValue());
    }

    private static ResourceKey<DensityFunction> key(String name) {
        return ResourceKey.create(Registry.DENSITY_FUNCTION_REGISTRY, new ResourceLocation("overworld/" + name));
    }

    /** Noise-fill materials, before surface rules, structures, and carvers. */
    private static int highestSolid(RandomState state, BigInteger x, BigInteger z) {
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        BigInteger ox = x.shiftRight(4).shiftLeft(4), oz = z.shiftRight(4).shiftLeft(4);
        var chunk = new NoiseChunk(4,state,Int128.parseDecimal(ox.toString()),Int128.parseDecimal(oz.toString()),
                settings.noiseSettings(),DensityFunctions.BeardifierMarker.INSTANCE,settings,
                (px,py,pz) -> py < -54 ? new Aquifer.FluidStatus(-54,Blocks.LAVA.defaultBlockState())
                        : new Aquifer.FluidStatus(63,Blocks.WATER.defaultBlockState()),Blender.empty());
        int lx = x.subtract(ox).intValueExact(), lz = z.subtract(oz).intValueExact();
        chunk.initializeForFirstCellX();
        for (int cell = 0; cell <= lx/4; cell++) {
            chunk.advanceCellX(cell);
            if (cell < lx/4) chunk.swapSlices();
        }
        int highest = -65;
        for (int y = 319; y >= -64; y--) {
            chunk.selectCellYZ((y+64)/8,lz/4);
            chunk.updateForY(y,(y&7)/8.0);
            chunk.updateForX(x.longValue(),(lx&3)/4.0);
            chunk.updateForZ(z.longValue(),(lz&3)/4.0);
            var block = chunk.getInterpolatedState();
            if (block == null) block = settings.defaultBlock();
            if (!block.isAir() && block.getFluidState().isEmpty()) { highest = y; break; }
        }
        chunk.stopInterpolation();
        return highest;
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        GeneratorMode mode = args.length > 0 && args[0].equals("ordinary") ? GeneratorMode.BIT_64 : GeneratorMode.BIT_64_STABLE_SHIFTS;
        try (var ignored = GenerationContext.open(new GeneratorConfig(mode, CoordinatePrecision.PRECISE))) {
            var state = RandomState.create(BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD), BuiltinRegistries.NOISE, 0L);
            var caves = state.debugCaves(key("sloped_cheese"));
            BigInteger z = args.length > 2 ? new BigInteger(args[2]) : BigInteger.valueOf(655);
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
                var chunk = new NoiseChunk(4, state, Int128.parseDecimal(originX.toString()), Int128.parseDecimal(originZ.toString()),
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
                    chunk.updateForX(x.longValue(),(localX&3)/4.0);
                    chunk.updateForZ(z.longValue(),(localZ&3)/4.0);
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
