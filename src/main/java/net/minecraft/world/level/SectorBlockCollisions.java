package net.minecraft.world.level;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.core.Int128;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.SectorAABB;
import net.minecraft.world.phys.SectorPhysicsOrigin;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Block collision iterator for a sector-physics operation.
 *
 * <p>The cursor and block lookup use exact world block coordinates.  The
 * returned shapes, and the entity shape used for filtering, are in the small
 * local frame described by {@code origin}; no world coordinate is converted
 * to a double during shape translation. Local spans are bounded by the shared
 * {@link WorldBounds#MAX_SAFE_LOCAL_SPAN}; its precision hard ceiling is
 * {@code (1L << 52) - 1}. Unsafe ranges yield one conservative collision.</p>
 */
public final class SectorBlockCollisions implements Iterator<VoxelShape>, AutoCloseable {
    private static final ThreadLocal<SectorBlockCollisions> IDLE = new ThreadLocal<>();
    private AABB localBox;
    private CollisionContext context;
    private final Cursor3D cursor = new Cursor3D(0L, 0, 0L, -1L, -1, -1L);
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private VoxelShape localEntityShape;
    private CollisionGetter collisionGetter;
    private SectorPhysicsOrigin origin;
    private boolean onlySuffocatingBlocks;
    private boolean invalidRange;
    private boolean invalidRangeReported;
    private VoxelShape next;
    private boolean done;
    private boolean computing;
    private boolean leased;
    private final VoxelShape.TranslationPool translations = new VoxelShape.TranslationPool();
    private SectorAABB rangeBox;
    private Int128 minX, maxX, minZ, maxZ;
    private int minY, maxY;
    /*
    * Collision sweeps usually touch only a few chunks, but a boundary sweep
     * can revisit them thousands of times. Four open-addressed slots cache
     * hits and misses; occupancy distinguishes unloaded chunks from empty slots.
     * Larger sweeps evict a home slot and remain exact (with more lookups).
    */
    private final long[] chunkKeys = new long[16];
    private final Int128[] chunkCoordinates = new Int128[8];
    private final BlockGetter[] chunks = new BlockGetter[4];
    private int occupied;
    private long cachedXHi, cachedXLo, cachedZHi, cachedZLo;
    private boolean hasCachedChunk;
    @Nullable
    private BlockGetter cachedChunk;

    public SectorBlockCollisions(CollisionGetter collisionGetter, @Nullable Entity entity,
            SectorAABB exactBox, AABB localBox,
            SectorPhysicsOrigin origin) {
        this(collisionGetter, entity, exactBox, localBox, origin, false);
    }

    public SectorBlockCollisions(CollisionGetter collisionGetter, @Nullable Entity entity,
            SectorAABB exactBox, AABB localBox,
            SectorPhysicsOrigin origin, boolean onlySuffocatingBlocks) {
        this.reset(collisionGetter, entity, exactBox, localBox, origin, onlySuffocatingBlocks);
    }

    private SectorBlockCollisions() {
    }

    /** Scoped, thread-confined scratch iterator. Iterator and returned shapes must not be used after close.
     * Always close, including after an early return or exception.
     * Nested queries get independent scratch state; ordinary Iterable queries remain independent.
     */
    public static SectorBlockCollisions acquire(CollisionGetter getter, @Nullable Entity entity,
            SectorAABB exactBox, AABB localBox, SectorPhysicsOrigin origin, boolean onlySuffocatingBlocks) {
        SectorBlockCollisions collisions = IDLE.get();
        IDLE.set(null);
        if (collisions == null) collisions = new SectorBlockCollisions();
        collisions.reset(getter, entity, exactBox, localBox, origin, onlySuffocatingBlocks);
        collisions.leased = true;
        return collisions;
    }

    @Override
    public void close() {
        if (!this.leased) return;
        if (this.computing) throw new IllegalStateException("Collision query is active");
        this.leased = false;
        this.done = true;
        this.next = null;
        this.translations.clear();
        this.collisionGetter = null;
        this.context = null;
        this.localBox = null;
        this.localEntityShape = null;
        this.origin = null;
        this.cachedChunk = null;
        Arrays.fill(this.chunks, null);
        IDLE.set(this);
    }

    private void reset(CollisionGetter collisionGetter, @Nullable Entity entity,
            SectorAABB exactBox, AABB localBox, SectorPhysicsOrigin origin, boolean onlySuffocatingBlocks) {
        this.collisionGetter = collisionGetter;
        this.origin = origin;
        this.localBox = localBox;
        this.localEntityShape = null; // Air and full cubes do not need a voxelized query box.
        this.context = entity == null ? CollisionContext.empty() : CollisionContext.of(entity);
        this.onlySuffocatingBlocks = onlySuffocatingBlocks;
        this.invalidRangeReported = false;
        this.next = null;
        this.done = false;
        this.computing = false;
        this.occupied = 0;
        this.hasCachedChunk = false;
        this.cachedChunk = null;
        Arrays.fill(this.chunks, null);

        // Immutable bounds can be reused by repeated probes. This retains only
        // geometry, never a level/entity or a chunk lookup across operations.
        if (this.rangeBox != exactBox) {
            this.minX = exactBox.minBlockXForCollision();
            this.maxX = exactBox.maxBlockXForCollision();
            this.minY = exactBox.minBlockYForCollision();
            this.maxY = exactBox.maxBlockYForCollision();
            this.minZ = exactBox.minBlockZForCollision();
            this.maxZ = exactBox.maxBlockZForCollision();
            this.rangeBox = exactBox;
        }
        Int128 minX = this.minX, maxX = this.maxX, minZ = this.minZ, maxZ = this.maxZ;
        int minY = this.minY, maxY = this.maxY;
        // Validate both the traversal span and its distance from the one local
        // origin. A small box at a distant address is not a safe local sweep.
        this.invalidRange = !origin.isSafe(minX, minZ) || !origin.isSafe(maxX, maxZ)
                || !isSafeCursorRange(minX, maxX, minY, maxY, minZ, maxZ);
        // Keep hostile ranges out of the cursor even though it now advances
        // by endpoints rather than by an overflowing linear index.
        if (!this.invalidRange) this.cursor.reset(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean isSafeCursorRange(Int128 minX, Int128 maxX, int minY, int maxY,
            Int128 minZ, Int128 maxZ) {
        // Preserve the 64-block horizontal sanity limit and int-sized volume
        // budget independently of the shared precision bound.
        if (maxX.compareTo(minX) < 0 || maxZ.compareTo(minZ) < 0 || maxY < minY
                || WorldBounds.localSpanExceeds(minX, maxX)
                || WorldBounds.localSpanExceeds(minZ, maxZ)
                || WorldBounds.signedDifferenceAsLong(maxX, minX) > 64L
                || WorldBounds.signedDifferenceAsLong(maxZ, minZ) > 64L) {
            return false;
        }
        long width = WorldBounds.signedDifferenceAsLong(maxX, minX) + 1L;
        long height = (long) maxY - minY + 1L;
        long depth = WorldBounds.signedDifferenceAsLong(maxZ, minZ) + 1L;
        return width <= Integer.MAX_VALUE / Math.max(1L, height)
                && width * height <= Integer.MAX_VALUE / Math.max(1L, depth);
    }

    @Nullable
    private BlockGetter getChunk(long xHi, long xLo, long zHi, long zLo) {
        long chunkXHi = xHi >> 4;
        long chunkXLo = (xLo >>> 4) | (xHi << 60);
        long chunkZHi = zHi >> 4;
        long chunkZLo = (zLo >>> 4) | (zHi << 60);
        if (this.hasCachedChunk && this.cachedXHi == chunkXHi && this.cachedXLo == chunkXLo
                && this.cachedZHi == chunkZHi && this.cachedZLo == chunkZLo) {
            return this.cachedChunk;
        }
        this.hasCachedChunk = true;
        this.cachedXHi = chunkXHi;
        this.cachedXLo = chunkXLo;
        this.cachedZHi = chunkZHi;
        this.cachedZLo = chunkZLo;
        int slot = (int) (chunkXHi ^ chunkXLo ^ chunkZHi ^ (chunkZLo * 3L)) & 3;
        int insertion = slot;
        for (int probe = 0; probe < 4; ++probe) {
            int key = slot << 2;
            if ((this.occupied & (1 << slot)) == 0) {
                insertion = slot;
                break;
            }
            if (this.chunkKeys[key] == chunkXHi && this.chunkKeys[key + 1] == chunkXLo
                    && this.chunkKeys[key + 2] == chunkZHi && this.chunkKeys[key + 3] == chunkZLo) {
                return this.cachedChunk = this.chunks[slot];
            }
            slot = (slot + 1) & 3;
        }

        int coordinate = insertion << 1;
        Int128 chunkX = this.chunkCoordinates[coordinate];
        if (chunkX == null || chunkX.hi() != chunkXHi || chunkX.lo() != chunkXLo) {
            this.chunkCoordinates[coordinate] = chunkX = Int128.of(chunkXHi, chunkXLo);
        }
        Int128 chunkZ = this.chunkCoordinates[coordinate + 1];
        if (chunkZ == null || chunkZ.hi() != chunkZHi || chunkZ.lo() != chunkZLo) {
            this.chunkCoordinates[coordinate + 1] = chunkZ = Int128.of(chunkZHi, chunkZLo);
        }
        BlockGetter getter;
        if (this.collisionGetter instanceof ServerLevel serverLevel) {
            // This method is intentionally non-blocking.  In particular, never
            // call Level.getChunk(..., false) here: its old server implementation
            // can wait for generation while the tick thread is processing input.
            getter = serverLevel.getChunkSource().getChunkNow(chunkX, chunkZ);
        } else {
            getter = this.collisionGetter.getChunkForCollisions(chunkX, chunkZ);
        }
        int key = insertion << 2;
        this.chunkKeys[key] = chunkXHi;
        this.chunkKeys[key + 1] = chunkXLo;
        this.chunkKeys[key + 2] = chunkZHi;
        this.chunkKeys[key + 3] = chunkZLo;
        this.chunks[insertion] = getter;
        this.occupied |= 1 << insertion;
        return this.cachedChunk = getter;
    }

    @Override
    public boolean hasNext() {
        if (this.computing) throw new IllegalStateException("Recursive iterator access");
        if (this.next != null) return true;
        if (this.done) return false;
        this.computing = true;
        this.done = true; // Fail closed if block shape computation throws.
        try {
            this.next = this.computeNext();
            this.done = this.next == null;
            return !this.done;
        } finally {
            this.computing = false;
        }
    }

    @Override
    public VoxelShape next() {
        if (!this.hasNext()) throw new NoSuchElementException();
        VoxelShape shape = this.next;
        this.next = null;
        return shape;
    }

    private VoxelShape queryShape() {
        if (this.localEntityShape == null) this.localEntityShape = Shapes.create(this.localBox);
        return this.localEntityShape;
    }

    private VoxelShape translate(VoxelShape shape, double x, double y, double z) {
        return this.leased ? this.translations.move(shape, x, y, z) : shape.move(x, y, z);
    }

    @Nullable
    private VoxelShape computeNext() {
        if (this.invalidRange) {
            // A sweep that cannot be represented by Cursor3D is conservative:
            // report an overlap in the current local frame once.  Returning no
            // shape would make the movement resolver accept the entire unsafe
            // delta and teleport across the world.
            if (!this.invalidRangeReported) {
                this.invalidRangeReported = true;
                return this.translate(Shapes.block(), this.localBox.minX, this.localBox.minY, this.localBox.minZ);
            }
            return null;
        }

        while (this.cursor.advance()) {
            long xHi = this.cursor.nextXHi();
            long xLo = this.cursor.nextXLo();
            int blockY = this.cursor.nextY();
            long zHi = this.cursor.nextZHi();
            long zLo = this.cursor.nextZLo();
            int type = this.cursor.getNextType();
            // At a world edge the one-block safety margin is saturated onto
            // the last real block. It is not an outer padding face: ordinary
            // blocks there must still contribute collision shapes. Correct each
            // clipped axis independently, including at X/Z world corners.
            if (xHi == Int128.MIN.hi() && xLo == Int128.MIN.lo()
                    || xHi == Int128.MAX.hi() && xLo == Int128.MAX.lo()) {
                --type;
            }
            if (zHi == Int128.MIN.hi() && zLo == Int128.MIN.lo()
                    || zHi == Int128.MAX.hi() && zLo == Int128.MAX.lo()) {
                --type;
            }
            if (type == Cursor3D.TYPE_CORNER) {
                continue;
            }

            BlockGetter getter = this.getChunk(xHi, xLo, zHi, zLo);
            if (getter == null) {
                // A non-blocking server lookup can observe a chunk while it is
                // still loading.  Treat it as solid for movement collision.  The
                // old behavior treated it as air, which made the player sink
                // through the edge of an unloaded chunk and then triggered the
                // anticheat teleport.  This is conservative and, importantly,
                // still never waits for chunk generation.
                if (this.onlySuffocatingBlocks) {
                    continue;
                }
                double localX = this.localX(xLo);
                double localY = (double) blockY - (double) this.origin.originBlockY();
                double localZ = this.localZ(zLo);
                VoxelShape localShape = this.translate(Shapes.block(), localX, localY, localZ);
                if (this.localBox.intersects(localX, localY, localZ,
                            localX + 1.0D, localY + 1.0D, localZ + 1.0D)
                        && Shapes.joinIsNotEmpty(localShape, this.queryShape(), BooleanOp.AND)) {
                    return localShape;
                }
                continue;
            }

            this.pos.set(xHi, xLo, blockY, zHi, zLo);
            BlockState state = getter.getBlockState(this.pos);
            if (this.onlySuffocatingBlocks && !state.isSuffocating(getter, this.pos)
                    || type == Cursor3D.TYPE_FACE && !state.hasLargeCollisionShape()
                    || type == Cursor3D.TYPE_EDGE && !state.is(Blocks.MOVING_PISTON)) {
                continue;
            }

            VoxelShape shape = state.getCollisionShape(this.collisionGetter, this.pos, this.context);
            if (shape.isEmpty()) {
                continue;
            }
            // Exact integer subtraction happens before conversion to local doubles.
            double localX = this.localX(xLo);
            double localY = (double) blockY - (double) this.origin.originBlockY();
            double localZ = this.localZ(zLo);
            if (shape == Shapes.block()) {
                if (this.localBox.intersects(localX, localY, localZ,
                        localX + 1.0D, localY + 1.0D, localZ + 1.0D)) {
                    return this.translate(shape, localX, localY, localZ);
                }
                continue;
            }
            VoxelShape localShape = this.translate(shape, localX, localY, localZ);
            if (!Shapes.joinIsNotEmpty(localShape, this.queryShape(), BooleanOp.AND)) {
                continue;
            }
            return localShape;
        }

        return null;
    }

    // Constructor validation bounds every visited coordinate relative to origin.
    // Modular low-word subtraction therefore equals the full signed local delta,
    // even when traversal crosses a high-word carry. Never use this without that guard.
    private double localX(long low) {
        long delta = low - this.origin.originBlockX().lo();
        return delta;
    }

    private double localZ(long low) {
        long delta = low - this.origin.originBlockZ().lo();
        return delta;
    }
}
