package net.minecraft.world.level.levelgen;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.Util;
import net.minecraft.core.Int1088;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

public class Beardifier implements DensityFunctions.BeardifierOrMarker {
    public static final int BEARD_KERNEL_RADIUS = 12;
    private static final int BEARD_KERNEL_SIZE = 24;
    private static final float[] BEARD_KERNEL = Util.make(new float[13824], (p_158082_) -> {
        for (int i = 0; i < 24; ++i) {
            for (int j = 0; j < 24; ++j) {
                for (int k = 0; k < 24; ++k) {
                    p_158082_[i * 24 * 24 + j * 24 + k] = (float) computeBeardContribution(j - 12, k - 12, i - 12);
                }
            }
        }
    });
    private final ObjectListIterator<Beardifier.Rigid> pieceIterator;
    private final ObjectListIterator<JigsawJunction> junctionIterator;

    public static Beardifier forStructuresInChunk(StructureManager pStructureManager, ChunkPos pChunkPos) {
        Int1088 i = pChunkPos.getMinBlockX();
        Int1088 j = pChunkPos.getMinBlockZ();
        ObjectList<Beardifier.Rigid> objectlist = new ObjectArrayList<>(10);
        ObjectList<JigsawJunction> objectlist1 = new ObjectArrayList<>(32);
        pStructureManager.startsForStructure(pChunkPos, (p_223941_) -> {
                     return p_223941_.terrainAdaptation() != TerrainAdjustment.NONE;
                 }).forEach((p_223936_) -> {
            TerrainAdjustment terrainadjustment = p_223936_.getStructure().terrainAdaptation();

            for (StructurePiece structurepiece : p_223936_.getPieces()) {
                if (structurepiece.isCloseToChunk(pChunkPos, 12)) {
                    if (structurepiece instanceof PoolElementStructurePiece) {
                        PoolElementStructurePiece poolelementstructurepiece = (PoolElementStructurePiece) structurepiece;
                        StructureTemplatePool.Projection structuretemplatepool$projection = poolelementstructurepiece.getElement().getProjection();
                        if (structuretemplatepool$projection == StructureTemplatePool.Projection.RIGID) {
                            objectlist.add(new Beardifier.Rigid(poolelementstructurepiece.getBoundingBox(), terrainadjustment, poolelementstructurepiece.getGroundLevelDelta()));
                        }

                        for (JigsawJunction jigsawjunction : poolelementstructurepiece.getJunctions()) {
                            Int1088 k = jigsawjunction.getSourceX();
                            Int1088 l = jigsawjunction.getSourceZ();
                            if (k.compareTo(WorldBounds.subtractBlockOffset(i, 12L)) > 0 && l.compareTo(WorldBounds.subtractBlockOffset(j, 12L)) > 0 && k.compareTo(WorldBounds.addBlockOffset(i, 27L)) < 0 && l.compareTo(WorldBounds.addBlockOffset(j, 27L)) < 0) {
                                objectlist1.add(jigsawjunction);
                            }
                        }
                    } else {
                        objectlist.add(new Beardifier.Rigid(structurepiece.getBoundingBox(), terrainadjustment, 0));
                    }
                }
            }
        });
        return new Beardifier(objectlist.iterator(), objectlist1.iterator());
    }

    @VisibleForTesting
    public Beardifier(ObjectListIterator<Beardifier.Rigid> pPieceIterator, ObjectListIterator<JigsawJunction> pJunctionIterator) {
        this.pieceIterator = pPieceIterator;
        this.junctionIterator = pJunctionIterator;
    }

