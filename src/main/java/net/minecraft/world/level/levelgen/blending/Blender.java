package net.minecraft.world.level.levelgen.blending;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction8;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ChunkPosMap;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.material.FluidState;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.commons.lang3.mutable.MutableObject;

public class Blender {
    private static Int1088 coordinate(DensityFunction.FunctionContext context, boolean x) {
        return net.minecraft.world.level.levelgen.ContextCoordinates.copy(context, x);
    }
    private static final Blender EMPTY = new Blender(new ChunkPosMap<>(), new ChunkPosMap<>()) {
        public Blender.BlendingOutput blendOffsetAndFactor(Int1088 p_209724_, Int1088 p_209725_) {
            return new Blender.BlendingOutput(1.0D, 0.0D);
        }

        public double blendDensity(DensityFunction.FunctionContext p_209727_, double p_209728_) {
            return p_209728_;
        }

        public BiomeResolver getBiomeResolver(BiomeResolver p_190232_) {
            return p_190232_;
        }
    };
    private static final NormalNoise SHIFT_NOISE = NormalNoise.create(new XoroshiroRandomSource(42L), BuiltinRegistries.NOISE.getOrThrow(Noises.SHIFT));
    private static final int HEIGHT_BLENDING_RANGE_CELLS = QuartPos.fromSection(7) - 1;
    private static final int HEIGHT_BLENDING_RANGE_CHUNKS = QuartPos.toSection(HEIGHT_BLENDING_RANGE_CELLS + 3);
    private static final int DENSITY_BLENDING_RANGE_CELLS = 2;
    private static final int DENSITY_BLENDING_RANGE_CHUNKS = QuartPos.toSection(5);
    private static final double OLD_CHUNK_XZ_RADIUS = 8.0D;
    private final ChunkPosMap<BlendingData> heightAndBiomeBlendingData;
    private final ChunkPosMap<BlendingData> densityBlendingData;

    public static Blender empty() {
        return EMPTY;
    }

    public static Blender of(@Nullable WorldGenRegion p_190203_) {
        if (p_190203_ == null) {
            return EMPTY;
        } else {
            ChunkPos chunkpos = p_190203_.getCenter();
            if (!p_190203_.isOldChunkAround(chunkpos, HEIGHT_BLENDING_RANGE_CHUNKS)) {
                return EMPTY;
            } else {
                ChunkPosMap<BlendingData> long2objectopenhashmap = new ChunkPosMap<>();
                ChunkPosMap<BlendingData> long2objectopenhashmap1 = new ChunkPosMap<>();
                int i = Mth.square(HEIGHT_BLENDING_RANGE_CHUNKS + 1);

                for (int j = -HEIGHT_BLENDING_RANGE_CHUNKS; j <= HEIGHT_BLENDING_RANGE_CHUNKS; ++j) {
                    for (int k = -HEIGHT_BLENDING_RANGE_CHUNKS; k <= HEIGHT_BLENDING_RANGE_CHUNKS; ++k) {
                        if (j * j + k * k <= i) {
                            Int1088 l = WorldBounds.addChunkOffset(chunkpos.x(), j);
                            Int1088 i1 = WorldBounds.addChunkOffset(chunkpos.z(), k);
                            BlendingData blendingdata = BlendingData.getOrUpdateBlendingData(p_190203_, l, i1);
                            if (blendingdata != null) {
                                long2objectopenhashmap.put(new ChunkPos(l, i1), blendingdata);
                                if (j >= -DENSITY_BLENDING_RANGE_CHUNKS && j <= DENSITY_BLENDING_RANGE_CHUNKS && k >= -DENSITY_BLENDING_RANGE_CHUNKS && k <= DENSITY_BLENDING_RANGE_CHUNKS) {
                                    long2objectopenhashmap1.put(new ChunkPos(l, i1), blendingdata);
                                }
                            }
                        }
                    }
                }

                return long2objectopenhashmap.isEmpty() && long2objectopenhashmap1.isEmpty() ? EMPTY : new Blender(long2objectopenhashmap, long2objectopenhashmap1);
            }
        }
    }

    Blender(ChunkPosMap<BlendingData> pBlendingData, ChunkPosMap<BlendingData> pBlendingDataForDensityBlending) {
        this.heightAndBiomeBlendingData = pBlendingData;
        this.densityBlendingData = pBlendingDataForDensityBlending;
    }

