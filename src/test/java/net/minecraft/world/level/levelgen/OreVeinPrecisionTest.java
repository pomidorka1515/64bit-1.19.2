package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.FarlandsMode;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Guards the 3D sampling of the ore-vein noise chain (vein_toggle, vein_ridged,
 * vein_gap) consumed by OreVeinifier. The planar-crush bug showed up when one of
 * the three sample axes silently collapsed at 64-bit coordinates (fraction lost
 * to double rounding, Y leaking into Z, or axes swapped), turning vein material
 * into flat 2D sheets.
 */
class OreVeinPrecisionTest {
   private static final long HUGE_X = 3L << 58;
   private static final long HUGE_Z = -(5L << 57);
   private static final int VEIN_Y = -30;
   private static final long NOISE_SEED = 1357L;

   @BeforeAll
   static void bootstrapMinecraft() {
      SharedConstants.tryDetectVersion();
      Bootstrap.bootStrap();
   }

   @AfterEach
   void restoreDefaultMode() {
      FarlandsMode.setMode(FarlandsMode.Mode.BIT_32);
   }

   private static DensityFunction.NoiseHolder holder(ResourceKey<NormalNoise.NoiseParameters> key) {
      Holder<NormalNoise.NoiseParameters> parameters = BuiltinRegistries.NOISE.getHolderOrThrow(key);
      NormalNoise noise = Noises.instantiate(BuiltinRegistries.NOISE, new LegacyRandomSource(NOISE_SEED).forkPositional(), key);
      return new DensityFunction.NoiseHolder(parameters, noise);
   }

   private static DensityFunctions.Noise veinNoise(ResourceKey<NormalNoise.NoiseParameters> key, double scale) {
      return new DensityFunctions.Noise(holder(key), scale, scale);
   }

   private static DensityFunction veinRidged() {
      return DensityFunctions.add(
         DensityFunctions.constant(-0.08D),
         DensityFunctions.max(veinNoise(Noises.ORE_VEIN_A, 4.0D).abs(), veinNoise(Noises.ORE_VEIN_B, 4.0D).abs())
      );
   }

   private static DensityFunction.FunctionContext context(long x, int y, long z) {
      return new DensityFunction.SinglePointContext(x, y, z);
   }

   private static long bits(double value) {
      return Double.doubleToRawLongBits(value);
   }

   @Test
   void veinNoiseFunctionsStayThreeDimensionalAtHugeCoordinates() {
      FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
      assertVariesAlongAllAxes(veinNoise(Noises.ORE_VEININESS, 1.5D), "ore_veininess (vein_toggle)");
      assertVariesAlongAllAxes(veinNoise(Noises.ORE_VEIN_A, 4.0D), "ore_vein_a (vein_ridged)");
      assertVariesAlongAllAxes(veinNoise(Noises.ORE_VEIN_B, 4.0D), "ore_vein_b (vein_ridged)");
      assertVariesAlongAllAxes(veinNoise(Noises.ORE_GAP, 1.0D), "ore_gap (vein_gap)");
      assertVariesAlongAllAxes(veinRidged(), "vein_ridged composition");
   }

   @Test
   void shiftNoiseVariantsKeepTheirVanillaAxisUsageAtHugeCoordinates() {
      FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
      DensityFunction.NoiseHolder shift = holder(Noises.SHIFT);

      DensityFunctions.Shift full = new DensityFunctions.Shift(shift);
      double baseFull = full.compute(context(HUGE_X, VEIN_Y, HUGE_Z));
      assertTrue(axisVaries(full, baseFull, HUGE_X, VEIN_Y, HUGE_Z, 1, 0, 0), "shift (X slot) must vary along X");
      assertTrue(axisVaries(full, baseFull, HUGE_X, VEIN_Y, HUGE_Z, 0, 1, 0), "shift (Y slot) must vary along Y");
      assertTrue(axisVaries(full, baseFull, HUGE_X, VEIN_Y, HUGE_Z, 0, 0, 1), "shift (Z slot) must vary along Z");

      DensityFunctions.ShiftA shiftA = new DensityFunctions.ShiftA(shift);
      double baseA = shiftA.compute(context(HUGE_X, VEIN_Y, HUGE_Z));
      assertTrue(axisVaries(shiftA, baseA, HUGE_X, VEIN_Y, HUGE_Z, 1, 0, 0), "shift_a must vary along X");
      assertTrue(axisVaries(shiftA, baseA, HUGE_X, VEIN_Y, HUGE_Z, 0, 0, 1), "shift_a must vary along Z");
      assertTrue(axisConstant(shiftA, baseA, HUGE_X, VEIN_Y, HUGE_Z, 0, 1, 0), "shift_a samples y=0 and must stay Y-independent like vanilla");

      DensityFunctions.ShiftB shiftB = new DensityFunctions.ShiftB(shift);
      double baseB = shiftB.compute(context(HUGE_X, VEIN_Y, HUGE_Z));
      assertTrue(axisVaries(shiftB, baseB, HUGE_X, VEIN_Y, HUGE_Z, 1, 0, 0), "shift_b maps blockX into the noise Y slot and must vary along X");
      assertTrue(axisVaries(shiftB, baseB, HUGE_X, VEIN_Y, HUGE_Z, 0, 0, 1), "shift_b maps blockZ into the noise X slot and must vary along Z");
      assertTrue(axisConstant(shiftB, baseB, HUGE_X, VEIN_Y, HUGE_Z, 0, 1, 0), "shift_b samples z=0 and must stay Y-independent like vanilla");
      assertNotEquals(bits(baseA), bits(baseB), "shift_a and shift_b must remain distinct noise slots (no axis collapse into the same plane)");
   }

