package net.minecraft.world.level.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;

public interface WritableLevelData extends LevelData {
    void setXSpawn(Int128 pXSpawn);

    default void setXSpawn(long pXSpawn) {
        this.setXSpawn(Int128.fromLong(pXSpawn));
    }

    void setYSpawn(int pYSpawn);

    void setZSpawn(Int128 pZSpawn);

    default void setZSpawn(long pZSpawn) {
        this.setZSpawn(Int128.fromLong(pZSpawn));
    }

    void setSpawnAngle(float pSpawnAngle);

    default void setSpawn(BlockPos pSpawnPoint, float pSpawnAngle) {
        this.setXSpawn(pSpawnPoint.getX());
        this.setYSpawn(pSpawnPoint.getY());
        this.setZSpawn(pSpawnPoint.getZ());
        this.setSpawnAngle(pSpawnAngle);
    }
}