    public Blender.BlendingOutput blendOffsetAndFactor(long p_209719_, long p_209720_) {
        return blendOffsetAndFactor(Int1088.fromLong(p_209719_), Int1088.fromLong(p_209720_));
    }

    public Blender.BlendingOutput blendOffsetAndFactor(Int1088 x, Int1088 z) {
        Int1088 i = x.shiftRight(2);
        Int1088 j = z.shiftRight(2);
        double d0 = this.getBlendingDataValue(i, 0, j, BlendingData::getHeight);
        if (d0 != Double.MAX_VALUE) {
            return new Blender.BlendingOutput(0.0D, heightToOffset(d0));
        } else {
            MutableDouble mutabledouble = new MutableDouble(0.0D);
            MutableDouble mutabledouble1 = new MutableDouble(0.0D);
            MutableDouble mutabledouble2 = new MutableDouble(Double.POSITIVE_INFINITY);
            this.heightAndBiomeBlendingData.forEach((p_202249_, p_202250_) -> {
                Int1088 originX = p_202249_.getMinBlockX().shiftRight(2);
                Int1088 originZ = p_202249_.getMinBlockZ().shiftRight(2);
                p_202250_.iterateHeights(0L, 0L, (p_190199_, p_190200_, p_190201_) -> {
                    double d3 = Mth.length(WorldBounds.signedDifference(i, WorldBounds.addBlockOffset(originX, p_190199_)), WorldBounds.signedDifference(j, WorldBounds.addBlockOffset(originZ, p_190200_)));
                    if (!(d3 > (double) HEIGHT_BLENDING_RANGE_CELLS)) {
                        if (d3 < mutabledouble2.doubleValue()) {
                            mutabledouble2.setValue(d3);
                        }

                        double d4 = 1.0D / (d3 * d3 * d3 * d3);
                        mutabledouble1.add(p_190201_ * d4);
                        mutabledouble.add(d4);
                    }
                });
            });
            if (mutabledouble2.doubleValue() == Double.POSITIVE_INFINITY) {
                return new Blender.BlendingOutput(1.0D, 0.0D);
            } else {
                double d1 = mutabledouble1.doubleValue() / mutabledouble.doubleValue();
                double d2 = Mth.clamp(mutabledouble2.doubleValue() / (double) (HEIGHT_BLENDING_RANGE_CELLS + 1), 0.0D, 1.0D);
                d2 = 3.0D * d2 * d2 - 2.0D * d2 * d2 * d2;
                return new Blender.BlendingOutput(d2, heightToOffset(d1));
            }
        }
    }

    private static double heightToOffset(double pHeight) {
        double d0 = 1.0D;
        double d1 = pHeight + 0.5D;
        double d2 = Mth.positiveModulo(d1, 8.0D);
        return 1.0D * (32.0D * (d1 - 128.0D) - 3.0D * (d1 - 120.0D) * d2 + 3.0D * d2 * d2) / (128.0D * (32.0D - 3.0D * d2));
    }

    public double blendDensity(DensityFunction.FunctionContext p_209721_, double p_209722_) {
        Int1088 i = coordinate(p_209721_, true).shiftRight(2);
        int j = p_209721_.blockY() / 8;
        Int1088 k = coordinate(p_209721_, false).shiftRight(2);
        double d0 = this.getBlendingDataValue(i, j, k, BlendingData::getDensity);
        if (d0 != Double.MAX_VALUE) {
            return d0;
        } else {
            MutableDouble mutabledouble = new MutableDouble(0.0D);
            MutableDouble mutabledouble1 = new MutableDouble(0.0D);
            MutableDouble mutabledouble2 = new MutableDouble(Double.POSITIVE_INFINITY);
            this.densityBlendingData.forEach((p_202241_, p_202242_) -> {
                Int1088 originX = p_202241_.getMinBlockX().shiftRight(2);
                Int1088 originZ = p_202241_.getMinBlockZ().shiftRight(2);
                p_202242_.iterateDensities(0L, 0L, j - 1, j + 1, (p_202230_, p_202231_, p_202232_, p_202233_) -> {
                    double d3 = Mth.length(WorldBounds.signedDifference(i, WorldBounds.addBlockOffset(originX, p_202230_)), (double) ((j - p_202231_) * 2), WorldBounds.signedDifference(k, WorldBounds.addBlockOffset(originZ, p_202232_)));
                    if (!(d3 > 2.0D)) {
                        if (d3 < mutabledouble2.doubleValue()) {
                            mutabledouble2.setValue(d3);
                        }

                        double d4 = 1.0D / (d3 * d3 * d3 * d3);
                        mutabledouble1.add(p_202233_ * d4);
                        mutabledouble.add(d4);
                    }
                });
            });
            if (mutabledouble2.doubleValue() == Double.POSITIVE_INFINITY) {
                return p_209722_;
            } else {
                double d1 = mutabledouble1.doubleValue() / mutabledouble.doubleValue();
                double d2 = Mth.clamp(mutabledouble2.doubleValue() / 3.0D, 0.0D, 1.0D);
                return Mth.lerp(d2, d1, p_209722_);
            }
        }
    }