   @Test
   void shiftedNoiseKeepsBothHorizontalAxesAtHugeCoordinates() {
      FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
      DensityFunction.NoiseHolder shift = holder(Noises.SHIFT);
      DensityFunctions.ShiftedNoise shifted = new DensityFunctions.ShiftedNoise(
         new DensityFunctions.ShiftA(shift), DensityFunctions.zero(), new DensityFunctions.ShiftB(shift),
         0.25D, 0.0D, holder(Noises.TEMPERATURE));
      double base = shifted.compute(context(HUGE_X, VEIN_Y, HUGE_Z));
      assertTrue(axisVaries(shifted, base, HUGE_X, VEIN_Y, HUGE_Z, 1, 0, 0), "shifted noise must vary along X");
      assertTrue(axisVaries(shifted, base, HUGE_X, VEIN_Y, HUGE_Z, 0, 0, 1), "shifted noise must vary along Z");
      assertTrue(axisConstant(shifted, base, HUGE_X, VEIN_Y, HUGE_Z, 0, 1, 0), "2D shifted noise must ignore Y like vanilla");
   }

   @Test
   void veinNoiseBelowThePreciseGateMatchesTheLegacyPath() {
      long x = 1_000_003L;
      long z = -2_500_007L;
      int y = -30;
      DensityFunctions.Noise[] functions = {
         veinNoise(Noises.ORE_VEININESS, 1.5D),
         veinNoise(Noises.ORE_VEIN_A, 4.0D),
         veinNoise(Noises.ORE_VEIN_B, 4.0D),
         veinNoise(Noises.ORE_GAP, 1.0D)
      };

      long[] expectedOff = new long[functions.length];
      FarlandsMode.setMode(FarlandsMode.Mode.OFF);
      for (int i = 0; i < functions.length; ++i) {
         expectedOff[i] = bits(functions[i].compute(context(x, y, z)));
      }

      FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
      for (int i = 0; i < functions.length; ++i) {
         assertEquals(expectedOff[i], bits(functions[i].compute(context(x, y, z))),
            "coordinates below the 2^53 gate must keep the untouched legacy evaluation");
      }
   }

   @Test
   void onlyPreciseModeChangesHugeCoordinateVeinNoise() {
      DensityFunctions.Noise gap = veinNoise(Noises.ORE_GAP, 1.0D);

      FarlandsMode.setMode(FarlandsMode.Mode.OFF);
      long off = bits(gap.compute(context(HUGE_X, VEIN_Y, HUGE_Z)));

      FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
      long precise = bits(gap.compute(context(HUGE_X, VEIN_Y, HUGE_Z)));
      assertNotEquals(off, precise, "precise mode must replace the crushed legacy double sampling at huge coordinates");
      assertEquals(bits(gap.noise().getValueScaled(HUGE_X, 1.0D, VEIN_Y, 1.0D, HUGE_Z)), precise,
         "the density function must delegate to the split-coordinate NormalNoise path");
   }

