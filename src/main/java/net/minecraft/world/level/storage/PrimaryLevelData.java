package net.minecraft.world.level.storage;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Lifecycle;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.CrashReportCategory;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.EndRingsMode;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.synth.FarlandsMode;
import net.minecraft.world.level.timers.TimerCallbacks;
import net.minecraft.world.level.timers.TimerQueue;
import org.slf4j.Logger;

public class PrimaryLevelData implements ServerLevelData, WorldData {
    private static final Logger LOGGER = LogUtils.getLogger();
    protected static final String PLAYER = "Player";
    protected static final String WORLD_GEN_SETTINGS = "WorldGenSettings";
    private LevelSettings settings;
    private final WorldGenSettings worldGenSettings;
    private final Lifecycle worldGenSettingsLifecycle;
    private Int128 xSpawn;
    private int ySpawn;
    private Int128 zSpawn;
    private float spawnAngle;
    private long gameTime;
    private long dayTime;
    @Nullable
    private final DataFixer fixerUpper;
    private final int playerDataVersion;
    private boolean upgradedPlayerTag;
    @Nullable
    private CompoundTag loadedPlayerTag;
    private final int version;
    private int clearWeatherTime;
    private boolean raining;
    private int rainTime;
    private boolean thundering;
    private int thunderTime;
    private boolean initialized;
    private boolean difficultyLocked;
    private WorldBorder.Settings worldBorder;
    private CompoundTag endDragonFightData;
    @Nullable
    private CompoundTag customBossEvents;
    private int wanderingTraderSpawnDelay;
    private int wanderingTraderSpawnChance;
    @Nullable
    private UUID wanderingTraderId;
    private final Set<String> knownServerBrands;
    private boolean wasModded;
    private final TimerQueue<MinecraftServer> scheduledEvents;

    private PrimaryLevelData(@Nullable DataFixer pFixerUpper, int pPlayerDataVersion, @Nullable CompoundTag pLoadedPlayerTag, boolean pWasModded, Int128 pXSpawn, int pYSpawn, Int128 pZSpawn, float pSpawnAngle, long pGameTime, long pDayTime, int pVersion, int pClearWeatherTime, int pRainTime, boolean pRaining, int pThunderTime, boolean pThundering, boolean pInitialized, boolean pDifficultyLocked, WorldBorder.Settings pWorldBorder, int pWanderingTraderSpawnDelay, int pWanderingTraderSpawnChance, @Nullable UUID pWanderingTraderId, Set<String> pKnownServerBrands, TimerQueue<MinecraftServer> pScheduledEvents, @Nullable CompoundTag pCustomBossEvents, CompoundTag pEndDragonFightData, LevelSettings pSettings, WorldGenSettings pWorldGenSettings, Lifecycle pWorldGenSettingsLifecycle) {
        if (!pWorldGenSettings.dimensions().containsKey(LevelStem.OVERWORLD)) {
            throw new IllegalStateException("Missing Overworld dimension data");
        } else {
            this.fixerUpper = pFixerUpper;
            this.wasModded = pWasModded;
            this.xSpawn = pXSpawn;
            this.ySpawn = pYSpawn;
            this.zSpawn = pZSpawn;
            this.spawnAngle = pSpawnAngle;
            this.gameTime = pGameTime;
            this.dayTime = pDayTime;
            this.version = pVersion;
            this.clearWeatherTime = pClearWeatherTime;
            this.rainTime = pRainTime;
            this.raining = pRaining;
            this.thunderTime = pThunderTime;
            this.thundering = pThundering;
            this.initialized = pInitialized;
            this.difficultyLocked = pDifficultyLocked;
            this.worldBorder = pWorldBorder;
            this.wanderingTraderSpawnDelay = pWanderingTraderSpawnDelay;
            this.wanderingTraderSpawnChance = pWanderingTraderSpawnChance;
            this.wanderingTraderId = pWanderingTraderId;
            this.knownServerBrands = pKnownServerBrands;
            this.loadedPlayerTag = pLoadedPlayerTag;
            this.playerDataVersion = pPlayerDataVersion;
            this.scheduledEvents = pScheduledEvents;
            this.customBossEvents = pCustomBossEvents;
            this.endDragonFightData = pEndDragonFightData;
            this.settings = pSettings;
            this.worldGenSettings = pWorldGenSettings;
            this.worldGenSettingsLifecycle = pWorldGenSettingsLifecycle;
        }
    }