    private double getBlendingDataValue(Int1088 p_190175_, int p_190176_, Int1088 p_190177_, Blender.CellValueGetter p_190178_) {
        Int1088 i = p_190175_.shiftRight(2);
        Int1088 j = p_190177_.shiftRight(2);
        boolean flag = (p_190175_.limb(0) & 3) == 0;
        boolean flag1 = (p_190177_.limb(0) & 3) == 0;
        double d0 = this.getBlendingDataValue(p_190178_, i, j, p_190175_, p_190176_, p_190177_);
        if (d0 == Double.MAX_VALUE) {
            if (flag && flag1) {
                d0 = this.getBlendingDataValue(p_190178_, WorldBounds.addChunkOffset(i, -1), WorldBounds.addChunkOffset(j, -1), p_190175_, p_190176_, p_190177_);
            }

            if (d0 == Double.MAX_VALUE) {
                if (flag) {
                    d0 = this.getBlendingDataValue(p_190178_, WorldBounds.addChunkOffset(i, -1), j, p_190175_, p_190176_, p_190177_);
                }

                if (d0 == Double.MAX_VALUE && flag1) {
                    d0 = this.getBlendingDataValue(p_190178_, i, WorldBounds.addChunkOffset(j, -1), p_190175_, p_190176_, p_190177_);
                }
            }
        }

        return d0;
    }

    private double getBlendingDataValue(Blender.CellValueGetter getter, Int1088 chunkX, Int1088 chunkZ, Int1088 x, int y, Int1088 z) {
        BlendingData data = this.heightAndBiomeBlendingData.get(new ChunkPos(chunkX, chunkZ));
        return data == null ? Double.MAX_VALUE : getter.get(data, (int) WorldBounds.signedDifferenceAsLong(x, chunkX.shiftLeftSaturated(2)), y, (int) WorldBounds.signedDifferenceAsLong(z, chunkZ.shiftLeftSaturated(2)));
    }

    public BiomeResolver getBiomeResolver(BiomeResolver p_190204_) {
        return (p_204669_, p_204670_, p_204671_, p_204672_) -> {
            Holder<Biome> holder = this.blendBiome(p_204669_, p_204670_, p_204671_);
            return holder == null ? p_190204_.getNoiseBiome(p_204669_, p_204670_, p_204671_, p_204672_) : holder;
        };
    }

