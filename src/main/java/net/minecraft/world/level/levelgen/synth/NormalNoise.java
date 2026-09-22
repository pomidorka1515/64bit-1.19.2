package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleListIterator;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.RandomSource;

public class NormalNoise {
   private static final double INPUT_FACTOR = 1.0181268882175227D;
   private static final double TARGET_DEVIATION = 0.3333333333333333D;
   private final double valueFactor;
   private final PerlinNoise first;
   private final PerlinNoise second;
   private final double maxValue;
   private final NormalNoise.NoiseParameters parameters;

   /** @deprecated */
   @Deprecated
   public static NormalNoise createLegacyNetherBiome(RandomSource p_230509_, NormalNoise.NoiseParameters p_230510_) {
      return new NormalNoise(p_230509_, p_230510_, false);
   }

   public static NormalNoise create(RandomSource p_230505_, int p_230506_, double... p_230507_) {
      return create(p_230505_, new NormalNoise.NoiseParameters(p_230506_, new DoubleArrayList(p_230507_)));
   }

   public static NormalNoise create(RandomSource p_230512_, NormalNoise.NoiseParameters p_230513_) {
      return new NormalNoise(p_230512_, p_230513_, true);
   }

   private NormalNoise(RandomSource p_230501_, NormalNoise.NoiseParameters p_230502_, boolean p_230503_) {
      int i = p_230502_.firstOctave;
      DoubleList doublelist = p_230502_.amplitudes;
      this.parameters = p_230502_;
      if (p_230503_) {
         this.first = PerlinNoise.create(p_230501_, i, doublelist);
         this.second = PerlinNoise.create(p_230501_, i, doublelist);
      } else {
         this.first = PerlinNoise.createLegacyForLegacyNetherBiome(p_230501_, i, doublelist);
         this.second = PerlinNoise.createLegacyForLegacyNetherBiome(p_230501_, i, doublelist);
      }

      int j = Integer.MAX_VALUE;
      int k = Integer.MIN_VALUE;
      DoubleListIterator doublelistiterator = doublelist.iterator();

      while(doublelistiterator.hasNext()) {
         int l = doublelistiterator.nextIndex();
         double d0 = doublelistiterator.nextDouble();
         if (d0 != 0.0D) {
            j = Math.min(j, l);
            k = Math.max(k, l);
         }
      }

      this.valueFactor = 0.16666666666666666D / expectedDeviation(k - j);
      this.maxValue = (this.first.maxValue() + this.second.maxValue()) * this.valueFactor;
   }

   public double maxValue() {
      return this.maxValue;
   }

   private static double expectedDeviation(int p_75385_) {
      return 0.1D * (1.0D + 1.0D / (double)(p_75385_ + 1));
   }

   public double getValue(double p_75381_, double p_75382_, double p_75383_) {
      double d0 = p_75381_ * 1.0181268882175227D;
      double d1 = p_75382_ * 1.0181268882175227D;
      double d2 = p_75383_ * 1.0181268882175227D;
      return (this.first.getValue(p_75381_, p_75382_, p_75383_) + this.second.getValue(d0, d1, d2)) * this.valueFactor;
   }

   public double getValue(long cellX, double fracX, double y, long cellZ, double fracZ) {
      long cellY = (long)Math.floor(y);
      return this.getValue(cellX, fracX, cellY, y - (double)cellY, cellZ, fracZ);
   }

   public double getValue(long cellX, double fracX, long cellY, double fracY, long cellZ, double fracZ) {
      double factor = 1.0181268882175227D;
      PreciseNoiseCoordinate.Split scratch = PreciseNoiseCoordinate.threadScratch();
      PreciseNoiseCoordinate.scaledSplit(cellX, fracX, factor, scratch);
      long secondX = scratch.lattice;
      double secondFracX = scratch.fraction;
      PreciseNoiseCoordinate.scaledSplit(cellY, fracY, factor, scratch);
      long secondY = scratch.lattice;
      double secondFracY = scratch.fraction;
      PreciseNoiseCoordinate.scaledSplit(cellZ, fracZ, factor, scratch);
      long secondZ = scratch.lattice;
      double secondFracZ = scratch.fraction;
      return (this.first.getValue(cellX, fracX, cellY, fracY, cellZ, fracZ) + this.second.getValue(secondX, secondFracX, secondY, secondFracY, secondZ, secondFracZ)) * this.valueFactor;
   }

   public double getValueScaled(long blockX, double xzScale, double y, long blockZ) {
      long cellY = (long)Math.floor(y);
      return this.getValueScaled(blockX, xzScale, cellY, y - (double)cellY, blockZ);
   }