    public PrimaryLevelData(LevelSettings pLevelSettings, WorldGenSettings pWorldGenSettings, Lifecycle pWorldGenSettingsLifecycle) {
        this((DataFixer) null, SharedConstants.getCurrentVersion().getWorldVersion(), (CompoundTag) null, false, Int128.ZERO, 0, Int128.ZERO, 0.0F, 0L, 0L, ForkSaveFormat.STORAGE_VERSION, 0, 0, false, 0, false, false, false, WorldBorder.DEFAULT_SETTINGS, 0, 0, (UUID) null, Sets.newLinkedHashSet(), new TimerQueue<>(TimerCallbacks.SERVER_CALLBACKS), (CompoundTag) null, new CompoundTag(), pLevelSettings.copy(), pWorldGenSettings, pWorldGenSettingsLifecycle);
    }

    public static PrimaryLevelData parse(Dynamic<Tag> pDynamic, DataFixer pDataFixer, int pVersion, @Nullable CompoundTag pPlayerNBT, LevelSettings pLevelSettings, LevelVersion pVersionData, WorldGenSettings pGeneratorSettings, Lifecycle pLifecycle) {
        long i = pDynamic.get("Time").asLong(0L);
        CompoundTag compoundtag = (CompoundTag) pDynamic.get("DragonFight").result().map(Dynamic::getValue).orElseGet(() -> {
            return pDynamic.get("DimensionData").get("1").get("DragonFight").orElseEmptyMap().getValue();
        });
        String s = pDynamic.get(FarlandsMode.LEVEL_DATA_KEY).asString("");
        FarlandsMode.setMode(s.isEmpty() ? (pDynamic.get(FarlandsMode.LEVEL_DATA_KEY).asBoolean(true) ? FarlandsMode.Mode.BIT_32 : FarlandsMode.Mode.BIT_64) : FarlandsMode.fromSerializedName(s));
        EndRingsMode.setEnabled(pDynamic.get(EndRingsMode.LEVEL_DATA_KEY).asBoolean(true));
        if (!(pDynamic.getValue() instanceof CompoundTag levelTag)) {
            throw new IllegalArgumentException("Level data must be a compound");
        }
        return new PrimaryLevelData(pDataFixer, pVersion, pPlayerNBT, pDynamic.get("WasModded").asBoolean(false), NbtUtils.readInt128(levelTag, "SpawnX"), pDynamic.get("SpawnY").asInt(0), NbtUtils.readInt128(levelTag, "SpawnZ"), pDynamic.get("SpawnAngle").asFloat(0.0F), i, pDynamic.get("DayTime").asLong(i), pVersionData.levelDataVersion(), pDynamic.get("clearWeatherTime").asInt(0), pDynamic.get("rainTime").asInt(0), pDynamic.get("raining").asBoolean(false), pDynamic.get("thunderTime").asInt(0), pDynamic.get("thundering").asBoolean(false), pDynamic.get("initialized").asBoolean(true), pDynamic.get("DifficultyLocked").asBoolean(false), WorldBorder.Settings.read(pDynamic, WorldBorder.DEFAULT_SETTINGS), pDynamic.get("WanderingTraderSpawnDelay").asInt(0), pDynamic.get("WanderingTraderSpawnChance").asInt(0), pDynamic.get("WanderingTraderId").read(UUIDUtil.CODEC).result().orElse((UUID) null), pDynamic.get("ServerBrands").asStream().flatMap((p_78529_) -> {
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     return p_78529_.asString().result().stream();
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 })
                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  .collect(Collectors.toCollection(Sets::newLinkedHashSet)),
                new TimerQueue<>(TimerCallbacks.SERVER_CALLBACKS, pDynamic.get("ScheduledEvents").asStream()), (CompoundTag) pDynamic.get("CustomBossEvents").orElseEmptyMap().getValue(), compoundtag, pLevelSettings, pGeneratorSettings, pLifecycle);
    }

    public CompoundTag createTag(RegistryAccess pRegistries, @Nullable CompoundTag pHostPlayerNBT) {
        this.updatePlayerTag();
        if (pHostPlayerNBT == null) {
            pHostPlayerNBT = this.loadedPlayerTag;
        }

        CompoundTag compoundtag = new CompoundTag();
        this.setTagData(pRegistries, compoundtag, pHostPlayerNBT);
        return compoundtag;
    }

