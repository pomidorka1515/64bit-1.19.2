package net.minecraft.world.level.storage;

import net.minecraft.core.BlockPos;

public interface WritableLevelData extends LevelData {
    void setXSpawn(long pXSpawn);

    void setYSpawn(int pYSpawn);

    void setZSpawn(long pZSpawn);

    void setSpawnAngle(float pSpawnAngle);

    default void setSpawn(BlockPos pSpawnPoint, float pSpawnAngle) {
        this.setXSpawn(pSpawnPoint.getX());
        this.setYSpawn(pSpawnPoint.getY());
        this.setZSpawn(pSpawnPoint.getZ());
        this.setSpawnAngle(pSpawnAngle);
    }
}
