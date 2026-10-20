package net.minecraft.world.phys;

/** Compatibility vector retaining its split-coordinate world position. */
public final class ExactVec3 extends Vec3 {
    private final SectorVec3 exact;

    public ExactVec3(SectorVec3 exact) {
        this(exact, exact.toApproximateVec3());
    }

    private ExactVec3(SectorVec3 exact, Vec3 approximate) {
        super(approximate.x, exact.y(), approximate.z);
        this.exact = exact;
    }

    public static SectorVec3 position(Vec3 vector) {
        return vector instanceof ExactVec3 exact ? exact.exact : SectorVec3.fromApproximate(vector);
    }

    @Override
    public Vec3 add(double x, double y, double z) {
        return new ExactVec3(this.exact.add(x, y, z));
    }
}
