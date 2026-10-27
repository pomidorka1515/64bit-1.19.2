package net.minecraft.world.level.storage;

import java.util.Locale;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelHeightAccessor;

public interface LevelData {
    Int1088 getXSpawn();

    int getYSpawn();

    Int1088 getZSpawn();

    float getSpawnAngle();

    long getGameTime();

    long getDayTime();

    boolean isThundering();

    boolean isRaining();

    void setRaining(boolean pRaining);

    boolean isHardcore();

    GameRules getGameRules();

    Difficulty getDifficulty();

    boolean isDifficultyLocked();

    default void fillCrashReportCategory(CrashReportCategory pCrashReportCategory, LevelHeightAccessor pLevel) {
        pCrashReportCategory.setDetail("Level spawn location", () -> {
            return CrashReportCategory.formatLocation(pLevel, new BlockPos(this.getXSpawn(), this.getYSpawn(), this.getZSpawn()));
        });
        pCrashReportCategory.setDetail("Level time", () -> {
            return String.format(Locale.ROOT, "%d game time, %d day time", this.getGameTime(), this.getDayTime());
        });
    }
}