    @Nullable
    private Holder<Biome> blendBiome(Int1088 p_224707_, int p_224708_, Int1088 p_224709_) {
        MutableDouble mutabledouble = new MutableDouble(Double.POSITIVE_INFINITY);
        MutableObject<Holder<Biome>> mutableobject = new MutableObject<>();
        this.heightAndBiomeBlendingData.forEach((p_224716_, p_224717_) -> {
            Int1088 originX = p_224716_.getMinBlockX().shiftRight(2);
            Int1088 originZ = p_224716_.getMinBlockZ().shiftRight(2);
            p_224717_.iterateBiomes(0L, p_224708_, 0L, (p_224723_, p_224724_, p_224725_) -> {
                double d2 = Mth.length(WorldBounds.signedDifference(p_224707_, WorldBounds.addBlockOffset(originX, p_224723_)), WorldBounds.signedDifference(p_224709_, WorldBounds.addBlockOffset(originZ, p_224724_)));
                if (!(d2 > (double) HEIGHT_BLENDING_RANGE_CELLS)) {
                    if (d2 < mutabledouble.doubleValue()) {
                        mutableobject.setValue(p_224725_);
                        mutabledouble.setValue(d2);
                    }
                }
            });
        });
        if (mutabledouble.doubleValue() == Double.POSITIVE_INFINITY) {
            return null;
        } else {
            double d0 = SHIFT_NOISE.getValueScaled(p_224707_, 1.0D, 0L, 0.0D, p_224709_) * 12.0D;
            double d1 = Mth.clamp((mutabledouble.doubleValue() + d0) / (double) (HEIGHT_BLENDING_RANGE_CELLS + 1), 0.0D, 1.0D);
            return d1 > 0.5D ? null : mutableobject.getValue();
        }
    }

