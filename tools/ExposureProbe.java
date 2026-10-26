package net.minecraft.world.level.levelgen;

import java.math.BigInteger;
import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int128;
import net.minecraft.core.Registry;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.*;

/** Read-only seed-0 probe for pillar infinity and its interpolated terrain effects. */
public final class ExposureProbe {
    private static DensityFunction.SinglePointContext axisPoint(BigInteger n, int y, int other, boolean alongX) {
        BigInteger x = alongX ? n : BigInteger.valueOf(other), z = alongX ? BigInteger.valueOf(other) : n;
        return new DensityFunction.SinglePointContext(x.shiftRight(64).longValue(), x.longValue(), y, z.shiftRight(64).longValue(), z.longValue());
    }

    private static int[] column(RandomState state, BigInteger n, boolean alongX) {
        BigInteger x = alongX ? n : BigInteger.ZERO, z = alongX ? BigInteger.ZERO : n;
        BigInteger ox = x.shiftRight(4).shiftLeft(4), oz = z.shiftRight(4).shiftLeft(4);
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var chunk = new NoiseChunk(4, state, Int128.parseDecimal(ox.toString()), Int128.parseDecimal(oz.toString()),
                settings.noiseSettings(), DensityFunctions.BeardifierMarker.INSTANCE, settings,
                (px,py,pz) -> py < -54 ? new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState())
                        : new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState()), Blender.empty());
        var density = state.router().finalDensity().mapAll(chunk::wrap);
        int lx=x.subtract(ox).intValueExact(), lz=z.subtract(oz).intValueExact();
        chunk.initializeForFirstCellX();
        for(int cx=0;cx<=lx/4;cx++) { chunk.advanceCellX(cx); if(cx<lx/4) chunk.swapSlices(); }
        int top=-65,solid=0,nan=0;
        for(int cy=47;cy>=0;cy--) {
            chunk.selectCellYZ(cy,lz/4);
            for(int dy=7;dy>=0;dy--) {
                int y=-64+cy*8+dy;
                chunk.updateForY(y,dy/8.0);
                chunk.updateForX(x.longValue(),(lx%4)/4.0);
                chunk.updateForZ(z.longValue(),(lz%4)/4.0);
                if(Double.isNaN(density.compute(chunk))) nan++;
                var block=chunk.getInterpolatedState();
                if(block==null) block=settings.defaultBlock();
                if(!block.isAir() && block.getFluidState().isEmpty()) { solid++;top=Math.max(top,y); }
            }
        }
        chunk.stopInterpolation();
        System.out.printf("COLUMN axis=%s n=%s top=%d solid=%d nan=%d%n",alongX?"X":"Z",n,top,solid,nan);
        return new int[]{top,solid,nan};
    }

    private static void survey64(RandomState state, DensityFunction pillars) {
        for(boolean alongX:new boolean[]{true,false}) for(int sign:new int[]{1,-1}) {
            BigInteger best=null; int bestY=0,bestOther=0;
            for(int other:new int[]{0,4}) for(int y=-64;y<=256;y+=8) {
                BigInteger lo=BigInteger.TEN.pow(21),hi=BigInteger.TEN.pow(26);
                if(!Double.isFinite(pillars.compute(axisPoint(lo.multiply(BigInteger.valueOf(sign)),y,other,alongX))))
                    throw new AssertionError("Nonfinite lower bracket");
                double end=pillars.compute(axisPoint(hi.multiply(BigInteger.valueOf(sign)),y,other,alongX));
                if(end!=Double.POSITIVE_INFINITY) continue;
                while(hi.subtract(lo).compareTo(BigInteger.ONE)>0) {
                    BigInteger mid=lo.add(hi).shiftRight(1);
                    if(Double.isFinite(pillars.compute(axisPoint(mid.multiply(BigInteger.valueOf(sign)),y,other,alongX)))) lo=mid;else hi=mid;
                }
                if(best==null || hi.compareTo(best)<0) {best=hi;bestY=y;bestOther=other;}
            }
            System.out.printf("BEST axis=%s sign=%d magnitude=%s y=%d other=%d%n",alongX?"X":"Z",sign,best,bestY,bestOther);
            if(best==null) continue;
            for(int delta=-1;delta<=0;delta++) {
                BigInteger n=best.add(BigInteger.valueOf(delta)).multiply(BigInteger.valueOf(sign));
                System.out.printf("PILLAR n=%s value=%s%n",n,pillars.compute(axisPoint(n,bestY,bestOther,alongX)));
            }
            BigInteger plane=best.add(BigInteger.valueOf(3)).shiftRight(2).shiftLeft(2).multiply(BigInteger.valueOf(sign));
            for(int delta=-8;delta<=8;delta++) column(state,plane.add(BigInteger.valueOf(delta)),alongX);
        }
    }
    private static DensityFunction.SinglePointContext point(BigInteger x, int y, long z) {
        return new DensityFunction.SinglePointContext(x.shiftRight(64).longValue(), x.longValue(), y, z < 0 ? -1 : 0, z);
    }

    private static int[][] strip(RandomState state, BigInteger x, long z) {
        var settings = BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        BigInteger origin = x.shiftRight(4).shiftLeft(4);
        long oz = Math.floorDiv(z, 16) * 16;
        var chunk = new NoiseChunk(4, state, Int128.parseDecimal(origin.toString()), Int128.of(oz < 0 ? -1 : 0, oz),
                settings.noiseSettings(), DensityFunctions.BeardifierMarker.INSTANCE, settings,
                (px, py, pz) -> py < -54 ? new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState())
                        : new Aquifer.FluidStatus(63, Blocks.WATER.defaultBlockState()), Blender.empty());
        var density = state.router().finalDensity().mapAll(chunk::wrap);
        int[] solid = new int[16], nan = new int[16], top = new int[16];
        java.util.Arrays.fill(top, -65);
        int lz = Math.floorMod(z, 16);
        chunk.initializeForFirstCellX();
        for (int cx = 0; cx < 4; cx++) {
            chunk.advanceCellX(cx);
            for (int cy = 47; cy >= 0; cy--) {
                chunk.selectCellYZ(cy, lz / 4);
                for (int dy = 7; dy >= 0; dy--) {
                    int y = -64 + cy * 8 + dy;
                    chunk.updateForY(y, dy / 8.0);
                    for (int dx = 0; dx < 4; dx++) {
                        int lx = cx * 4 + dx;
                        chunk.updateForX(origin.longValue() + lx, dx / 4.0);
                        chunk.updateForZ(z, (lz % 4) / 4.0);
                        double value = density.compute(chunk);
                        if (Double.isNaN(value)) nan[lx]++;
                        var block = chunk.getInterpolatedState();
                        if (block == null) block = settings.defaultBlock();
                        if (!block.isAir() && block.getFluidState().isEmpty()) {
                            solid[lx]++;
                            top[lx] = Math.max(top[lx], y);
                        }
                    }
                }
            }
            if (cx < 3) chunk.swapSlices();
        }
        chunk.stopInterpolation();
        System.out.printf("STRIP x=%s z=%d", origin, z);
        for (int i = 0; i < 16; i++) System.out.printf(" %d:%d/%d/%d", i, top[i], solid[i], nan[i]);
        System.out.println(" (offset:top/solid/NaN)");
        return new int[][] {top, solid, nan};
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (var ignored = GenerationContext.open(new GeneratorConfig(args[0].contains("64") ? GeneratorMode.BIT_64_STABLE_SHIFTS : GeneratorMode.BIT_32_STABLE_SHIFTS, CoordinatePrecision.PRECISE))) {
            var state = RandomState.create(BuiltinRegistries.NOISE_GENERATOR_SETTINGS.getOrThrow(NoiseGeneratorSettings.OVERWORLD), BuiltinRegistries.NOISE, 0);
            var pillars = state.debugFunction(ResourceKey.create(Registry.DENSITY_FUNCTION_REGISTRY, new ResourceLocation("overworld/caves/pillars")));
            if (args[0].equals("verify64")) {
                String[] edges={"2319144373482458742508","-2319144370667437144697","2319144373009074352936","-2319144370667437144697"};
                for(int axis=0;axis<4;axis++) {
                    boolean alongX=axis<2; int sign=axis%2==0?1:-1;
                    BigInteger edge=new BigInteger(edges[axis]);
                    for(int delta=-16;delta<=16;delta++) {
                        int[] values=column(state,edge.add(BigInteger.valueOf((long)delta*sign)),alongX);
                        if((values[2]>0)!=(delta>=0)) throw new AssertionError("NaN boundary changed axis="+axis+" delta="+delta);
                    }
                }
                System.out.println("PASS: exhaustive 33-column windows across all four 64-bit local NaN boundaries");
            } else if (args[0].equals("columns64")) {
                for(String n:args[1].split(",")) column(state,new BigInteger(n),args[2].equals("X"));
            } else if (args[0].equals("search64")) {
                boolean alongX=args[1].equals("X");
                BigInteger lo=new BigInteger(args[2]),hi=new BigInteger(args[3]);
                if(column(state,lo,alongX)[2]!=0 || column(state,hi,alongX)[2]==0) throw new AssertionError("Not bracketed");
                while(hi.subtract(lo).abs().compareTo(BigInteger.ONE)>0) {
                    BigInteger mid=lo.add(hi).shiftRight(1);
                    if(column(state,mid,alongX)[2]==0)lo=mid;else hi=mid;
                }
                System.out.printf("NAN_EDGE axis=%s inward=%s outward=%s%n",args[1],lo,hi);
            } else if (args[0].equals("survey64")) {
                survey64(state,pillars);
            } else if (args[0].equals("verify")) {
                BigInteger edge = new BigInteger("434257164951950");
                double before = pillars.compute(point(edge.subtract(BigInteger.ONE),152,4));
                double after = pillars.compute(point(edge,152,4));
                if (!Double.isFinite(before) || after != Double.POSITIVE_INFINITY) throw new AssertionError("Pillar boundary changed");
                var columns = strip(state, edge, 0);
                for (int i = 0; i < 16; i++) {
                    if (columns[2][i] != (i < 12 ? 0 : 16)
                            || columns[1][i] != (i < 12 ? 299 : 283)) throw new AssertionError("Material boundary changed at " + i);
                }
                System.out.println("PASS: finite/infinite neighbors and all 16 interpolated material columns");
            } else if (args[0].equals("edges")) {
                long z = Long.parseLong(args[1]);
                for (int y = -64; y <= 256; y += 8) {
                    BigInteger lo = new BigInteger(args[2]), hi = new BigInteger(args[3]);
                    if (!Double.isFinite(pillars.compute(point(lo,y,z))) || Double.isFinite(pillars.compute(point(hi,y,z)))) continue;
                    // Local bracket only: noise is not assumed globally monotonic.
                    while (hi.subtract(lo).compareTo(BigInteger.ONE) > 0) {
                        BigInteger mid = lo.add(hi).shiftRight(1);
                        if (Double.isFinite(pillars.compute(point(mid,y,z)))) lo = mid; else hi = mid;
                    }
                    System.out.printf("LOCAL_EDGE z=%d y=%d beforeX=%s before=%s afterX=%s after=%s%n", z,y,lo,pillars.compute(point(lo,y,z)),hi,pillars.compute(point(hi,y,z)));
                }
            } else {
                for (String coordinate : args[1].split(","))
                    for (String transverse : args[2].split(",")) strip(state, new BigInteger(coordinate), Long.parseLong(transverse));
            }
        }
    }
}