   @Test
   void oreVeinifierPlacesVolumetricVeinsAtHugeCoordinates() {
      FarlandsMode.setMode(FarlandsMode.Mode.BIT_64_PRECISE);
      DensityFunction toggle = veinNoise(Noises.ORE_VEININESS, 1.5D);
      DensityFunction ridged = veinRidged();
      DensityFunction gap = veinNoise(Noises.ORE_GAP, 1.0D);
      NoiseChunk.BlockStateFiller veinifier = OreVeinifier.create(toggle, ridged, gap, new LegacyRandomSource(424242L).forkPositional());

      // Deterministically locate a copper-vein core: toggle >= 0.42 (edge round-off is
      // zero at y = 25) inside a wide ridged zero-band, which guarantees vein material.
      int centerY = 25;
      long centerX = 0L;
      long centerZ = 0L;
      double bestToggle = Double.NEGATIVE_INFINITY;

      for (long dx = -1024; dx <= 1024; dx += 8) {
         for (long dz = -1024; dz <= 1024; dz += 8) {
            DensityFunction.FunctionContext probe = context(HUGE_X + dx, centerY, HUGE_Z + dz);
            double t = toggle.compute(probe);
            double r = ridged.compute(probe);
            if (t >= 0.42D && r < -0.05D && t > bestToggle) {
               bestToggle = t;
               centerX = HUGE_X + dx;
               centerZ = HUGE_Z + dz;
            }
         }
      }

      assertTrue(bestToggle > Double.NEGATIVE_INFINITY, "expected a vein core (toggle >= 0.42, ridged < -0.05) near the probe origin");

      // Sample the vein indicator on a dense 3D grid around the core. A planar crush
      // makes the indicator independent of one axis (material extruded into flat
      // sheets), so require genuine per-axis variation plus a 3D span.
      int nx = 33;
      int ny = 9;
      int nz = 33;
      boolean[][][] material = new boolean[nx][ny][nz];
      int veinBlocks = 0;

      for (int ix = 0; ix < nx; ++ix) {
         for (int iy = 0; iy < ny; ++iy) {
            for (int iz = 0; iz < nz; ++iz) {
               BlockState state = veinifier.calculate(context(centerX + (long)(ix - 16), centerY + iy - 4, centerZ + (long)(iz - 16)));
               material[ix][iy][iz] = state != null;
               if (state != null) {
                  ++veinBlocks;
               }
            }
         }
      }

      assertTrue(veinBlocks >= 12, "vein sample window should contain vein material, found " + veinBlocks);

      Set<Integer> veinX = new HashSet<>();
      Set<Integer> veinY = new HashSet<>();
      Set<Integer> veinZ = new HashSet<>();

      for (int ix = 0; ix < nx; ++ix) {
         for (int iy = 0; iy < ny; ++iy) {
            for (int iz = 0; iz < nz; ++iz) {
               if (material[ix][iy][iz]) {
                  veinX.add(ix);
                  veinY.add(iy);
                  veinZ.add(iz);
               }
            }
         }
      }

      assertTrue(veinX.size() >= 4, "vein material must span X, spans: " + veinX.size());
      assertTrue(veinY.size() >= 2, "vein material must span Y, spans: " + veinY.size());
      assertTrue(veinZ.size() >= 4, "vein material must span Z, spans: " + veinZ.size());
      assertTrue(variesAlongAxis(material, 0), "vein material must vary between X-adjacent samples (not a Y-Z sheet)");
      assertTrue(variesAlongAxis(material, 1), "vein material must vary between Y-adjacent samples (not an X-Z plane)");
      assertTrue(variesAlongAxis(material, 2), "vein material must vary between Z-adjacent samples (not an X-Y wall)");
   }

   private static boolean variesAlongAxis(boolean[][][] material, int axis) {
      for (int ix = 0; ix < material.length; ++ix) {
         for (int iy = 0; iy < material[0].length; ++iy) {
            for (int iz = 0; iz < material[0][0].length; ++iz) {
               if (axis == 0 && ix + 1 < material.length && material[ix][iy][iz] != material[ix + 1][iy][iz]) {
                  return true;
               }

               if (axis == 1 && iy + 1 < material[0].length && material[ix][iy][iz] != material[ix][iy + 1][iz]) {
                  return true;
               }

               if (axis == 2 && iz + 1 < material[0][0].length && material[ix][iy][iz] != material[ix][iy][iz + 1]) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static void assertVariesAlongAllAxes(DensityFunction function, String name) {
      double base = function.compute(context(HUGE_X, VEIN_Y, HUGE_Z));
      assertTrue(axisVaries(function, base, HUGE_X, VEIN_Y, HUGE_Z, 1, 0, 0), name + " must vary along the X axis");
      assertTrue(axisVaries(function, base, HUGE_X, VEIN_Y, HUGE_Z, 0, 1, 0), name + " must vary along the Y axis");
      assertTrue(axisVaries(function, base, HUGE_X, VEIN_Y, HUGE_Z, 0, 0, 1), name + " must vary along the Z axis");
   }

   private static boolean axisVaries(DensityFunction function, double base, long x, int y, long z, int dx, int dy, int dz) {
      for (int offset = 1; offset <= 32; ++offset) {
         double value = function.compute(context(x + (long)dx * offset, y + dy * offset, z + (long)dz * offset));
         if (bits(base) != bits(value)) {
            return true;
         }
      }

      return false;
   }

   private static boolean axisConstant(DensityFunction function, double base, long x, int y, long z, int dx, int dy, int dz) {
      for (int offset = 1; offset <= 8; ++offset) {
         double value = function.compute(context(x + (long)dx * offset, y + dy * offset, z + (long)dz * offset));
         if (bits(base) != bits(value)) {
            return false;
         }
      }

      return true;
   }
}