    public static void generateBorderTicks(WorldGenRegion p_197032_, ChunkAccess p_197033_) {
        ChunkPos chunkpos = p_197033_.getPos();
        boolean flag = p_197033_.isOldNoiseGeneration();
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        BlockPos blockpos = new BlockPos(chunkpos.getMinBlockX(), 0, chunkpos.getMinBlockZ());
        BlendingData blendingdata = p_197033_.getBlendingData();
        if (blendingdata != null) {
            int i = blendingdata.getAreaWithOldGeneration().getMinBuildHeight();
            int j = blendingdata.getAreaWithOldGeneration().getMaxBuildHeight() - 1;
            if (flag) {
                for (int k = 0; k < 16; ++k) {
                    for (int l = 0; l < 16; ++l) {
                        generateBorderTick(p_197033_, blockpos$mutableblockpos.setWithOffset(blockpos, k, i - 1, l));
                        generateBorderTick(p_197033_, blockpos$mutableblockpos.setWithOffset(blockpos, k, i, l));
                        generateBorderTick(p_197033_, blockpos$mutableblockpos.setWithOffset(blockpos, k, j, l));
                        generateBorderTick(p_197033_, blockpos$mutableblockpos.setWithOffset(blockpos, k, j + 1, l));
                    }
                }
            }

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (p_197032_.getChunk(WorldBounds.addChunkOffset(chunkpos.x(), direction.getStepX()), WorldBounds.addChunkOffset(chunkpos.z(), direction.getStepZ())).isOldNoiseGeneration() != flag) {
                    int i1 = direction == Direction.EAST ? 15 : 0;
                    int j1 = direction == Direction.WEST ? 0 : 15;
                    int k1 = direction == Direction.SOUTH ? 15 : 0;
                    int l1 = direction == Direction.NORTH ? 0 : 15;

                    for (int i2 = i1; i2 <= j1; ++i2) {
                        for (int j2 = k1; j2 <= l1; ++j2) {
                            int k2 = Math.min(j, p_197033_.getHeight(Heightmap.Types.MOTION_BLOCKING, i2, j2)) + 1;

                            for (int l2 = i; l2 < k2; ++l2) {
                                generateBorderTick(p_197033_, blockpos$mutableblockpos.setWithOffset(blockpos, i2, l2, j2));
                            }
                        }
                    }
                }
            }
        }
    }

    private static void generateBorderTick(ChunkAccess p_197041_, BlockPos p_197042_) {
        BlockState blockstate = p_197041_.getBlockState(p_197042_);
        if (blockstate.is(BlockTags.LEAVES)) {
            p_197041_.markPosForPostprocessing(p_197042_);
        }

        FluidState fluidstate = p_197041_.getFluidState(p_197042_);
        if (!fluidstate.isEmpty()) {
            p_197041_.markPosForPostprocessing(p_197042_);
        }
    }

    public static void addAroundOldChunksCarvingMaskFilter(WorldGenLevel p_197035_, ProtoChunk p_197036_) {
        ChunkPos chunkpos = p_197036_.getPos();
        ImmutableMap.Builder<Direction8, BlendingData> builder = ImmutableMap.builder();

        for (Direction8 direction8 : Direction8.values()) {
            Int1088 i = WorldBounds.addChunkOffset(chunkpos.x(), direction8.getStepX());
            Int1088 j = WorldBounds.addChunkOffset(chunkpos.z(), direction8.getStepZ());
            BlendingData blendingdata = p_197035_.getChunk(i, j).getBlendingData();
            if (blendingdata != null) {
                builder.put(direction8, blendingdata);
            }
        }

        ImmutableMap<Direction8, BlendingData> immutablemap = builder.build();
        if (p_197036_.isOldNoiseGeneration() || !immutablemap.isEmpty()) {
            Blender.DistanceGetter blender$distancegetter = makeOldChunkDistanceGetter(p_197036_.getBlendingData(), immutablemap);
            CarvingMask.Mask carvingmask$mask = (p_202262_, p_202263_, p_202264_) -> {
                double d0 = (double) p_202262_ + 0.5D + SHIFT_NOISE.getValue((double) p_202262_, (double) p_202263_, (double) p_202264_) * 4.0D;
                double d1 = (double) p_202263_ + 0.5D + SHIFT_NOISE.getValue((double) p_202263_, (double) p_202264_, (double) p_202262_) * 4.0D;
                double d2 = (double) p_202264_ + 0.5D + SHIFT_NOISE.getValue((double) p_202264_, (double) p_202262_, (double) p_202263_) * 4.0D;
                return blender$distancegetter.getDistance(d0, d1, d2) < 4.0D;
            };
            Stream.of(GenerationStep.Carving.values()).map(p_197036_::getOrCreateCarvingMask).forEach((p_202259_) -> {
                p_202259_.setAdditionalMask(carvingmask$mask);
            });
        }
    }

    public static Blender.DistanceGetter makeOldChunkDistanceGetter(@Nullable BlendingData p_224727_, Map<Direction8, BlendingData> p_224728_) {
        List<Blender.DistanceGetter> list = Lists.newArrayList();
        if (p_224727_ != null) {
            list.add(makeOffsetOldChunkDistanceGetter((Direction8) null, p_224727_));
        }

        p_224728_.forEach((p_224734_, p_224735_) -> {
            list.add(makeOffsetOldChunkDistanceGetter(p_224734_, p_224735_));
        });
        return (p_202267_, p_202268_, p_202269_) -> {
            double d0 = Double.POSITIVE_INFINITY;

            for (Blender.DistanceGetter blender$distancegetter : list) {
                double d1 = blender$distancegetter.getDistance(p_202267_, p_202268_, p_202269_);
                if (d1 < d0) {
                    d0 = d1;
                }
            }

            return d0;
        };
    }

    private static Blender.DistanceGetter makeOffsetOldChunkDistanceGetter(@Nullable Direction8 p_224730_, BlendingData p_224731_) {
        double d0 = 0.0D;
        double d1 = 0.0D;
        if (p_224730_ != null) {
            for (Direction direction : p_224730_.getDirections()) {
                d0 += (double) (direction.getStepX() * 16);
                d1 += (double) (direction.getStepZ() * 16);
            }
        }

        double d5 = d0;
        double d2 = d1;
        double d3 = (double) p_224731_.getAreaWithOldGeneration().getHeight() / 2.0D;
        double d4 = (double) p_224731_.getAreaWithOldGeneration().getMinBuildHeight() + d3;
        return (p_224703_, p_224704_, p_224705_) -> {
            return distanceToCube(p_224703_ - 8.0D - d5, p_224704_ - d4, p_224705_ - 8.0D - d2, 8.0D, d3, 8.0D);
        };
    }

    private static double distanceToCube(double p_197025_, double p_197026_, double p_197027_, double p_197028_, double p_197029_, double p_197030_) {
        double d0 = Math.abs(p_197025_) - p_197028_;
        double d1 = Math.abs(p_197026_) - p_197029_;
        double d2 = Math.abs(p_197027_) - p_197030_;
        return Mth.length(Math.max(0.0D, d0), Math.max(0.0D, d1), Math.max(0.0D, d2));
    }

    public static record BlendingOutput(double alpha, double blendingOffset) {
    }

    interface CellValueGetter {
        double get(BlendingData p_190234_, int p_190235_, int p_190236_, int p_190237_);
    }

    public interface DistanceGetter {
        double getDistance(double p_197062_, double p_197063_, double p_197064_);
    }
}