   /** Samples a block-coordinate noise function while splitting all three scaled axes. */
   public double getValueScaled(long blockX, double xzScale, long blockY, double yScale, long blockZ) {
      PreciseNoiseCoordinate.Split scratch = PreciseNoiseCoordinate.threadScratch();
      PreciseNoiseCoordinate.scale(blockX, xzScale, scratch);
      long firstX = scratch.lattice;
      double firstFracX = scratch.fraction;
      PreciseNoiseCoordinate.scale(blockY, yScale, scratch);
      long firstY = scratch.lattice;
      double firstFracY = scratch.fraction;
      PreciseNoiseCoordinate.scale(blockZ, xzScale, scratch);
      long firstZ = scratch.lattice;
      double firstFracZ = scratch.fraction;
      double factor = 1.0181268882175227D;
      PreciseNoiseCoordinate.scale(blockX, xzScale * factor, scratch);
      long secondX = scratch.lattice;
      double secondFracX = scratch.fraction;
      PreciseNoiseCoordinate.scale(blockY, yScale * factor, scratch);
      long secondY = scratch.lattice;
      double secondFracY = scratch.fraction;
      PreciseNoiseCoordinate.scale(blockZ, xzScale * factor, scratch);
      long secondZ = scratch.lattice;
      double secondFracZ = scratch.fraction;
      return (this.first.getValue(firstX, firstFracX, firstY, firstFracY, firstZ, firstFracZ) + this.second.getValue(secondX, secondFracX, secondY, secondFracY, secondZ, secondFracZ)) * this.valueFactor;
   }

   public double getValueScaledShifted(long blockX, double xzScale, double shiftX, double y, long blockZ, double shiftZ) {
      PreciseNoiseCoordinate.Split scratch = PreciseNoiseCoordinate.threadScratch();
      PreciseNoiseCoordinate.scale(blockX, xzScale, scratch);
      long shiftedFirstX = PreciseNoiseCoordinate.shiftedLattice(scratch.lattice, scratch.fraction, shiftX);
      double firstFracX = PreciseNoiseCoordinate.shiftedFraction(scratch.lattice, scratch.fraction, shiftX);
      PreciseNoiseCoordinate.scale(blockZ, xzScale, scratch);
      long shiftedFirstZ = PreciseNoiseCoordinate.shiftedLattice(scratch.lattice, scratch.fraction, shiftZ);
      double firstFracZ = PreciseNoiseCoordinate.shiftedFraction(scratch.lattice, scratch.fraction, shiftZ);
      double factor = 1.0181268882175227D;
      PreciseNoiseCoordinate.scale(blockX, xzScale * factor, scratch);
      long shiftedSecondX = PreciseNoiseCoordinate.shiftedLattice(scratch.lattice, scratch.fraction, shiftX * factor);
      double secondFracX = PreciseNoiseCoordinate.shiftedFraction(scratch.lattice, scratch.fraction, shiftX * factor);
      PreciseNoiseCoordinate.scale(blockZ, xzScale * factor, scratch);
      long shiftedSecondZ = PreciseNoiseCoordinate.shiftedLattice(scratch.lattice, scratch.fraction, shiftZ * factor);
      double secondFracZ = PreciseNoiseCoordinate.shiftedFraction(scratch.lattice, scratch.fraction, shiftZ * factor);
      return (this.first.getValue(shiftedFirstX, firstFracX, y, shiftedFirstZ, firstFracZ) + this.second.getValue(shiftedSecondX, secondFracX, y * factor, shiftedSecondZ, secondFracZ)) * this.valueFactor;
   }

   public NormalNoise.NoiseParameters parameters() {
      return this.parameters;
   }

   @VisibleForTesting
   public void parityConfigString(StringBuilder p_192847_) {
      p_192847_.append("NormalNoise {");
      p_192847_.append("first: ");
      this.first.parityConfigString(p_192847_);
      p_192847_.append(", second: ");
      this.second.parityConfigString(p_192847_);
      p_192847_.append("}");
   }

   public static record NoiseParameters(int firstOctave, DoubleList amplitudes) {
      public static final Codec<NormalNoise.NoiseParameters> DIRECT_CODEC = RecordCodecBuilder.create((p_192865_) -> {
         return p_192865_.group(Codec.INT.fieldOf("firstOctave").forGetter(NormalNoise.NoiseParameters::firstOctave), Codec.DOUBLE.listOf().fieldOf("amplitudes").forGetter(NormalNoise.NoiseParameters::amplitudes)).apply(p_192865_, NormalNoise.NoiseParameters::new);
      });
      public static final Codec<Holder<NormalNoise.NoiseParameters>> CODEC = RegistryFileCodec.create(Registry.NOISE_REGISTRY, DIRECT_CODEC);

      public NoiseParameters(int p_192861_, List<Double> p_192862_) {
         this(p_192861_, new DoubleArrayList(p_192862_));
      }

      public NoiseParameters(int p_192857_, double p_192858_, double... p_192859_) {
         this(p_192857_, Util.make(new DoubleArrayList(p_192859_), (p_210636_) -> {
            p_210636_.add(0, p_192858_);
         }));
      }
   }
}