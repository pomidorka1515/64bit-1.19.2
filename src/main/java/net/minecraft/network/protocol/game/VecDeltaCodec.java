package net.minecraft.network.protocol.game;

import net.minecraft.core.Int128;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class VecDeltaCodec {
    private static final double TRUNCATION_STEPS = 4096.0D;
    /** Signed-short wire range, in 1/4096-block steps. */
    public static final long MAX_RELATIVE_DELTA = 32767L;
    public static final long MIN_RELATIVE_DELTA = -32768L;
    /** Outside the relative wire range: ServerEntity must send an absolute teleport. */
    public static final long ABSOLUTE_POSITION_REQUIRED = Long.MAX_VALUE;

    private SectorVec3 sectorBase = SectorVec3.fromApproximate(Vec3.ZERO);
    private Vec3 base = Vec3.ZERO;

    private static long encode(double delta) {
        double steps = Math.floor(delta * TRUNCATION_STEPS);
        // Check before narrowing: Java's double-to-long cast saturates, and
        // flooring via Mth.lfloor can wrap at Long.MIN_VALUE.
        if (!Double.isFinite(steps) || steps < MIN_RELATIVE_DELTA || steps > MAX_RELATIVE_DELTA) {
            return ABSOLUTE_POSITION_REQUIRED;
        }
        return (long) steps;
    }

    private static long encode(Int128 block, double fraction, Int128 baseBlock, double baseFraction) {
        // Saturation prevents opposite Int128 edges wrapping into a tiny delta.
        // Only bounded local differences are ever projected to double.
        Int128 difference = block.subtractSaturated(baseBlock);
        if (difference.compareTo(Int128.fromLong(-8L)) < 0
                || difference.compareTo(Int128.fromLong(8L)) > 0) {
            return ABSOLUTE_POSITION_REQUIRED;
        }
        return encode((double) difference.lo() + (fraction - baseFraction));
    }

    private static double decode(long delta) {
        if (delta < MIN_RELATIVE_DELTA || delta > MAX_RELATIVE_DELTA) {
            throw new IllegalArgumentException("Relative movement exceeds wire range: " + delta);
        }
        return (double) delta / TRUNCATION_STEPS;
    }

    /** Lossy compatibility view of the exact decoded target. */
    public Vec3 decode(long x, long y, long z) {
        SectorVec3 position = this.decodeExact(x, y, z);
        return x == 0L && y == 0L && z == 0L ? this.base : position.toApproximateVec3();
    }

    /**
     * Applies validated local deltas to the last network target, never the render
     * interpolation position. SectorVec3 clamps outward movement at Int128 world
     * edges rather than wrapping; zero deltas preserve the exact fractions.
     */
    public SectorVec3 decodeExact(long x, long y, long z) {
        double dx = decode(x);
        double dy = decode(y);
        double dz = decode(z);
        if (x == 0L && y == 0L && z == 0L) {
            return this.sectorBase;
        }
        return this.sectorBase.add(dx, dy, dz);
    }

    public long encodeX(Vec3 position) {
        return this.encodeX(SectorVec3.fromApproximate(position));
    }

    public long encodeX(SectorVec3 position) {
        return encode(position.blockX(), position.subX(), this.sectorBase.blockX(), this.sectorBase.subX());
    }

    public long encodeY(Vec3 position) {
        return encode(position.y - this.sectorBase.y());
    }

    public long encodeY(SectorVec3 position) {
        return encode(position.y() - this.sectorBase.y());
    }

    public long encodeZ(Vec3 position) {
        return this.encodeZ(SectorVec3.fromApproximate(position));
    }

    public long encodeZ(SectorVec3 position) {
        return encode(position.blockZ(), position.subZ(), this.sectorBase.blockZ(), this.sectorBase.subZ());
    }

    public Vec3 delta(Vec3 position) {
        return SectorVec3.fromApproximate(position).relativeTo(this.sectorBase);
    }

    public void setBase(Vec3 position) {
        this.setBase(SectorVec3.fromApproximate(position));
    }

    public void setBase(SectorVec3 position) {
        this.sectorBase = position;
        this.base = position.toApproximateVec3();
    }

    public SectorVec3 getSectorBase() {
        return this.sectorBase;
    }
}
