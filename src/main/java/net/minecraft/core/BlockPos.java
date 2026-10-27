package net.minecraft.core;

import com.google.common.collect.AbstractIterator;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.concurrent.Immutable;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;

@Immutable
public class BlockPos extends Vec3i {
    public static final Codec<BlockPos> CODEC = Vec3i.CODEC.xmap(BlockPos::new, value -> value).stable();
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final BlockPos ZERO = new BlockPos(0, 0, 0);

    public BlockPos(long pX, int pY, long pZ) {
        super(pX, pY, pZ);
    }

    public BlockPos(Int1088 pX, int pY, Int1088 pZ) {
        super(pX, pY, pZ);
    }

    public BlockPos(double pX, double pY, double pZ) {
        super(pX, pY, pZ);
    }

    public BlockPos(Vec3 pVector) {
        this(net.minecraft.world.phys.SectorVec3.fromApproximate(pVector).blockPosition());
    }

    public BlockPos(Position pPos) {
        this(pPos.exactPosition().blockPosition());
    }

    public BlockPos(Vec3i pVector) {
        this(pVector.getX(), pVector.getY(), pVector.getZ());
    }

    public BlockPos offset(double dx, double dy, double dz) {
        return dx == 0 && dy == 0 && dz == 0 ? this : new BlockPos(
                WorldBounds.addDoubleBlockOffset(getX(), dx), Double.isFinite(dy) ? Mth.floor(getY() + dy) : getY(),
                WorldBounds.addDoubleBlockOffset(getZ(), dz));
    }
    public BlockPos offset(long dx, int dy, long dz) { return offset(Int1088.fromLong(dx), dy, Int1088.fromLong(dz)); }
    public BlockPos offset(Vec3i other) { return offset(other.getX(), other.getY(), other.getZ()); }
    public BlockPos offset(Int1088 dx, int dy, Int1088 dz) {
        return dx.signum() == 0 && dy == 0 && dz.signum() == 0 ? this : new BlockPos(
                getX().addSaturated(dx), WorldBounds.addSaturated(getY(), dy), getZ().addSaturated(dz));
    }
    public BlockPos subtract(Vec3i other) {
        return new BlockPos(getX().subtractSaturated(other.getX()),
                (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, (long) getY() - other.getY())),
                getZ().subtractSaturated(other.getZ()));
    }

    public BlockPos multiply(int pScalar) {
        if (pScalar == 1) {
            return this;
        } else {
            return pScalar == 0 ? ZERO : new BlockPos(WorldBounds.multiplySaturated(this.getX(), pScalar), WorldBounds.multiplySaturated(this.getY(), pScalar), WorldBounds.multiplySaturated(this.getZ(), pScalar));
        }
    }

    public BlockPos above() {
        return this.relative(Direction.UP);
    }

    public BlockPos above(int pDistance) {
        return this.relative(Direction.UP, pDistance);
    }

    public BlockPos below() {
        return this.relative(Direction.DOWN);
    }

    public BlockPos below(int pDistance) {
        return this.relative(Direction.DOWN, pDistance);
    }

    public BlockPos north() {
        return this.relative(Direction.NORTH);
    }

    public BlockPos north(int pDistance) {
        return this.relative(Direction.NORTH, pDistance);
    }

    public BlockPos south() {
        return this.relative(Direction.SOUTH);
    }

    public BlockPos south(int pDistance) {
        return this.relative(Direction.SOUTH, pDistance);
    }

    public BlockPos west() {
        return this.relative(Direction.WEST);
    }

    public BlockPos west(int pDistance) {
        return this.relative(Direction.WEST, pDistance);
    }

    public BlockPos east() {
        return this.relative(Direction.EAST);
    }

    public BlockPos east(int pDistance) {
        return this.relative(Direction.EAST, pDistance);
    }

    public BlockPos relative(Direction pDirection) {
        return this.relative(pDirection, 1);
    }

    public BlockPos relative(Direction pDirection, int pDistance) {
        return pDistance == 0 ? this : this.offset((long) pDirection.getStepX() * pDistance,
                pDirection.getStepY() * pDistance, (long) pDirection.getStepZ() * pDistance);
    }

    public BlockPos relative(Direction.Axis pAxis, int pAmount) {
        if (pAmount == 0) {
            return this;
        } else {
            int i = pAxis == Direction.Axis.X ? pAmount : 0;
            int j = pAxis == Direction.Axis.Y ? pAmount : 0;
            int k = pAxis == Direction.Axis.Z ? pAmount : 0;
            return this.offset((long) i, j, (long) k);
        }
    }

    public BlockPos rotate(Rotation pRotation) {
        switch (pRotation) {
            case NONE:
            default:
                return this;
            case CLOCKWISE_90:
                return new BlockPos(this.getZ().negate(), this.getY(), this.getX());
            case CLOCKWISE_180:
                return new BlockPos(this.getX().negate(), this.getY(), this.getZ().negate());
            case COUNTERCLOCKWISE_90:
                return new BlockPos(this.getZ(), this.getY(), this.getX().negate());
        }
    }

    public BlockPos cross(Vec3i pVector) {
        return new BlockPos(super.cross(pVector));
    }

    public BlockPos atY(int pY) {
        return new BlockPos(this.getX(), pY, this.getZ());
    }

    public BlockPos immutable() {
        return this;
    }

    public BlockPos.MutableBlockPos mutable() {
        return new BlockPos.MutableBlockPos(this.getX(), this.getY(), this.getZ());
    }

    public static Iterable<BlockPos> randomInCube(RandomSource pRandom, int pAmount, BlockPos pCenter, int pRadius) {
        return randomBetweenClosed(pRandom, pAmount, WorldBounds.subtractBlockOffset(pCenter.getX(), pRadius), pCenter.getY() - pRadius, WorldBounds.subtractBlockOffset(pCenter.getZ(), pRadius), WorldBounds.addBlockOffset(pCenter.getX(), pRadius), pCenter.getY() + pRadius, WorldBounds.addBlockOffset(pCenter.getZ(), pRadius));
    }

    public static Iterable<BlockPos> randomBetweenClosed(RandomSource pRandom, int pAmount, long pMinX, int pMinY, long pMinZ, long pMaxX, int pMaxY, long o) {
        return randomBetweenClosed(pRandom, pAmount, Int1088.fromLong(pMinX), pMinY, Int1088.fromLong(pMinZ), Int1088.fromLong(pMaxX), pMaxY, Int1088.fromLong(o));
    }

    public static Iterable<BlockPos> randomBetweenClosed(RandomSource pRandom, int pAmount, Int1088 pMinX, int pMinY, Int1088 pMinZ, Int1088 pMaxX, int pMaxY, Int1088 pMaxZ) {
        boolean valid = pMinX.compareTo(pMaxX) <= 0 && pMinY <= pMaxY && pMinZ.compareTo(pMaxZ) <= 0;
        return () -> {
            return new AbstractIterator<BlockPos>() {
                final BlockPos.MutableBlockPos nextPos = new BlockPos.MutableBlockPos();
                final RandomCoordinate x = valid ? new RandomCoordinate(pMinX, pMaxX) : null;
                final RandomCoordinate y = valid ? new RandomCoordinate(Int1088.fromLong(pMinY), Int1088.fromLong(pMaxY)) : null;
                final RandomCoordinate z = valid ? new RandomCoordinate(pMinZ, pMaxZ) : null;
                int counter = pAmount;

                protected BlockPos computeNext() {
                    if (this.counter <= 0 || !valid) {
                        return this.endOfData();
                    } else {
                        BlockPos blockpos = this.nextPos.setLimbs(this.x.sample(pRandom), (int) this.y.sample(pRandom)[0], this.z.sample(pRandom));
                        --this.counter;
                        return blockpos;
                    }
                }
            };
        };
    }

    private static final class RandomCoordinate {
        private final long[] origin;
        private final long[] limit = new long[Int1088.LIMBS];
        private final long[] sample = new long[Int1088.LIMBS];
        private final int top, bits;

        RandomCoordinate(Int1088 min, Int1088 max) {
            this.origin = fixed(min);
            max.copyFixed(this.limit, 0);
            // Unsigned width, not a coordinate: MIN..MAX has width 2^1088-1.
            Int1088Math.subtract(this.limit, 0, Int1088.LIMBS, this.origin, 0, Int1088.LIMBS, this.limit, 0);
            int top = Int1088.LIMBS - 1;
            while (top > 0 && this.limit[top] == 0) --top;
            this.top = top;
            this.bits = 64 - Long.numberOfLeadingZeros(this.limit[top]);
        }

        long[] sample(RandomSource random) {
            java.util.Arrays.fill(this.sample, this.top + 1, Int1088.LIMBS, 0);
            boolean greater;
            do {
                for (int i = 0; i <= this.top; ++i) this.sample[i] = random.nextLong();
                if (this.bits < 64) this.sample[this.top] = this.bits == 0 ? 0 : this.sample[this.top] >>> (64 - this.bits);
                greater = false;
                for (int i = this.top; i >= 0; --i) {
                    int comparison = Long.compareUnsigned(this.sample[i], this.limit[i]);
                    if (comparison != 0) { greater = comparison > 0; break; }
                }
            } while (greater);
            // Unsigned offset + origin is proven inside [min,max]; modular limb
            // arithmetic here cannot wrap the resulting coordinate out of range.
            Int1088Math.add(this.origin, 0, Int1088.LIMBS, this.sample, 0, Int1088.LIMBS, this.sample, 0);
            return this.sample;
        }
    }

    public static Iterable<BlockPos> withinManhattan(BlockPos pPos, int pXSize, int pYSize, int pZSize) {
        int i = pXSize + pYSize + pZSize;
        Int1088 j = pPos.getX();
        int k = pPos.getY();
        Int1088 l = pPos.getZ();
        return () -> {
            return new AbstractIterator<BlockPos>() {
                private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
                private int currentDepth;
                private int maxX;
                private int maxY;
                private int x;
                private int y;
                private boolean zMirror;
                private int mirrorOffset;

                protected BlockPos computeNext() {
                    if (this.zMirror) {
                        this.zMirror = false;
                        if (canOffset(l, -this.mirrorOffset)) {
                            this.cursor.setZ(WorldBounds.addBlockOffset(l, -this.mirrorOffset));
                            return this.cursor;
                        }
                    }
                    {
                        BlockPos blockpos;
                        for (blockpos = null; blockpos == null; ++this.y) {
                            if (this.y > this.maxY) {
                                ++this.x;
                                if (this.x > this.maxX) {
                                    ++this.currentDepth;
                                    if (this.currentDepth > i) {
                                        return this.endOfData();
                                    }

                                    this.maxX = Math.min(pXSize, this.currentDepth);
                                    this.x = -this.maxX;
                                }

                                this.maxY = Math.min(pYSize, this.currentDepth - Math.abs(this.x));
                                this.y = -this.maxY;
                            }

                            int i1 = this.x;
                            int j1 = this.y;
                            int k1 = this.currentDepth - Math.abs(i1) - Math.abs(j1);
                            if (k1 <= pZSize && canOffset(j, i1) && (long) k + j1 >= Integer.MIN_VALUE && (long) k + j1 <= Integer.MAX_VALUE) {
                                if (canOffset(l, k1)) {
                                    this.zMirror = k1 != 0;
                                    this.mirrorOffset = k1;
                                    blockpos = this.cursor.set(WorldBounds.addBlockOffset(j, i1), k + j1, WorldBounds.addBlockOffset(l, k1));
                                } else if (k1 != 0 && canOffset(l, -k1)) {
                                    blockpos = this.cursor.set(WorldBounds.addBlockOffset(j, i1), k + j1, WorldBounds.addBlockOffset(l, -k1));
                                }
                            }
                        }

                        return blockpos;
                    }
                }
            };
        };
    }

    public static Optional<BlockPos> findClosestMatch(BlockPos pPos, int pWidth, int pHeight, Predicate<BlockPos> pPosFilter) {
        for (BlockPos blockpos : withinManhattan(pPos, pWidth, pHeight, pWidth)) {
            if (pPosFilter.test(blockpos)) {
                return Optional.of(blockpos.immutable());
            }
        }

        return Optional.empty();
    }

    public static Stream<BlockPos> withinManhattanStream(BlockPos pPos, int pXSize, int pYSize, int pZSize) {
        return StreamSupport.stream(withinManhattan(pPos, pXSize, pYSize, pZSize).spliterator(), false);
    }

    public static Iterable<BlockPos> betweenClosed(BlockPos pFirstPos, BlockPos pSecondPos) {
        return betweenClosed(min(pFirstPos.getX(), pSecondPos.getX()), Math.min(pFirstPos.getY(), pSecondPos.getY()), min(pFirstPos.getZ(), pSecondPos.getZ()), max(pFirstPos.getX(), pSecondPos.getX()), Math.max(pFirstPos.getY(), pSecondPos.getY()), max(pFirstPos.getZ(), pSecondPos.getZ()));
    }

    public static Stream<BlockPos> betweenClosedStream(BlockPos pFirstPos, BlockPos pSecondPos) {
        return StreamSupport.stream(betweenClosed(pFirstPos, pSecondPos).spliterator(), false);
    }

    public static Stream<BlockPos> betweenClosedStream(BoundingBox pBox) {
        return betweenClosedStream(min(pBox.minX(), pBox.maxX()), Math.min(pBox.minY(), pBox.maxY()), min(pBox.minZ(), pBox.maxZ()), max(pBox.minX(), pBox.maxX()), Math.max(pBox.minY(), pBox.maxY()), max(pBox.minZ(), pBox.maxZ()));
    }

    public static Stream<BlockPos> betweenClosedStream(AABB pAabb) {
        return betweenClosedStream(Int1088.fromDoubleFloorSaturated(pAabb.minX), Mth.floor(pAabb.minY), Int1088.fromDoubleFloorSaturated(pAabb.minZ), Int1088.fromDoubleFloorSaturated(pAabb.maxX), Mth.floor(pAabb.maxY), Int1088.fromDoubleFloorSaturated(pAabb.maxZ));
    }

    public static Stream<BlockPos> betweenClosedStream(long pMinX, int pMinY, long pMinZ, long pMaxX, int pMaxY, long pMaxZ) {
        return StreamSupport.stream(betweenClosed(pMinX, pMinY, pMinZ, pMaxX, pMaxY, pMaxZ).spliterator(), false);
    }

    public static Iterable<BlockPos> betweenClosed(long pX1, int pY1, long pZ1, long pX2, int pY2, long pZ2) {
        return betweenClosed(Int1088.fromLong(pX1), pY1, Int1088.fromLong(pZ1), Int1088.fromLong(pX2), pY2, Int1088.fromLong(pZ2));
    }

    public static Stream<BlockPos> betweenClosedStream(Int1088 pX1, int pY1, Int1088 pZ1, Int1088 pX2, int pY2, Int1088 pZ2) {
        return StreamSupport.stream(betweenClosed(pX1, pY1, pZ1, pX2, pY2, pZ2).spliterator(), false);
    }

    private static Int1088 min(Int1088 a, Int1088 b) { return a.compareTo(b) <= 0 ? a : b; }
    private static Int1088 max(Int1088 a, Int1088 b) { return a.compareTo(b) >= 0 ? a : b; }

    private static boolean canOffset(Int1088 coordinate, long offset) {
        return offset == 0 || (offset > 0
                ? WorldBounds.canAdvanceBlock(coordinate, Int1088.MAX.subtract(Int1088.fromLong(offset - 1)))
                : WorldBounds.canAdvanceBlock(Int1088.MIN.subtract(Int1088.fromLong(offset + 1)), coordinate));
    }

    public static Iterable<BlockPos> betweenClosed(Int1088 pX1, int pY1, Int1088 pZ1, Int1088 pX2, int pY2, Int1088 pZ2) {
        if (pX1.compareTo(pX2) > 0 || pY1 > pY2 || pZ1.compareTo(pZ2) > 0) {
            return java.util.Collections::emptyIterator;
        }

        return () -> new AbstractIterator<BlockPos>() {
            private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            private final Cursor3D range = new Cursor3D(pX1, pY1, pZ1, pX2, pY2, pZ2);

            protected BlockPos computeNext() {
                if (!this.range.advance()) {
                    return this.endOfData();
                }

                for (int i = 0; i < Int1088.LIMBS; ++i) {
                    this.cursor.mutableX[i] = this.range.nextXLimb(i);
                    this.cursor.mutableZ[i] = this.range.nextZLimb(i);
                }
                this.cursor.setY(this.range.nextY());
                return this.cursor;
            }
        };
    }

    private static long[] fixed(Int1088 value) {
        long[] words = new long[Int1088.LIMBS];
        value.copyFixed(words, 0);
        return words;
    }

    public static Iterable<BlockPos.MutableBlockPos> spiralAround(BlockPos p_121936_, int p_121937_, Direction p_121938_, Direction p_121939_) {
        Validate.validState(p_121938_.getAxis() != p_121939_.getAxis(), "The two directions cannot be on the same axis");
        return () -> {
            return new AbstractIterator<BlockPos.MutableBlockPos>() {
                private final Direction[] directions = new Direction[] {p_121938_, p_121939_, p_121938_.getOpposite(), p_121939_.getOpposite()};
                private final BlockPos.MutableBlockPos cursor = p_121936_.mutable();
                private final long legs = 4L * p_121937_;
                private int leg = -1;
                private int legSize;
                private int legIndex;
                private Int1088 lastX = this.cursor.getX();
                private int lastY = this.cursor.getY();
                private Int1088 lastZ = this.cursor.getZ();

                protected BlockPos.MutableBlockPos computeNext() {
                    if (this.leg == -1) {
                        this.leg = 0;
                        this.legSize = 1;
                        this.legIndex = 1;
                        return this.cursor;
                    }
                    Direction step = this.directions[(this.leg + 4) % 4];
                    if (!canOffset(this.lastX, step.getStepX()) || !canOffset(this.lastZ, step.getStepZ())) {
                        return this.endOfData();
                    }
                    this.cursor.set(this.lastX, this.lastY, this.lastZ).move(this.directions[(this.leg + 4) % 4]);
                    this.lastX = this.cursor.getX();
                    this.lastY = this.cursor.getY();
                    this.lastZ = this.cursor.getZ();
                    if (this.legIndex >= this.legSize) {
                        if (this.leg >= this.legs) {
                            return this.endOfData();
                        }

                        ++this.leg;
                        this.legIndex = 0;
                        this.legSize = this.leg / 2 + 1;
                    }

                    ++this.legIndex;
                    return this.cursor;
                }
            };
        };
    }

    public static class MutableBlockPos extends BlockPos {
        private final long[] mutableX = new long[Int1088.LIMBS];
        private final long[] mutableZ = new long[Int1088.LIMBS];
        private final long[] scratchX = new long[Int1088.LIMBS];
        private final long[] scratchZ = new long[Int1088.LIMBS];
        private final long[] scratchOther = new long[Int1088.LIMBS];

        public MutableBlockPos() {
            this(0, 0, 0);
        }

        public MutableBlockPos(long pX, int pY, long pZ) {
            super(pX, pY, pZ);
            this.set(pX, pY, pZ);
        }

        public MutableBlockPos(Int1088 pX, int pY, Int1088 pZ) {
            super(pX, pY, pZ);
            this.set(pX, pY, pZ);
        }

        public MutableBlockPos(double pX, double pY, double pZ) {
            super(pX, pY, pZ);
            this.set(pX, pY, pZ);
        }

        @Override public Int1088 getX() { return Int1088.fromFixedLimbs(this.mutableX); }
        @Override public Int1088 getZ() { return Int1088.fromFixedLimbs(this.mutableZ); }
        @Override public long xLimb(int index) { return this.mutableX[java.util.Objects.checkIndex(index, Int1088.LIMBS)]; }
        @Override public long zLimb(int index) { return this.mutableZ[java.util.Objects.checkIndex(index, Int1088.LIMBS)]; }
        @Override public int xLimbCount() { return Int1088Math.canonicalLength(this.mutableX, 0, Int1088.LIMBS); }
        @Override public int zLimbCount() { return Int1088Math.canonicalLength(this.mutableZ, 0, Int1088.LIMBS); }
        @Override public boolean xFitsLong() { return xLimbCount() == 1; }
        @Override public boolean zFitsLong() { return zLimbCount() == 1; }
        @Override public long xLow() { return this.mutableX[0]; }
        @Override public long zLow() { return this.mutableZ[0]; }

        private MutableBlockPos setLimbs(long[] x, int y, long[] z) {
            System.arraycopy(x, 0, this.mutableX, 0, Int1088.LIMBS);
            System.arraycopy(z, 0, this.mutableZ, 0, Int1088.LIMBS);
            this.setY(y);
            return this;
        }

        public BlockPos offset(double pDx, double pDy, double pDz) {
            return super.offset(pDx, pDy, pDz).immutable();
        }

        public BlockPos offset(int pDx, int pDy, int pDz) {
            return super.offset(pDx, pDy, pDz).immutable();
        }

        public BlockPos offset(long pDx, int pDy, long pDz) {
            return super.offset(pDx, pDy, pDz).immutable();
        }

        public BlockPos offset(Int1088 pDx, int pDy, Int1088 pDz) {
            return super.offset(pDx, pDy, pDz).immutable();
        }

        public BlockPos multiply(int pScalar) {
            return super.multiply(pScalar).immutable();
        }

        public BlockPos relative(Direction pDirection, int pDistance) {
            return super.relative(pDirection, pDistance).immutable();
        }

        public BlockPos relative(Direction.Axis pAxis, int pAmount) {
            return super.relative(pAxis, pAmount).immutable();
        }

        public BlockPos rotate(Rotation pRotation) {
            return super.rotate(pRotation).immutable();
        }

        public BlockPos.MutableBlockPos set(long pX, int pY, long pZ) {
            this.setX(pX);
            this.setY(pY);
            this.setZ(pZ);
            return this;
        }

        public BlockPos.MutableBlockPos set(double pX, double pY, double pZ) {
            return this.set(Int1088.fromDoubleFloorSaturated(pX), Mth.floor(pY), Int1088.fromDoubleFloorSaturated(pZ));
        }

        public BlockPos.MutableBlockPos set(Int1088 pX, int pY, Int1088 pZ) {
            this.setX(pX);
            this.setY(pY);
            this.setZ(pZ);
            return this;
        }

        public BlockPos.MutableBlockPos set(Vec3i vector) {
            for (int i = 0; i < Int1088.LIMBS; ++i) {
                this.scratchX[i] = vector.xLimb(i);
                this.scratchZ[i] = vector.zLimb(i);
            }
            return this.setLimbs(this.scratchX, vector.getY(), this.scratchZ);
        }

        public BlockPos.MutableBlockPos set(AxisCycle pCycle, int pX, int pY, int pZ) {
            return this.set(pCycle.cycle(pX, pY, pZ, Direction.Axis.X), pCycle.cycle(pX, pY, pZ, Direction.Axis.Y), pCycle.cycle(pX, pY, pZ, Direction.Axis.Z));
        }

        public BlockPos.MutableBlockPos setWithOffset(Vec3i pPos, Direction pDirection) {
            return this.setWithOffset(pPos, (long) pDirection.getStepX(), pDirection.getStepY(), (long) pDirection.getStepZ());
        }

        public BlockPos.MutableBlockPos setWithOffset(Vec3i vector, long x, int y, long z) {
            for (int i = 0; i < Int1088.LIMBS; ++i) {
                this.scratchX[i] = vector.xLimb(i);
                this.scratchZ[i] = vector.zLimb(i);
            }
            int newY = WorldBounds.addSaturated(vector.getY(), y);
            addLongSaturated(this.scratchX, x, this.mutableX);
            addLongSaturated(this.scratchZ, z, this.mutableZ);
            this.setY(newY);
            return this;
        }
        public BlockPos.MutableBlockPos setWithOffset(Vec3i pos, Vec3i offset) {
            int y = WorldBounds.addSaturated(pos.getY(), offset.getY());
            for (int i = 0; i < Int1088.LIMBS; ++i) this.scratchX[i] = pos.xLimb(i);
            for (int i = 0; i < Int1088.LIMBS; ++i) this.scratchOther[i] = offset.xLimb(i);
            for (int i = 0; i < Int1088.LIMBS; ++i) this.scratchZ[i] = pos.zLimb(i);
            Int1088Math.addSaturated(this.scratchX, 0, Int1088.LIMBS, this.scratchOther, 0, Int1088.LIMBS, this.mutableX, 0);
            for (int i = 0; i < Int1088.LIMBS; ++i) this.scratchOther[i] = offset.zLimb(i);
            Int1088Math.addSaturated(this.scratchZ, 0, Int1088.LIMBS, this.scratchOther, 0, Int1088.LIMBS, this.mutableZ, 0);
            this.setY(y);
            return this;
        }

        private void addLongSaturated(long[] source, long offset, long[] out) {
            this.scratchOther[0] = offset;
            java.util.Arrays.fill(this.scratchOther, 1, Int1088.LIMBS, offset >> 63);
            Int1088Math.addSaturated(source, 0, Int1088.LIMBS, this.scratchOther, 0, Int1088.LIMBS, out, 0);
        }
        public BlockPos.MutableBlockPos move(Direction direction) { return move(direction, 1); }
        public BlockPos.MutableBlockPos move(Direction direction, int distance) {
            return setWithOffset(this, (long) direction.getStepX() * distance, direction.getStepY() * distance, (long) direction.getStepZ() * distance);
        }
        public BlockPos.MutableBlockPos move(int x, int y, int z) { return setWithOffset(this, x, y, z); }

        public BlockPos.MutableBlockPos move(Vec3i pOffset) {
            return this.setWithOffset(this, pOffset);
        }

        public BlockPos.MutableBlockPos clamp(Direction.Axis pAxis, int pMin, int pMax) {
            switch (pAxis) {
                case X:
                    return this.set(max(Int1088.fromLong(pMin), min(this.getX(), Int1088.fromLong(pMax))), this.getY(), this.getZ());
                case Y:
                    return this.setY(Mth.clamp(this.getY(), pMin, pMax));
                case Z:
                    return this.set(this.getX(), this.getY(), max(Int1088.fromLong(pMin), min(this.getZ(), Int1088.fromLong(pMax))));
                default:
                    throw new IllegalStateException("Unable to clamp axis " + pAxis);
            }
        }

        public BlockPos.MutableBlockPos setX(long pX) {
            this.mutableX[0] = pX;
            java.util.Arrays.fill(this.mutableX, 1, Int1088.LIMBS, pX >> 63);
            return this;
        }

        public BlockPos.MutableBlockPos setX(Int1088 pX) {
            pX.copyFixed(this.mutableX, 0);
            return this;
        }

        public BlockPos.MutableBlockPos setY(int pY) {
            super.setY(pY);
            return this;
        }

        public BlockPos.MutableBlockPos setZ(long pZ) {
            this.mutableZ[0] = pZ;
            java.util.Arrays.fill(this.mutableZ, 1, Int1088.LIMBS, pZ >> 63);
            return this;
        }

        public BlockPos.MutableBlockPos setZ(Int1088 pZ) {
            pZ.copyFixed(this.mutableZ, 0);
            return this;
        }

        public BlockPos immutable() {
            return new BlockPos(this.getX(), this.getY(), this.getZ());
        }
    }
}
