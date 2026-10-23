package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import java.util.BitSet;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.synth.FarlandsMode;

public class OreFeature extends Feature<OreConfiguration> {
    public OreFeature(Codec<OreConfiguration> pCodec) {
        super(pCodec);
    }

    public boolean place(FeaturePlaceContext<OreConfiguration> pContext) {
        RandomSource randomsource = pContext.random();
        BlockPos blockpos = pContext.origin();
        WorldGenLevel worldgenlevel = pContext.level();
        OreConfiguration oreconfiguration = pContext.config();
        float f = randomsource.nextFloat() * (float) Math.PI;
        float f1 = (float) oreconfiguration.size / 8.0F;
        int i = Mth.ceil(((float) oreconfiguration.size / 16.0F * 2.0F + 1.0F) / 2.0F);
        // Precise mode keeps horizontal ellipsoid endpoints relative to the exact block
        // origin: adding a small radius to a far-away long coordinate as a double rounds
        // both endpoints onto the same value, crushing the blob into a 2D planar sheet.
        // All other modes retain the legacy absolute-double endpoints untouched.
        boolean preciseEllipsoid = FarlandsMode.usesPreciseCoordinates();
        boolean wide = blockpos.getXHi() != (blockpos.getXLo() >> 63) || blockpos.getZHi() != (blockpos.getZLo() >> 63);
        long centerX = wide ? 0L : blockpos.getXLo();
        long centerZ = wide ? 0L : blockpos.getZLo();
        double d0;
        double d1;
        double d2;
        double d3;
        if (preciseEllipsoid) {
            d0 = Math.sin((double) f) * (double) f1;
            d1 = -d0;
            d2 = Math.cos((double) f) * (double) f1;
            d3 = -d2;
        } else {
            double absoluteX = net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.toDouble(blockpos.getXHi(), blockpos.getXLo());
            double absoluteZ = net.minecraft.world.level.levelgen.synth.WorldgenCoordinate.toDouble(blockpos.getZHi(), blockpos.getZLo());
            d0 = WorldBounds.clampAbsoluteDouble(absoluteX + Math.sin((double) f) * (double) f1);
            d1 = WorldBounds.clampAbsoluteDouble(absoluteX - Math.sin((double) f) * (double) f1);
            d2 = WorldBounds.clampAbsoluteDouble(absoluteZ + Math.cos((double) f) * (double) f1);
            d3 = WorldBounds.clampAbsoluteDouble(absoluteZ - Math.cos((double) f) * (double) f1);
            if (wide) {
                // Keep floating-point rounding in non-precise modes, but do not
                // subtract a rounded 128-bit endpoint from its clamped value:
                // that would move the entire vein far outside the local frame.
                d0 = (absoluteX + Math.sin((double) f) * (double) f1) - absoluteX;
                d1 = (absoluteX - Math.sin((double) f) * (double) f1) - absoluteX;
                d2 = (absoluteZ + Math.cos((double) f) * (double) f1) - absoluteZ;
                d3 = (absoluteZ - Math.cos((double) f) * (double) f1) - absoluteZ;
            }
        }
        int j = 2;
        double d4 = (double) (blockpos.getY() + randomsource.nextInt(3) - 2);
        double d5 = (double) (blockpos.getY() + randomsource.nextInt(3) - 2);
        long k = WorldBounds.addBlockOffset(centerX, -(long) Mth.ceil(f1) - i);
        int l = blockpos.getY() - 2 - i;
        long i1 = WorldBounds.addBlockOffset(centerZ, -(long) Mth.ceil(f1) - i);
        int j1 = 2 * (Mth.ceil(f1) + i);
        int k1 = 2 * (2 + i);

        long maxX = WorldBounds.addBlockOffset(k, j1);
        long maxZ = WorldBounds.addBlockOffset(i1, j1);
        for (long l1 = k;; l1 = WorldBounds.addBlockOffset(l1, 1L)) {
            for (long i2 = i1;; i2 = WorldBounds.addBlockOffset(i2, 1L)) {
                if (l <= worldgenlevel.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,
                        WorldBounds.addBlockOffset(blockpos.getX(), l1 - centerX),
                        WorldBounds.addBlockOffset(blockpos.getZ(), i2 - centerZ))) {
                    return this.doPlace(worldgenlevel, randomsource, oreconfiguration, d0, d1, d2, d3, d4, d5, centerX, centerZ, preciseEllipsoid, k, l, i1, j1, k1, blockpos);
                }
                if (i2 == maxZ) {
                    break;
                }
            }
            if (l1 == maxX) {
                break;
            }
        }

