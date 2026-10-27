package net.minecraft.world.level.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;

public interface WritableLevelData extends LevelData {
    void setXSpawn(Int1088 pXSpawn);

    default void setXSpawn(long pXSpawn) {
        this.setXSpawn(Int1088.fromLong(pXSpawn));
    }

    void setYSpawn(int pYSpawn);

    void setZSpawn(Int1088 pZSpawn);

    default void setZSpawn(long pZSpawn) {
        this.setZSpawn(Int1088.fromLong(pZSpawn));
    }

    void setSpawnAngle(float pSpawnAngle);

    default void setSpawn(BlockPos pSpawnPoint, float pSpawnAngle) {
        this.setXSpawn(pSpawnPoint.getX());
        this.setYSpawn(pSpawnPoint.getY());
        this.setZSpawn(pSpawnPoint.getZ());
        this.setSpawnAngle(pSpawnAngle);
    }
}
