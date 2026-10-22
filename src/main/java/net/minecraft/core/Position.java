package net.minecraft.core;

public interface Position {
    net.minecraft.world.phys.SectorVec3 exactPosition();

    double x();

    double y();

    double z();
}
