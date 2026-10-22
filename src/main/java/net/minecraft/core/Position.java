package net.minecraft.core;

public interface Position {
    net.minecraft.world.phys.SectorVec3 exactPosition();

    double y();
}
