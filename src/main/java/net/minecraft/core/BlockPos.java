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

    public BlockPos(Int128 pX, int pY, Int128 pZ) {
        super(pX, pY, pZ);
    }

    public BlockPos(double pX, double pY, double pZ) {
        super(pX, pY, pZ);
    }

    public BlockPos(Vec3 pVector) {
        this(net.minecraft.world.phys.SectorVec3.fromApproximate(pVector).blockPosition());
    }

    public BlockPos(Position pPos) {
        this(pPos.x(), pPos.y(), pPos.z());
    }

    public BlockPos(Vec3i pVector) {
        this(pVector.getX(), pVector.getY(), pVector.getZ());
    }

    public BlockPos offset(double pDx, double pDy, double pDz) {
        return pDx == 0.0D && pDy == 0.0D && pDz == 0.0D ? this : new BlockPos(WorldBounds.addDoubleBlockOffset(this.getX(), pDx), Mth.floor((double) this.getY() + pDy), WorldBounds.addDoubleBlockOffset(this.getZ(), pDz));
    }

    public BlockPos offset(long pDx, int pDy, long pDz) {
        return pDx == 0 && pDy == 0 && pDz == 0 ? this : new BlockPos(WorldBounds.addBlockOffset(this.getX(), pDx), this.getY() + pDy, WorldBounds.addBlockOffset(this.getZ(), pDz));
    }

    public BlockPos offset(Vec3i pVector) {
        return this.offset(pVector.getX(), pVector.getY(), pVector.getZ());
    }

    public BlockPos offset(Int128 pDx, int pDy, Int128 pDz) {
        return pDx.equals(Int128.ZERO) && pDy == 0 && pDz.equals(Int128.ZERO) ? this
                : new BlockPos(this.getX().addSaturated(pDx), this.getY() + pDy, this.getZ().addSaturated(pDz));
    }

    public BlockPos subtract(Vec3i pVector) {
        return new BlockPos(this.getX().subtractSaturated(pVector.getX()), this.getY() - pVector.getY(), this.getZ().subtractSaturated(pVector.getZ()));
    }

    public BlockPos multiply(int pScalar) {
        if (pScalar == 1) {
            return this;
        } else {
            return pScalar == 0 ? ZERO : new BlockPos(WorldBounds.multiplySaturated(this.getX(), pScalar), this.getY() * pScalar, WorldBounds.multiplySaturated(this.getZ(), pScalar));
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
        return new BlockPos(WorldBounds.addBlockOffset(this.getX(), pDirection.getStepX()),
                this.getY() + pDirection.getStepY(), WorldBounds.addBlockOffset(this.getZ(), pDirection.getStepZ()));
    }

    public BlockPos relative(Direction pDirection, int pDistance) {
        return pDistance == 0 ? this : new BlockPos(WorldBounds.addBlockOffset(this.getX(), (long) pDirection.getStepX() * pDistance), this.getY() + pDirection.getStepY() * pDistance, WorldBounds.addBlockOffset(this.getZ(), (long) pDirection.getStepZ() * pDistance));
    }

    public BlockPos relative(Direction.Axis pAxis, int pAmount) {
        if (pAmount == 0) {
            return this;
        } else {
            int i = pAxis == Direction.Axis.X ? pAmount : 0;
            int j = pAxis == Direction.Axis.Y ? pAmount : 0;
            int k = pAxis == Direction.Axis.Z ? pAmount : 0;
            return new BlockPos(WorldBounds.addBlockOffset(this.getX(), i), this.getY() + j,
                    WorldBounds.addBlockOffset(this.getZ(), k));
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
        return randomBetweenClosed(pRandom, pAmount, Int128.fromLong(pMinX), pMinY, Int128.fromLong(pMinZ), Int128.fromLong(pMaxX), pMaxY, Int128.fromLong(o));
    }

    public static Iterable<BlockPos> randomBetweenClosed(RandomSource pRandom, int pAmount, Int128 pMinX, int pMinY, Int128 pMinZ, Int128 pMaxX, int pMaxY, Int128 pMaxZ) {
        boolean valid = pMinX.compareTo(pMaxX) <= 0 && pMinY <= pMaxY && pMinZ.compareTo(pMaxZ) <= 0;
        return () -> {
            return new AbstractIterator<BlockPos>() {
                final BlockPos.MutableBlockPos nextPos = new BlockPos.MutableBlockPos();
                int counter = pAmount;

                protected BlockPos computeNext() {
                    if (this.counter <= 0 || !valid) {
                        return this.endOfData();
                    } else {
                        BlockPos blockpos = this.nextPos.set(randomCoordinate(pRandom, pMinX, pMaxX), (int) randomCoordinate(pRandom, Int128.fromLong(pMinY), Int128.fromLong(pMaxY)).lo(), randomCoordinate(pRandom, pMinZ, pMaxZ));
                        --this.counter;
                        return blockpos;
                    }
                }
            };
        };
    }

    private static Int128 randomCoordinate(RandomSource random, Int128 min, Int128 max) {
        // Unsigned width preserves spans larger than the signed domain. Masked
        // rejection samples a power-of-two envelope without modulo bias.
        Int128 width = max.subtract(min);
        int bits = width.hi() != 0L ? 128 - Long.numberOfLeadingZeros(width.hi())
                : 64 - Long.numberOfLeadingZeros(width.lo());
        Int128 sample;
        do {
            long hi = bits > 64 ? random.nextLong() >>> (128 - bits) : 0L;
            long lo = bits >= 64 ? random.nextLong() : bits == 0 ? 0L : random.nextLong() >>> (64 - bits);
            sample = Int128.of(hi, lo);
        } while (Long.compareUnsigned(sample.hi(), width.hi()) > 0
                || sample.hi() == width.hi() && Long.compareUnsigned(sample.lo(), width.lo()) > 0);
        return min.add(sample);
    }

    public static Iterable<BlockPos> withinManhattan(BlockPos pPos, int pXSize, int pYSize, int pZSize) {
        int i = pXSize + pYSize + pZSize;
        Int128 j = pPos.getX();
        int k = pPos.getY();
        Int128 l = pPos.getZ();
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
        return betweenClosedStream(Int128.fromDoubleFloorSaturated(pAabb.minX), Mth.floor(pAabb.minY), Int128.fromDoubleFloorSaturated(pAabb.minZ), Int128.fromDoubleFloorSaturated(pAabb.maxX), Mth.floor(pAabb.maxY), Int128.fromDoubleFloorSaturated(pAabb.maxZ));
    }

    public static Stream<BlockPos> betweenClosedStream(long pMinX, int pMinY, long pMinZ, long pMaxX, int pMaxY, long pMaxZ) {
        return StreamSupport.stream(betweenClosed(pMinX, pMinY, pMinZ, pMaxX, pMaxY, pMaxZ).spliterator(), false);
    }

    public static Iterable<BlockPos> betweenClosed(long pX1, int pY1, long pZ1, long pX2, int pY2, long pZ2) {
        return betweenClosed(Int128.fromLong(pX1), pY1, Int128.fromLong(pZ1), Int128.fromLong(pX2), pY2, Int128.fromLong(pZ2));
    }

    public static Stream<BlockPos> betweenClosedStream(Int128 pX1, int pY1, Int128 pZ1, Int128 pX2, int pY2, Int128 pZ2) {
        return StreamSupport.stream(betweenClosed(pX1, pY1, pZ1, pX2, pY2, pZ2).spliterator(), false);
    }

    private static Int128 min(Int128 a, Int128 b) { return a.compareTo(b) <= 0 ? a : b; }
    private static Int128 max(Int128 a, Int128 b) { return a.compareTo(b) >= 0 ? a : b; }

    private static boolean canOffset(Int128 coordinate, long offset) {
        return offset == 0 || (offset > 0
                ? WorldBounds.canAdvanceBlock(coordinate, Int128.MAX.subtract(Int128.fromLong(offset - 1)))
                : WorldBounds.canAdvanceBlock(Int128.MIN.subtract(Int128.fromLong(offset + 1)), coordinate));
    }

    public static Iterable<BlockPos> betweenClosed(Int128 pX1, int pY1, Int128 pZ1, Int128 pX2, int pY2, Int128 pZ2) {
        if (pX1.compareTo(pX2) > 0 || pY1 > pY2 || pZ1.compareTo(pZ2) > 0) {
            return java.util.Collections::emptyIterator;
        }

        // Iterate endpoint-first instead of deriving a flattened count.  The old
        // count used signed-long subtraction/multiplication and an int index, so
        // an edge range could wrap into an effectively endless generator loop.
        return () -> new AbstractIterator<BlockPos>() {
            private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            private Int128 x = pX1;
            private int y = pY1;
            private Int128 z = pZ1;
            private boolean done;

            protected BlockPos computeNext() {
                if (this.done) {
                    return this.endOfData();
                }

                this.cursor.set(this.x, this.y, this.z);
                if (WorldBounds.canAdvanceBlock(this.x, pX2)) {
                    this.x = WorldBounds.addBlockOffset(this.x, 1L);
                } else if (this.y != pY2) {
                    this.x = pX1;
                    ++this.y;
                } else if (WorldBounds.canAdvanceBlock(this.z, pZ2)) {
                    this.x = pX1;
                    this.y = pY1;
                    this.z = WorldBounds.addBlockOffset(this.z, 1L);
                } else {
                    this.done = true;
                }

                return this.cursor;
            }
        };
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
                private Int128 lastX = this.cursor.getX();
                private int lastY = this.cursor.getY();
                private Int128 lastZ = this.cursor.getZ();

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
        public MutableBlockPos() {
            this(0, 0, 0);
        }

        public MutableBlockPos(long pX, int pY, long pZ) {
            super(pX, pY, pZ);
        }

        public MutableBlockPos(Int128 pX, int pY, Int128 pZ) {
            super(pX, pY, pZ);
        }

        public MutableBlockPos(double pX, double pY, double pZ) {
            this(Int128.fromDoubleFloorSaturated(pX), Mth.floor(pY), Int128.fromDoubleFloorSaturated(pZ));
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

        public BlockPos offset(Int128 pDx, int pDy, Int128 pDz) {
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
            return this.set(Int128.fromDoubleFloorSaturated(pX), Mth.floor(pY), Int128.fromDoubleFloorSaturated(pZ));
        }

        public BlockPos.MutableBlockPos set(Int128 pX, int pY, Int128 pZ) {
            this.setX(pX);
            this.setY(pY);
            this.setZ(pZ);
            return this;
        }

        public BlockPos.MutableBlockPos set(Vec3i pVector) {
            this.setX(pVector.xHi(), pVector.xLo());
            this.setY(pVector.getY());
            this.setZ(pVector.zHi(), pVector.zLo());
            return this;
        }

        public BlockPos.MutableBlockPos set(AxisCycle pCycle, int pX, int pY, int pZ) {
            return this.set(pCycle.cycle(pX, pY, pZ, Direction.Axis.X), pCycle.cycle(pX, pY, pZ, Direction.Axis.Y), pCycle.cycle(pX, pY, pZ, Direction.Axis.Z));
        }

        public BlockPos.MutableBlockPos setWithOffset(Vec3i pPos, Direction pDirection) {
            return this.setWithOffset(pPos, (long) pDirection.getStepX(), pDirection.getStepY(), (long) pDirection.getStepZ());
        }

        public BlockPos.MutableBlockPos setWithOffset(Vec3i pVector, long pOffsetX, int pOffsetY, long pOffsetZ) {
            // Raw signed 128-bit add; saturates exactly like WorldBounds.addBlockOffset
            // (block.addSaturated(Int128.fromLong(offset))) but without allocation. The
            // offsets fed here by block iteration are tiny, so this is the hot form.
            this.addOffsetToX(pVector.xHi(), pVector.xLo(), pOffsetX);
            this.setY(pVector.getY() + pOffsetY);
            this.addOffsetToZ(pVector.zHi(), pVector.zLo(), pOffsetZ);
            return this;
        }

        public BlockPos.MutableBlockPos setWithOffset(Vec3i pPos, Vec3i pOffset) {
            return this.set(WorldBounds.addBlockOffset(pPos.getX(), pOffset.getX()),
                    pPos.getY() + pOffset.getY(), WorldBounds.addBlockOffset(pPos.getZ(), pOffset.getZ()));
        }

        public BlockPos.MutableBlockPos move(Direction pDirection) {
            return this.move(pDirection, 1);
        }

        public BlockPos.MutableBlockPos move(Direction pDirection, int pN) {
            this.addOffsetToX(this.xHi(), this.xLo(), (long) pDirection.getStepX() * pN);
            this.setY(this.getY() + pDirection.getStepY() * pN);
            this.addOffsetToZ(this.zHi(), this.zLo(), (long) pDirection.getStepZ() * pN);
            return this;
        }

        public BlockPos.MutableBlockPos move(int pX, int pY, int pZ) {
            this.addOffsetToX(this.xHi(), this.xLo(), (long) pX);
            this.setY(this.getY() + pY);
            this.addOffsetToZ(this.zHi(), this.zLo(), (long) pZ);
            return this;
        }

        /** Signs a Java long offset into 128 bits and adds it, saturating like addSaturated. */
        private void addOffsetToX(long pBlockHi, long pBlockLo, long pOffset) {
            long offsetHi = pOffset >> 63;
            long low = pBlockLo + pOffset;
            long carry = Int128Math.addCarry(pBlockLo, pOffset, low);
            long high = pBlockHi + offsetHi + carry;
            if (Int128Math.addOverflowed(pBlockHi, offsetHi, carry, high)) {
                high = pBlockHi < 0L ? Int128.MIN.hi() : Int128.MAX.hi();
                low = pBlockHi < 0L ? Int128.MIN.lo() : Int128.MAX.lo();
            }
            this.setX(high, low);
        }

        private void addOffsetToZ(long pBlockHi, long pBlockLo, long pOffset) {
            long offsetHi = pOffset >> 63;
            long low = pBlockLo + pOffset;
            long carry = Int128Math.addCarry(pBlockLo, pOffset, low);
            long high = pBlockHi + offsetHi + carry;
            if (Int128Math.addOverflowed(pBlockHi, offsetHi, carry, high)) {
                high = pBlockHi < 0L ? Int128.MIN.hi() : Int128.MAX.hi();
                low = pBlockHi < 0L ? Int128.MIN.lo() : Int128.MAX.lo();
            }
            this.setZ(high, low);
        }

        public BlockPos.MutableBlockPos move(Vec3i pOffset) {
            return this.set(WorldBounds.addBlockOffset(this.getX(), pOffset.getX()),
                    this.getY() + pOffset.getY(), WorldBounds.addBlockOffset(this.getZ(), pOffset.getZ()));
        }

        public BlockPos.MutableBlockPos clamp(Direction.Axis pAxis, int pMin, int pMax) {
            switch (pAxis) {
                case X:
                    return this.set(max(Int128.fromLong(pMin), min(this.getX(), Int128.fromLong(pMax))), this.getY(), this.getZ());
                case Y:
                    return this.set(this.getX(), Mth.clamp(this.getY(), pMin, pMax), this.getZ());
                case Z:
                    return this.set(this.getX(), this.getY(), max(Int128.fromLong(pMin), min(this.getZ(), Int128.fromLong(pMax))));
                default:
                    throw new IllegalStateException("Unable to clamp axis " + pAxis);
            }
        }

        public BlockPos.MutableBlockPos setX(long pX) {
            super.setX(pX);
            return this;
        }

        public BlockPos.MutableBlockPos setX(Int128 pX) {
            super.setX(pX);
            return this;
        }

        public BlockPos.MutableBlockPos setY(int pY) {
            super.setY(pY);
            return this;
        }

        public BlockPos.MutableBlockPos setZ(long pZ) {
            super.setZ(pZ);
            return this;
        }

        public BlockPos.MutableBlockPos setZ(Int128 pZ) {
            super.setZ(pZ);
            return this;
        }

        public BlockPos immutable() {
            return new BlockPos(this);
        }
    }
}