    private void setTagData(RegistryAccess pRegistry, CompoundTag pNbt, @Nullable CompoundTag pPlayerNBT) {
        ListTag listtag = new ListTag();
        this.knownServerBrands.stream().map(StringTag::valueOf).forEach(listtag::add);
        pNbt.put("ServerBrands", listtag);
        pNbt.putBoolean("WasModded", this.wasModded);
        pNbt.putString(FarlandsMode.LEVEL_DATA_KEY, FarlandsMode.getMode().serializedName());
        pNbt.putBoolean(EndRingsMode.LEVEL_DATA_KEY, EndRingsMode.isEnabled());
        CompoundTag compoundtag = new CompoundTag();
        compoundtag.putString("Name", SharedConstants.getCurrentVersion().getName());
        compoundtag.putInt("Id", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        compoundtag.putBoolean("Snapshot", !SharedConstants.getCurrentVersion().isStable());
        compoundtag.putString("Series", SharedConstants.getCurrentVersion().getDataVersion().getSeries());
        pNbt.put("Version", compoundtag);
        pNbt.putInt("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());
        DynamicOps<Tag> dynamicops = RegistryOps.create(NbtOps.INSTANCE, pRegistry);
        WorldGenSettings.CODEC.encodeStart(dynamicops, this.worldGenSettings).resultOrPartial(Util.prefix("WorldGenSettings: ", LOGGER::error)).ifPresent((p_78574_) -> {
            pNbt.put("WorldGenSettings", p_78574_);
        });
        pNbt.putInt("GameType", this.settings.gameType().getId());
        NbtUtils.writeInt128(pNbt, "SpawnX", this.xSpawn);
        pNbt.putInt("SpawnY", this.ySpawn);
        NbtUtils.writeInt128(pNbt, "SpawnZ", this.zSpawn);
        pNbt.putFloat("SpawnAngle", this.spawnAngle);
        pNbt.putLong("Time", this.gameTime);
        pNbt.putLong("DayTime", this.dayTime);
        pNbt.putLong("LastPlayed", Util.getEpochMillis());
        pNbt.putString("LevelName", this.settings.levelName());
        pNbt.putInt("version", ForkSaveFormat.STORAGE_VERSION);
        pNbt.putInt("clearWeatherTime", this.clearWeatherTime);
        pNbt.putInt("rainTime", this.rainTime);
        pNbt.putBoolean("raining", this.raining);
        pNbt.putInt("thunderTime", this.thunderTime);
        pNbt.putBoolean("thundering", this.thundering);
        pNbt.putBoolean("hardcore", this.settings.hardcore());
        pNbt.putBoolean("allowCommands", this.settings.allowCommands());
        pNbt.putBoolean("initialized", this.initialized);
        this.worldBorder.write(pNbt);
        pNbt.putByte("Difficulty", (byte) this.settings.difficulty().getId());
        pNbt.putBoolean("DifficultyLocked", this.difficultyLocked);
        pNbt.put("GameRules", this.settings.gameRules().createTag());
        pNbt.put("DragonFight", this.endDragonFightData);
        if (pPlayerNBT != null) {
            pNbt.put("Player", pPlayerNBT);
        }

        DataPackConfig.CODEC.encodeStart(NbtOps.INSTANCE, this.settings.getDataPackConfig()).result().ifPresent((p_78560_) -> {
            pNbt.put("DataPacks", p_78560_);
        });
        if (this.customBossEvents != null) {
            pNbt.put("CustomBossEvents", this.customBossEvents);
        }

        pNbt.put("ScheduledEvents", this.scheduledEvents.store());
        pNbt.putInt("WanderingTraderSpawnDelay", this.wanderingTraderSpawnDelay);
        pNbt.putInt("WanderingTraderSpawnChance", this.wanderingTraderSpawnChance);
        if (this.wanderingTraderId != null) {
            pNbt.putUUID("WanderingTraderId", this.wanderingTraderId);
        }
    }

    public Int128 getXSpawn() {
        return this.xSpawn;
    }

    public int getYSpawn() {
        return this.ySpawn;
    }

    public Int128 getZSpawn() {
        return this.zSpawn;
    }

    public float getSpawnAngle() {
        return this.spawnAngle;
    }

    public long getGameTime() {
        return this.gameTime;
    }

    public long getDayTime() {
        return this.dayTime;
    }

    private void updatePlayerTag() {
        if (!this.upgradedPlayerTag && this.loadedPlayerTag != null) {
            if (this.playerDataVersion < SharedConstants.getCurrentVersion().getWorldVersion()) {
                if (this.fixerUpper == null) {
                    throw (NullPointerException) Util.pauseInIde(new NullPointerException("Fixer Upper not set inside LevelData, and the player tag is not upgraded."));
                }

                this.loadedPlayerTag = NbtUtils.update(this.fixerUpper, DataFixTypes.PLAYER, this.loadedPlayerTag, this.playerDataVersion);
            }

            this.upgradedPlayerTag = true;
        }
    }

    public CompoundTag getLoadedPlayerTag() {
        this.updatePlayerTag();
        return this.loadedPlayerTag;
    }

    public void setXSpawn(Int128 pX) {
        this.xSpawn = pX;
    }

    public void setYSpawn(int pY) {
        this.ySpawn = pY;
    }

    public void setZSpawn(Int128 pZ) {
        this.zSpawn = pZ;
    }

    public void setSpawnAngle(float pAngle) {
        this.spawnAngle = pAngle;
    }

    public void setGameTime(long pTime) {
        this.gameTime = pTime;
    }

    public void setDayTime(long pTime) {
        this.dayTime = pTime;
    }

    public void setSpawn(BlockPos pSpawnPoint, float pAngle) {
        this.xSpawn = pSpawnPoint.getX();
        this.ySpawn = pSpawnPoint.getY();
        this.zSpawn = pSpawnPoint.getZ();
        this.spawnAngle = pAngle;
    }

    public String getLevelName() {
        return this.settings.levelName();
    }

    public int getVersion() {
        return this.version;
    }

    public int getClearWeatherTime() {
        return this.clearWeatherTime;
    }

    public void setClearWeatherTime(int pTime) {
        this.clearWeatherTime = pTime;
    }

    public boolean isThundering() {
        return this.thundering;
    }

    public void setThundering(boolean pThundering) {
        this.thundering = pThundering;
    }

    public int getThunderTime() {
        return this.thunderTime;
    }

    public void setThunderTime(int pTime) {
        this.thunderTime = pTime;
    }

    public boolean isRaining() {
        return this.raining;
    }

    public void setRaining(boolean pIsRaining) {
        this.raining = pIsRaining;
    }

    public int getRainTime() {
        return this.rainTime;
    }

    public void setRainTime(int pTime) {
        this.rainTime = pTime;
    }

    public GameType getGameType() {
        return this.settings.gameType();
    }

    public void setGameType(GameType pType) {
        this.settings = this.settings.withGameType(pType);
    }

    public boolean isHardcore() {
        return this.settings.hardcore();
    }

    public boolean getAllowCommands() {
        return this.settings.allowCommands();
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    public void setInitialized(boolean pInitialized) {
        this.initialized = pInitialized;
    }

    public GameRules getGameRules() {
        return this.settings.gameRules();
    }

    public WorldBorder.Settings getWorldBorder() {
        return this.worldBorder;
    }

    public void setWorldBorder(WorldBorder.Settings pSerializer) {
        this.worldBorder = pSerializer;
    }

    public Difficulty getDifficulty() {
        return this.settings.difficulty();
    }

    public void setDifficulty(Difficulty pDifficulty) {
        this.settings = this.settings.withDifficulty(pDifficulty);
    }

    public boolean isDifficultyLocked() {
        return this.difficultyLocked;
    }

    public void setDifficultyLocked(boolean pLocked) {
        this.difficultyLocked = pLocked;
    }

    public TimerQueue<MinecraftServer> getScheduledEvents() {
        return this.scheduledEvents;
    }

    public void fillCrashReportCategory(CrashReportCategory pCrashReportCategory, LevelHeightAccessor pLevel) {
        ServerLevelData.super.fillCrashReportCategory(pCrashReportCategory, pLevel);
        WorldData.super.fillCrashReportCategory(pCrashReportCategory);
    }

    public WorldGenSettings worldGenSettings() {
        return this.worldGenSettings;
    }

    public Lifecycle worldGenSettingsLifecycle() {
        return this.worldGenSettingsLifecycle;
    }

    public CompoundTag endDragonFightData() {
        return this.endDragonFightData;
    }

    public void setEndDragonFightData(CompoundTag pNbt) {
        this.endDragonFightData = pNbt;
    }

    public DataPackConfig getDataPackConfig() {
        return this.settings.getDataPackConfig();
    }

    public void setDataPackConfig(DataPackConfig pCodec) {
        this.settings = this.settings.withDataPackConfig(pCodec);
    }

    @Nullable
    public CompoundTag getCustomBossEvents() {
        return this.customBossEvents;
    }

    public void setCustomBossEvents(@Nullable CompoundTag pNbt) {
        this.customBossEvents = pNbt;
    }

    public int getWanderingTraderSpawnDelay() {
        return this.wanderingTraderSpawnDelay;
    }

    public void setWanderingTraderSpawnDelay(int pDelay) {
        this.wanderingTraderSpawnDelay = pDelay;
    }

    public int getWanderingTraderSpawnChance() {
        return this.wanderingTraderSpawnChance;
    }

    public void setWanderingTraderSpawnChance(int pChance) {
        this.wanderingTraderSpawnChance = pChance;
    }

    @Nullable
    public UUID getWanderingTraderId() {
        return this.wanderingTraderId;
    }

    public void setWanderingTraderId(UUID pId) {
        this.wanderingTraderId = pId;
    }

    public void setModdedInfo(String pName, boolean pIsModded) {
        this.knownServerBrands.add(pName);
        this.wasModded |= pIsModded;
    }

    public boolean wasModded() {
        return this.wasModded;
    }

    public Set<String> getKnownServerBrands() {
        return ImmutableSet.copyOf(this.knownServerBrands);
    }

    public ServerLevelData overworldData() {
        return this;
    }

    public LevelSettings getLevelSettings() {
        return this.settings.copy();
    }
}