    public double compute(DensityFunction.FunctionContext pContext) {
        int j = pContext.blockY();

        double d0;
        double d1;
        for (d0 = 0.0D; this.pieceIterator.hasNext(); d0 += d1) {
            Beardifier.Rigid beardifier$rigid = this.pieceIterator.next();
            BoundingBox boundingbox = beardifier$rigid.box();
            int l = beardifier$rigid.groundLevelDelta();
            double i1 = Math.max(0.0D, Math.max(-difference(pContext, true, boundingbox.minX()), difference(pContext, true, boundingbox.maxX())));
            double j1 = Math.max(0.0D, Math.max(-difference(pContext, false, boundingbox.minZ()), difference(pContext, false, boundingbox.maxZ())));
            int k1 = boundingbox.minY() + l;
            int l1 = j - k1;
            int i3;
            switch (beardifier$rigid.terrainAdjustment()) {
                case NONE:
                    i3 = 0;
                    break;
                case BURY:
                case BEARD_THIN:
                    i3 = l1;
                    break;
                case BEARD_BOX:
                    i3 = Math.max(0, Math.max(k1 - j, j - boundingbox.maxY()));
                    break;
                default:
                    throw new IncompatibleClassChangeError();
            }

            int i2 = i3;
            switch (beardifier$rigid.terrainAdjustment()) {
                case NONE:
                    d1 = 0.0D;
                    break;
                case BURY:
                    d1 = getBuryContribution(i1, i2, j1);
                    break;
                case BEARD_THIN:
                case BEARD_BOX:
                    d1 = getBeardContribution(i1, i2, j1, l1) * 0.8D;
                    break;
                default:
                    throw new IncompatibleClassChangeError();
            }
        }

        this.pieceIterator.back(Integer.MAX_VALUE);

        while (this.junctionIterator.hasNext()) {
            JigsawJunction jigsawjunction = this.junctionIterator.next();
            double j2 = difference(pContext, true, jigsawjunction.getSourceX());
            int k2 = j - jigsawjunction.getSourceGroundY();
            double l2 = difference(pContext, false, jigsawjunction.getSourceZ());
            d0 += getBeardContribution(j2, k2, l2, k2) * 0.4D;
        }

        this.junctionIterator.back(Integer.MAX_VALUE);
        return d0;
    }

    /** Exact local subtraction; remote differences saturate without aliasing a limb. */
    private static double difference(DensityFunction.FunctionContext context, boolean x, Int1088 value) {
        long low = 0L, borrow = 0L, sign = 0L, top = 0L;
        boolean fits = true;
        for (int index = 0; index < Int1088.LIMBS; ++index) {
            long a = x ? context.blockXLimb(index) : context.blockZLimb(index);
            long b = value.limb(index);
            long first = a - b;
            long result = first - borrow;
            borrow = (Long.compareUnsigned(a, b) < 0 || borrow != 0 && first == 0L) ? 1L : 0L;
            if (index == 0) { low = result; sign = result >> 63; }
            else if (result != sign) fits = false;
            top = result;
        }
        long sourceTop = x ? context.blockXLimb(Int1088.LIMBS - 1) : context.blockZLimb(Int1088.LIMBS - 1);
        if (((sourceTop ^ value.limb(Int1088.LIMBS - 1)) & (sourceTop ^ top)) < 0L)
            return sourceTop < 0L ? -Double.MAX_VALUE : Double.MAX_VALUE;
        return fits ? (double) low : top < 0L ? -Double.MAX_VALUE : Double.MAX_VALUE;
    }

    public double minValue() {
        return Double.NEGATIVE_INFINITY;
    }

    public double maxValue() {
        return Double.POSITIVE_INFINITY;
    }

    private static double getBuryContribution(double pX, int pY, double pZ) {
        double d0 = Mth.length(pX, (double) pY / 2.0D, pZ);
        return Mth.clampedMap(d0, 0.0D, 6.0D, 1.0D, 0.0D);
    }

    private static double getBeardContribution(double p_223926_, int p_223927_, double p_223928_, int p_223929_) {
        double i = p_223926_ + 12.0D;
        int j = p_223927_ + 12;
        double k = p_223928_ + 12.0D;
        if (isInKernelRange(i) && isInKernelRange(j) && isInKernelRange(k)) {
            double d0 = (double) p_223929_ + 0.5D;
            double d1 = Mth.lengthSquared(p_223926_, d0, p_223928_);
            double d2 = -d0 * Mth.fastInvSqrt(d1 / 2.0D) / 2.0D;
            return d2 * (double) BEARD_KERNEL[(int) (k * 24.0D * 24.0D + i * 24.0D + j)];
        } else {
            return 0.0D;
        }
    }

    private static boolean isInKernelRange(double p_223920_) {
        return p_223920_ >= 0 && p_223920_ < 24;
    }

    private static double computeBeardContribution(int pX, int pY, int pZ) {
        return computeBeardContribution(pX, (double) pY + 0.5D, pZ);
    }

    private static double computeBeardContribution(int p_223922_, double p_223923_, int p_223924_) {
        double d0 = Mth.lengthSquared((double) p_223922_, p_223923_, (double) p_223924_);
        return Math.pow(Math.E, -d0 / 16.0D);
    }

    @VisibleForTesting
    public static record Rigid(BoundingBox box, TerrainAdjustment terrainAdjustment, int groundLevelDelta) {
    }
}