        return false;
    }

    protected boolean doPlace(WorldGenLevel pLevel, RandomSource pRandom, OreConfiguration pConfig, double pMinX, double pMaxX, double pMinZ, double pMaxZ, double pMinY, double pMaxY, long centerX, long centerZ, boolean preciseEllipsoid, long pX, int pY, long pZ, int pWidth, int pHeight) {
        return this.doPlace(pLevel, pRandom, pConfig, pMinX, pMaxX, pMinZ, pMaxZ, pMinY, pMaxY, centerX, centerZ, preciseEllipsoid, pX, pY, pZ, pWidth, pHeight, new BlockPos(centerX, pY, centerZ));
    }

    private boolean doPlace(WorldGenLevel pLevel, RandomSource pRandom, OreConfiguration pConfig, double pMinX, double pMaxX, double pMinZ, double pMaxZ, double pMinY, double pMaxY, long centerX, long centerZ, boolean preciseEllipsoid, long pX, int pY, long pZ, int pWidth, int pHeight, BlockPos exactOrigin) {
        int i = 0;
        BitSet bitset = new BitSet(pWidth * pHeight * pWidth);
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        int j = pConfig.size;
        double[] adouble = new double[j * 4];

        for (int k = 0; k < j; ++k) {
            float f = (float) k / (float) j;
            double d0 = Mth.lerp((double) f, pMinX, pMaxX);
            double d1 = Mth.lerp((double) f, pMinY, pMaxY);
            double d2 = Mth.lerp((double) f, pMinZ, pMaxZ);
            double d3 = pRandom.nextDouble() * (double) j / 16.0D;
            double d4 = ((double) (Mth.sin((float) Math.PI * f) + 1.0F) * d3 + 1.0D) / 2.0D;
            adouble[k * 4 + 0] = d0;
            adouble[k * 4 + 1] = d1;
            adouble[k * 4 + 2] = d2;
            adouble[k * 4 + 3] = d4;
        }

        for (int l3 = 0; l3 < j - 1; ++l3) {
            if (!(adouble[l3 * 4 + 3] <= 0.0D)) {
                for (int i4 = l3 + 1; i4 < j; ++i4) {
                    if (!(adouble[i4 * 4 + 3] <= 0.0D)) {
                        double d8 = adouble[l3 * 4 + 0] - adouble[i4 * 4 + 0];
                        double d10 = adouble[l3 * 4 + 1] - adouble[i4 * 4 + 1];
                        double d12 = adouble[l3 * 4 + 2] - adouble[i4 * 4 + 2];
                        double d14 = adouble[l3 * 4 + 3] - adouble[i4 * 4 + 3];
                        if (d14 * d14 > d8 * d8 + d10 * d10 + d12 * d12) {
                            if (d14 > 0.0D) {
                                adouble[i4 * 4 + 3] = -1.0D;
                            } else {
                                adouble[l3 * 4 + 3] = -1.0D;
                            }
                        }
                    }
                }
            }
        }

        BulkSectionAccess bulksectionaccess = new BulkSectionAccess(pLevel);

        try {
            for (int j4 = 0; j4 < j; ++j4) {
                double d9 = adouble[j4 * 4 + 3];
                if (!(d9 < 0.0D)) {
                    double d11 = adouble[j4 * 4 + 0];
                    double d13 = adouble[j4 * 4 + 1];
                    double d15 = adouble[j4 * 4 + 2];
                    long k4;
                    int l;
                    long i1;
                    long j1;
                    int k1;
                    long l1;
                    if (preciseEllipsoid) {
                        // d11/d15 are origin-relative offsets; expand them from the exact long center
                        // so the X/Z spans cannot be crushed together by absolute-double rounding.
                        k4 = Math.max(WorldBounds.addBlockOffset(centerX, safeFloor(d11 - d9)), pX);
                        l = Math.max(Mth.floor(d13 - d9), pY);
                        i1 = Math.max(WorldBounds.addBlockOffset(centerZ, safeFloor(d15 - d9)), pZ);
                        j1 = Math.max(WorldBounds.addBlockOffset(centerX, safeFloor(d11 + d9)), k4);
                        k1 = Math.max(Mth.floor(d13 + d9), l);
                        l1 = Math.max(WorldBounds.addBlockOffset(centerZ, safeFloor(d15 + d9)), i1);
                    } else {
                        k4 = Math.max(safeFloor(d11 - d9), pX);
                        l = Math.max(Mth.floor(d13 - d9), pY);
                        i1 = Math.max(safeFloor(d15 - d9), pZ);
                        j1 = Math.max(safeFloor(d11 + d9), k4);
                        k1 = Math.max(Mth.floor(d13 + d9), l);
                        l1 = Math.max(safeFloor(d15 + d9), i1);
                    }

                    for (long i2 = k4;; i2 = WorldBounds.addBlockOffset(i2, 1L)) {
                        double d5 = ((preciseEllipsoid ? WorldBounds.signedDifference(i2, centerX) : (double) i2) + 0.5D - d11) / d9;
                        if (d5 * d5 < 1.0D) {
                            for (int j2 = l; j2 <= k1; ++j2) {
                                double d6 = ((double) j2 + 0.5D - d13) / d9;
                                if (d5 * d5 + d6 * d6 < 1.0D) {
                                    for (long k2 = i1;; k2 = WorldBounds.addBlockOffset(k2, 1L)) {
                                        double d7 = ((preciseEllipsoid ? WorldBounds.signedDifference(k2, centerZ) : (double) k2) + 0.5D - d15) / d9;
                                        if (d5 * d5 + d6 * d6 + d7 * d7 < 1.0D && !pLevel.isOutsideBuildHeight(j2)) {
                                            long l2 = (long) WorldBounds.signedDifference(i2, pX) + (j2 - pY) * pWidth + (long) WorldBounds.signedDifference(k2, pZ) * pWidth * pHeight;
                                            if (!bitset.get((int) l2)) {
                                                bitset.set((int) l2);
                                                // Only ellipsoid content math is projected. Resolve world sections
                                                // and neighbor reads using the original full-width anchor.
                                                blockpos$mutableblockpos.set(WorldBounds.addBlockOffset(exactOrigin.getX(), i2 - centerX), j2,
                                                        WorldBounds.addBlockOffset(exactOrigin.getZ(), k2 - centerZ));
                                                if (pLevel.ensureCanWrite(blockpos$mutableblockpos)) {
                                                    LevelChunkSection levelchunksection = bulksectionaccess.getSection(blockpos$mutableblockpos);
                                                    if (levelchunksection != null) {
                                                        int i3 = SectionPos.sectionRelative(blockpos$mutableblockpos.getXLo());
                                                        int j3 = SectionPos.sectionRelative(j2);
                                                        int k3 = SectionPos.sectionRelative(blockpos$mutableblockpos.getZLo());
                                                        BlockState blockstate = levelchunksection.getBlockState(i3, j3, k3);

                                                        for (OreConfiguration.TargetBlockState oreconfiguration$targetblockstate : pConfig.targetStates) {
                                                            if (canPlaceOre(blockstate, bulksectionaccess::getBlockState, pRandom, pConfig, oreconfiguration$targetblockstate, blockpos$mutableblockpos)) {
                                                                levelchunksection.setBlockState(i3, j3, k3, oreconfiguration$targetblockstate.state, false);
                                                                ++i;
                                                                break;
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        if (k2 == l1) {
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        if (i2 == j1) {
                            break;
                        }
                    }
                }
            }
        } catch (Throwable throwable1) {
            try {
                bulksectionaccess.close();
            } catch (Throwable throwable) {
                throwable1.addSuppressed(throwable);
            }

            throw throwable1;
        }

        bulksectionaccess.close();
        return i > 0;
    }

    private static long safeFloor(double coordinate) {
        return Mth.lfloor(WorldBounds.clampAbsoluteDouble(coordinate));
    }

    public static boolean canPlaceOre(BlockState pState, Function<BlockPos, BlockState> pAdjacentStateAccessor, RandomSource pRandom, OreConfiguration pConfig, OreConfiguration.TargetBlockState pTargetState, BlockPos.MutableBlockPos pMutablePos) {
        if (!pTargetState.target.test(pState, pRandom)) {
            return false;
        } else if (shouldSkipAirCheck(pRandom, pConfig.discardChanceOnAirExposure)) {
            return true;
        } else {
            return !isAdjacentToAir(pAdjacentStateAccessor, pMutablePos);
        }
    }

    protected static boolean shouldSkipAirCheck(RandomSource pRandom, float pChance) {
        if (pChance <= 0.0F) {
            return true;
        } else if (pChance >= 1.0F) {
            return false;
        } else {
            return pRandom.nextFloat() >= pChance;
        }
    }
}
