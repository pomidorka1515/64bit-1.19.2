package net.minecraft.world.level.storage;

import com.mojang.serialization.Lifecycle;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldGenSettings;

public interface WorldData {
    net.minecraft.world.level.levelgen.synth.GeneratorConfig generatorConfig();

    void setGeneratorConfig(net.minecraft.world.level.levelgen.synth.GeneratorConfig config);

    int ANVIL_VERSION_ID = 19133;
    int MCREGION_VERSION_ID = 19132;

    DataPackConfig getDataPackConfig();

    void setDataPackConfig(DataPackConfig pCodec);

    boolean wasModded();

    Set<String> getKnownServerBrands();

    void setModdedInfo(String pName, boolean pIsModded);

    default void fillCrashReportCategory(CrashReportCategory pCategory) {
        pCategory.setDetail("Known server brands", () -> {
            return String.join(", ", this.getKnownServerBrands());
        });
        pCategory.setDetail("Level was modded", () -> {
            return Boolean.toString(this.wasModded());
        });
        pCategory.setDetail("Level storage version", () -> {
            int i = this.getVersion();
            return String.format(Locale.ROOT, "0x%05X - %s", i, this.getStorageVersionName(i));
        });
    }

    default String getStorageVersionName(int pStorageVersionId) {
        switch (pStorageVersionId) {
            case 19132:
                return "McRegion";
            case 19133:
                return "Anvil";
            default:
                return "Unknown?";
        }
    }

    @Nullable
    CompoundTag getCustomBossEvents();

    void setCustomBossEvents(@Nullable CompoundTag pNbt);

    ServerLevelData overworldData();

    LevelSettings getLevelSettings();

    CompoundTag createTag(RegistryAccess pRegistries, @Nullable CompoundTag pHostPlayerNBT);

    boolean isHardcore();

    int getVersion();

    String getLevelName();

    GameType getGameType();

    void setGameType(GameType pType);

    boolean getAllowCommands();

    Difficulty getDifficulty();

    void setDifficulty(Difficulty pDifficulty);

    boolean isDifficultyLocked();

    void setDifficultyLocked(boolean pLocked);

    GameRules getGameRules();

    @Nullable
    CompoundTag getLoadedPlayerTag();

    CompoundTag endDragonFightData();

    void setEndDragonFightData(CompoundTag pNbt);

    WorldGenSettings worldGenSettings();

    Lifecycle worldGenSettingsLifecycle();
}
