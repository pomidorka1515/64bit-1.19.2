package net.minecraft.core;

public interface Position {
    default net.minecraft.world.phys.SectorVec3 exactPosition() {
        return this instanceof net.minecraft.world.phys.Vec3 vector
                ? net.minecraft.world.phys.SectorVec3.fromApproximate(vector)
                : net.minecraft.world.phys.SectorVec3.fromApproximate(this.x(), this.y(), this.z());
    }

    double x();

    double y();

    double z();
}
