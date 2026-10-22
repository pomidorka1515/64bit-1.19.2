package net.minecraft.util.worldupdate;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatMaps;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenCustomHashMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadFactory;
import net.minecraft.core.Int128;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.chunk.storage.RegionFileNames;
import net.minecraft.world.level.chunk.storage.RegionPos;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.slf4j.Logger;

public class WorldUpgrader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadFactory THREAD_FACTORY = (new ThreadFactoryBuilder()).setDaemon(true).build();
    private final WorldGenSettings worldGenSettings;
    private final boolean eraseCache;
    private final LevelStorageSource.LevelStorageAccess levelStorage;
    private final Thread thread;
    private volatile boolean running = true;
    private volatile boolean finished;
    private volatile float progress;
    private volatile int totalChunks;
    private volatile int converted;
    private volatile int skipped;
    private final Object2FloatMap<ResourceKey<Level>> progressMap = Object2FloatMaps.synchronize(new Object2FloatOpenCustomHashMap<>(Util.identityStrategy()));
    private volatile Component status = Component.translatable("optimizeWorld.stage.counting");

    public WorldUpgrader(LevelStorageSource.LevelStorageAccess pLevelStorage, WorldGenSettings pWorldGenSettings, boolean pEraseCache) {
        this.worldGenSettings = pWorldGenSettings;
        this.eraseCache = pEraseCache;
        this.levelStorage = pLevelStorage;
        this.thread = THREAD_FACTORY.newThread(this::work);
        this.thread.setUncaughtExceptionHandler((p_18825_, p_18826_) -> {
            LOGGER.error("Error upgrading world", p_18826_);
            this.status = Component.translatable("optimizeWorld.stage.failed");
            this.finished = true;
        });
        this.thread.start();
    }

    public void cancel() {
        this.running = false;

        try {
            this.thread.join();
        } catch (InterruptedException interruptedexception) {
        }
    }

    private void work() {
        this.totalChunks = 0;
        ImmutableMap.Builder<ResourceKey<Level>, ListIterator<ChunkPos>> builder = ImmutableMap.builder();
        ImmutableSet<ResourceKey<Level>> immutableset = this.worldGenSettings.levels();

        for (ResourceKey<Level> resourcekey : immutableset) {
            List<ChunkPos> list = this.getAllChunkPos(resourcekey);
            builder.put(resourcekey, list.listIterator());
            this.totalChunks += list.size();
        }

        if (this.totalChunks == 0) {
            this.finished = true;
        } else {
            float f1 = (float) this.totalChunks;
            ImmutableMap<ResourceKey<Level>, ListIterator<ChunkPos>> immutablemap = builder.build();
            ImmutableMap.Builder<ResourceKey<Level>, ChunkStorage> builder1 = ImmutableMap.builder();

            for (ResourceKey<Level> resourcekey1 : immutableset) {
                Path path = this.levelStorage.getDimensionPath(resourcekey1);
                builder1.put(resourcekey1, new ChunkStorage(path.resolve("region"), true));
            }

            ImmutableMap<ResourceKey<Level>, ChunkStorage> immutablemap1 = builder1.build();
            long j = Util.getMillis();
            this.status = Component.translatable("optimizeWorld.stage.upgrading");

            while (this.running) {
                boolean flag = false;
                float f = 0.0F;

                for (ResourceKey<Level> resourcekey2 : immutableset) {
                    ListIterator<ChunkPos> listiterator = immutablemap.get(resourcekey2);
                    ChunkStorage chunkstorage = immutablemap1.get(resourcekey2);
                    if (listiterator.hasNext()) {
                        ChunkPos chunkpos = listiterator.next();
                        boolean flag1 = false;

                        try {
                            CompoundTag compoundtag = chunkstorage.read(chunkpos).join().orElse((CompoundTag) null);
                            if (compoundtag != null) {
                                ChunkPos chunkpos1 = new ChunkPos(NbtUtils.readInt128(compoundtag, "xPos"), NbtUtils.readInt128(compoundtag, "zPos"));
                                if (!chunkpos1.equals(chunkpos)) {
                                    LOGGER.warn("Chunk {} has invalid position {}", chunkpos, chunkpos1);
                                }

                                boolean flag2 = false;
                                if (this.eraseCache) {
                                    flag2 = flag2 || compoundtag.contains("Heightmaps");
                                    compoundtag.remove("Heightmaps");
                                    flag2 = flag2 || compoundtag.contains("isLightOn");
                                    compoundtag.remove("isLightOn");
                                    ListTag listtag = compoundtag.getList("sections", 10);

                                    for (int i = 0; i < listtag.size(); ++i) {
                                        CompoundTag compoundtag2 = listtag.getCompound(i);
                                        flag2 = flag2 || compoundtag2.contains("BlockLight");
                                        compoundtag2.remove("BlockLight");
                                        flag2 = flag2 || compoundtag2.contains("SkyLight");
                                        compoundtag2.remove("SkyLight");
                                    }
                                }

                                if (flag2) {
                                    chunkstorage.write(chunkpos, compoundtag);
                                    flag1 = true;
                                }
                            }
                        } catch (CompletionException | ReportedException reportedexception) {
                            Throwable throwable = reportedexception.getCause();
                            if (!(throwable instanceof IOException)) {
                                throw reportedexception;
                            }

                            LOGGER.error("Error upgrading chunk {}", chunkpos, throwable);
                        }

                        if (flag1) {
                            ++this.converted;
                        } else {
                            ++this.skipped;
                        }

                        flag = true;
                    }

                    float f2 = (float) listiterator.nextIndex() / f1;
                    this.progressMap.put(resourcekey2, f2);
                    f += f2;
                }

                this.progress = f;
                if (!flag) {
                    this.running = false;
                }
            }

            this.status = Component.translatable("optimizeWorld.stage.finished");

            for (ChunkStorage chunkstorage1 : immutablemap1.values()) {
                try {
                    chunkstorage1.close();
                } catch (IOException ioexception) {
                    LOGGER.error("Error upgrading chunk", (Throwable) ioexception);
                }
            }

            j = Util.getMillis() - j;
            LOGGER.info("World optimizaton finished after {} ms", (long) j);
            this.finished = true;
        }
    }

    private List<ChunkPos> getAllChunkPos(ResourceKey<Level> p_18831_) {
        File file1 = this.levelStorage.getDimensionPath(p_18831_).toFile();
        File file2 = new File(file1, "region");
        return getAllChunkPos(file2);
    }

    static List<ChunkPos> getAllChunkPos(File file2) {
        File[] afile = file2.listFiles((p_18822_, p_18823_) -> {
            return p_18823_.endsWith(".mca");
        });
        if (afile == null) {
            return ImmutableList.of();
        } else {
            List<ChunkPos> list = Lists.newArrayList();

            for (File file3 : afile) {
                RegionPos region = RegionFileNames.parseRegionFileName(file3.getName()).orElse(null);
                if (region != null && isRegionInBounds(region)) {
                    Int128 i = region.x().shiftLeftSaturated(5);
                    Int128 j = region.z().shiftLeftSaturated(5);

                    try {
                        RegionFile regionfile = new RegionFile(file3.toPath(), file2.toPath(), true);

                        try {
                            for (int k = 0; k < 32; ++k) {
                                for (int l = 0; l < 32; ++l) {
                                    ChunkPos chunkpos = new ChunkPos(i.add(Int128.fromLong(k)), j.add(Int128.fromLong(l)));
                                    if (regionfile.doesChunkExist(chunkpos)) {
                                        list.add(chunkpos);
                                    }
                                }
                            }
                        } catch (Throwable throwable1) {
                            try {
                                regionfile.close();
                            } catch (Throwable throwable) {
                                throwable1.addSuppressed(throwable);
                            }

                            throw throwable1;
                        }

                        regionfile.close();
                    } catch (Throwable throwable2) {
                    }
                }
            }

            return list;
        }
    }

    private static boolean isRegionInBounds(RegionPos region) {
        Int128 min = WorldBounds.MIN_CHUNK.shiftRight(5);
        Int128 max = WorldBounds.MAX_CHUNK.shiftRight(5);
        return region.x().compareTo(min) >= 0 && region.x().compareTo(max) <= 0
                && region.z().compareTo(min) >= 0 && region.z().compareTo(max) <= 0;
    }

    public boolean isFinished() {
        return this.finished;
    }

    public ImmutableSet<ResourceKey<Level>> levels() {
        return this.worldGenSettings.levels();
    }

    public float dimensionProgress(ResourceKey<Level> p_18828_) {
        return this.progressMap.getFloat(p_18828_);
    }

    public float getProgress() {
        return this.progress;
    }

    public int getTotalChunks() {
        return this.totalChunks;
    }

    public int getConverted() {
        return this.converted;
    }

    public int getSkipped() {
        return this.skipped;
    }

    public Component getStatus() {
        return this.status;
    }
}
