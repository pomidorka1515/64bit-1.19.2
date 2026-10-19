package net.minecraft.client.multiplayer;

import com.mojang.logging.LogUtils;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ClientChunkCache extends ChunkSource {
    static final Logger LOGGER = LogUtils.getLogger();
    private final LevelChunk emptyChunk;
    private final LevelLightEngine lightEngine;
    volatile ClientChunkCache.Storage storage;
    final ClientLevel level;

    public ClientChunkCache(ClientLevel pLevel, int pViewDistance) {
        this.level = pLevel;
        this.emptyChunk = new EmptyLevelChunk(pLevel, new ChunkPos(0, 0), pLevel.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY).getHolderOrThrow(Biomes.PLAINS));
        this.lightEngine = new LevelLightEngine(this, true, pLevel.dimensionType().hasSkyLight());
        this.storage = new ClientChunkCache.Storage(calculateStorageRange(pViewDistance));
    }

    public LevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    private static boolean isValidChunk(@Nullable LevelChunk pChunk, long pX, long pZ) {
        if (pChunk == null) {
            return false;
        } else {
            ChunkPos chunkpos = pChunk.getPos();
            return chunkpos.x == pX && chunkpos.z == pZ;
        }
    }

    public boolean isInViewRange(long chunkX, long chunkZ) {
        return this.storage.inRange(chunkX, chunkZ);
    }

    public void drop(long i2, long j) {
        if (this.storage.inRange(i2, j)) {
            int i = this.storage.getIndex(i2, j);
            LevelChunk levelchunk = this.storage.getChunk(i);
            if (isValidChunk(levelchunk, i2, j)) {
                this.storage.replace(i, levelchunk, (LevelChunk) null);
            }
        }
    }

    @Nullable
    public LevelChunk getChunk(long pChunkX, long pChunkZ, ChunkStatus pRequiredStatus, boolean pLoad) {
        if (this.storage.inRange(pChunkX, pChunkZ)) {
            LevelChunk levelchunk = this.storage.getChunk(this.storage.getIndex(pChunkX, pChunkZ));
            if (isValidChunk(levelchunk, pChunkX, pChunkZ)) {
                return levelchunk;
            }
        }

        return pLoad ? this.emptyChunk : null;
    }

    public BlockGetter getLevel() {
        return this.level;
    }

    @Nullable
    public LevelChunk replaceWithPacketData(long pX, long pZ, FriendlyByteBuf pBuffer, CompoundTag pTag, Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> pConsumer) {
        if (!this.storage.inRange(pX, pZ)) {
            LOGGER.warn("Ignoring chunk since it's not in the view range: {}, {}", pX, pZ);
            return null;
        } else {
            int i = this.storage.getIndex(pX, pZ);
            LevelChunk levelchunk = this.storage.chunks.get(i);
            ChunkPos chunkpos = new ChunkPos(pX, pZ);
            if (!isValidChunk(levelchunk, pX, pZ)) {
                levelchunk = new LevelChunk(this.level, chunkpos);
                levelchunk.replaceWithPacketData(pBuffer, pTag, pConsumer);
                this.storage.replace(i, levelchunk);
            } else {
                levelchunk.replaceWithPacketData(pBuffer, pTag, pConsumer);
            }

            this.level.onChunkLoaded(chunkpos);
            return levelchunk;
        }
    }

    public void tick(BooleanSupplier pHasTimeLeft, boolean pTickChunks) {
    }

    public void updateViewCenter(long pX, long pZ) {
        this.storage.viewCenterX = WorldBounds.clampChunk(pX);
        this.storage.viewCenterZ = WorldBounds.clampChunk(pZ);
    }

    public void updateViewRadius(int pViewDistance) {
        int i = this.storage.chunkRadius;
        int j = calculateStorageRange(pViewDistance);
        if (i != j) {
            ClientChunkCache.Storage clientchunkcache$storage = new ClientChunkCache.Storage(j);
            clientchunkcache$storage.viewCenterX = this.storage.viewCenterX;
            clientchunkcache$storage.viewCenterZ = this.storage.viewCenterZ;

            for (int k = 0; k < this.storage.chunks.length(); ++k) {
                LevelChunk levelchunk = this.storage.chunks.get(k);
                if (levelchunk != null) {
                    ChunkPos chunkpos = levelchunk.getPos();
                    if (clientchunkcache$storage.inRange(chunkpos.x, chunkpos.z)) {
                        clientchunkcache$storage.replace(clientchunkcache$storage.getIndex(chunkpos.x, chunkpos.z), levelchunk);
                    }
                }
            }

            this.storage = clientchunkcache$storage;
        }
    }

    private static int calculateStorageRange(int pViewDistance) {
        return Math.max(2, pViewDistance) + 3;
    }

    public String gatherStats() {
        return this.storage.chunks.length() + ", " + this.getLoadedChunksCount();
    }

    public int getLoadedChunksCount() {
        return this.storage.chunkCount;
    }

    public void onLightUpdate(LightLayer pType, SectionPos pPos) {
        Minecraft.getInstance().levelRenderer.setSectionDirty(pPos.x(), pPos.y(), pPos.z());
    }

    @OnlyIn(Dist.CLIENT)
    final class Storage {
        final AtomicReferenceArray<LevelChunk> chunks;
        final int chunkRadius;
        private final int viewRange;
        volatile long viewCenterX;
        volatile long viewCenterZ;
        int chunkCount;

        Storage(int p_104474_) {
            this.chunkRadius = p_104474_;
            this.viewRange = p_104474_ * 2 + 1;
            this.chunks = new AtomicReferenceArray<>(this.viewRange * this.viewRange);
        }

        int getIndex(long pX, long pZ) {
            long x = Math.floorMod(pX, (long) this.viewRange);
            long z = Math.floorMod(pZ, (long) this.viewRange);
            return (int) (z * (long) this.viewRange + x);
        }

        protected void replace(int pChunkIndex, @Nullable LevelChunk pChunk) {
            LevelChunk levelchunk = this.chunks.getAndSet(pChunkIndex, pChunk);
            if (levelchunk != null) {
                --this.chunkCount;
                ClientChunkCache.this.level.unload(levelchunk);
            }

            if (pChunk != null) {
                ++this.chunkCount;
            }
        }

        protected LevelChunk replace(int pChunkIndex, LevelChunk pChunk, @Nullable LevelChunk pReplaceWith) {
            if (this.chunks.compareAndSet(pChunkIndex, pChunk, pReplaceWith) && pReplaceWith == null) {
                --this.chunkCount;
            }

            ClientChunkCache.this.level.unload(pChunk);
            return pChunk;
        }

        boolean inRange(long pX, long pZ) {
            return WorldBounds.within(pX, this.viewCenterX, this.chunkRadius)
                    && WorldBounds.within(pZ, this.viewCenterZ, this.chunkRadius);
        }

        @Nullable
        protected LevelChunk getChunk(int pChunkIndex) {
            return this.chunks.get(pChunkIndex);
        }

        private void dumpChunks(String pFilePath) {
            try {
                FileOutputStream fileoutputstream = new FileOutputStream(pFilePath);

                try {
                    long radius = (long) ClientChunkCache.this.storage.chunkRadius;
                    long minZ = WorldBounds.addChunkOffset(this.viewCenterZ, -radius);
                    long maxZ = WorldBounds.addChunkOffset(this.viewCenterZ, radius);
                    long minX = WorldBounds.addChunkOffset(this.viewCenterX, -radius);
                    long maxX = WorldBounds.addChunkOffset(this.viewCenterX, radius);

                    for (long z = minZ;; z = WorldBounds.addChunkOffset(z, 1L)) {
                        for (long x = minX;; x = WorldBounds.addChunkOffset(x, 1L)) {
                            LevelChunk levelchunk = ClientChunkCache.this.storage.chunks.get(ClientChunkCache.this.storage.getIndex(x, z));
                            if (levelchunk != null) {
                                ChunkPos chunkpos = levelchunk.getPos();
                                fileoutputstream.write((chunkpos.x + "\t" + chunkpos.z + "\t" + levelchunk.isEmpty() + "\n").getBytes(StandardCharsets.UTF_8));
                            }
                            if (x == maxX)
                                break;
                        }
                        if (z == maxZ)
                            break;
                    }
                } catch (Throwable throwable1) {
                    try {
                        fileoutputstream.close();
                    } catch (Throwable throwable) {
                        throwable1.addSuppressed(throwable);
                    }

                    throw throwable1;
                }

                fileoutputstream.close();
            } catch (IOException ioexception) {
                ClientChunkCache.LOGGER.error("Failed to dump chunks to file {}", pFilePath, ioexception);
            }
        }
    }
}
