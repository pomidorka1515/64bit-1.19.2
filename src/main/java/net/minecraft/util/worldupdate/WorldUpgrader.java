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
import java.nio.file.Files;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadFactory;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.chunk.storage.RegionPaths;
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
                builder1.put(resourcekey1, new ChunkStorage(path, true));
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
                                Int1088 x = NbtUtils.readInt1088(compoundtag, "xPos");
                                Int1088 z = NbtUtils.readInt1088(compoundtag, "zPos");
                                if (x.compareTo(ChunkPos.MIN_CHUNK_COORDINATE) < 0 || x.compareTo(ChunkPos.MAX_CHUNK_COORDINATE) > 0
                                        || z.compareTo(ChunkPos.MIN_CHUNK_COORDINATE) < 0 || z.compareTo(ChunkPos.MAX_CHUNK_COORDINATE) > 0) {
                                    throw new IllegalArgumentException("Chunk position outside domain at " + chunkpos);
                                }
                                ChunkPos chunkpos1 = new ChunkPos(x, z);
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
        return getAllChunkPos(this.levelStorage.getDimensionPath(p_18831_).toFile());
    }

    static List<ChunkPos> getAllChunkPos(File rootFile) {
        Path root = rootFile.toPath();
        Path tree = root.resolve("region");
        List<ChunkPos> list = Lists.newArrayList();
        if (!Files.isDirectory(tree)) return list;
        try (var paths = Files.walk(tree)) {
            paths.filter(Files::isRegularFile).filter(path -> path.getFileName().toString().equals("r.mca")).forEach(path -> {
                RegionPos region = RegionPaths.parseRegionFile(root, path).orElse(null);
                if (region == null) {
                    LOGGER.warn("Ignoring invalid region path {}", path);
                    return;
                }
                Int1088 x = region.x().shiftLeftSaturated(5);
                Int1088 z = region.z().shiftLeftSaturated(5);
                try (RegionFile file = new RegionFile(path, root, true)) {
                    for (int dx = 0; dx < 32; ++dx) {
                        for (int dz = 0; dz < 32; ++dz) {
                            ChunkPos pos = new ChunkPos(x.add(Int1088.fromLong(dx)), z.add(Int1088.fromLong(dz)));
                            if (file.doesChunkExist(pos)) list.add(pos);
                        }
                    }
                } catch (IOException e) {
                    LOGGER.warn("Failed to scan region {}", path, e);
                }
            });
        } catch (IOException e) {
            LOGGER.warn("Failed to scan region tree {}", tree, e);
        }
        return list;
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
