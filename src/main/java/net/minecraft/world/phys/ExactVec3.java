package net.minecraft.world.phys;

/** Compatibility vector retaining its split-coordinate world position. */
public final class ExactVec3 extends Vec3 {
    private final SectorVec3 exact;

    public ExactVec3(SectorVec3 exact) {
        super(net.minecraft.world.level.WorldBounds.clampAbsoluteDouble(exact.blockX().toDoubleSaturated() + exact.subX()),
                exact.y(), net.minecraft.world.level.WorldBounds.clampAbsoluteDouble(exact.blockZ().toDoubleSaturated() + exact.subZ()));
        this.exact = exact;
    }

    public static SectorVec3 position(Vec3 vector) {
        return vector instanceof ExactVec3 exact ? exact.exact : SectorVec3.fromApproximate(vector);
    }

    @Override
    public Vec3 add(double x, double y, double z) {
        return new ExactVec3(this.exact.add(x, y, z));
    }

    @Override
    public SectorVec3 exactPosition() {
        return this.exact;
    }
}
