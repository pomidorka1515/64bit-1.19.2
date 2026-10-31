package net.minecraft.world.level.levelgen.synth;

import net.minecraft.client.renderer.ClientHotspotPerformanceProbe;
import net.minecraft.core.Int1088;

/** Compares the paired vertex hash with the unchanged independent reference. */
public final class NoiseVertexPerformanceProbe {
    public static void main(String[] args) {
        try (var scope = GenerationContext.open(new GeneratorConfig(GeneratorMode.PATCHED, CoordinatePrecision.PRECISE))) {
            for (int bits : new int[] {0, 160, 1024}) {
                PreciseNoiseCoordinate.Split[] splits = new PreciseNoiseCoordinate.Split[1024];
                Int1088 base = bits == 0 ? Int1088.ZERO : Int1088.ONE.shiftLeftSaturated(bits);
                for (int i = 0; i < splits.length; ++i) {
                    splits[i] = new PreciseNoiseCoordinate.Split();
                    WorldgenCoordinate.scale(base.addSaturated(i), 684.412, -3, splits[i]);
                }
                long old = ClientHotspotPerformanceProbe.measure("vertices bits=" + bits + " independent",
                        () -> hash(splits, false));
                long next = ClientHotspotPerformanceProbe.measure("vertices bits=" + bits + " paired",
                        () -> hash(splits, true));
                if (old != next) throw new AssertionError("Vertex checksum mismatch");
            }
        }
    }

    private static long hash(PreciseNoiseCoordinate.Split[] splits, boolean optimized) {
        long checksum = 0;
        for (int i = 0; i < ClientHotspotPerformanceProbe.OPERATIONS; ++i) {
            var split = splits[i & 1023];
            if (optimized) {
                split.computeVertices();
                checksum += split.vertex0 ^ split.vertex1;
            } else {
                checksum += split.vertexIndex(0) ^ split.vertexIndex(1);
            }
        }
        return checksum;
    }
}
