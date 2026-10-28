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
import net.minecraft.core.Int1088;
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
        return isValidChunk(pChunk, net.minecraft.core.Int1088.fromLong(pX), net.minecraft.core.Int1088.fromLong(pZ));
    }

    private static boolean isValidChunk(@Nullable LevelChunk pChunk, net.minecraft.core.Int1088 pX, net.minecraft.core.Int1088 pZ) {
        if (pChunk == null) {
            return false;
        } else {
            ChunkPos chunkpos = pChunk.getPos();
            return chunkpos.x().equals(pX) && chunkpos.z().equals(pZ);
        }
    }

    public boolean isInViewRange(long chunkX, long chunkZ) {
        return this.isInViewRange(net.minecraft.core.Int1088.fromLong(chunkX), net.minecraft.core.Int1088.fromLong(chunkZ));
    }

    public boolean isInViewRange(net.minecraft.core.Int1088 chunkX, net.minecraft.core.Int1088 chunkZ) {
        return this.storage.inRange(chunkX, chunkZ);
    }

    public void drop(long i2, long j) {
        this.drop(net.minecraft.core.Int1088.fromLong(i2), net.minecraft.core.Int1088.fromLong(j));
    }

    public void drop(net.minecraft.core.Int1088 i2, net.minecraft.core.Int1088 j) {
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
        return this.getChunk(net.minecraft.core.Int1088.fromLong(pChunkX), net.minecraft.core.Int1088.fromLong(pChunkZ), pRequiredStatus, pLoad);
    }

    @Nullable
    public LevelChunk getChunk(net.minecraft.core.Int1088 pChunkX, net.minecraft.core.Int1088 pChunkZ, ChunkStatus pRequiredStatus, boolean pLoad) {
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
        return this.replaceWithPacketData(net.minecraft.core.Int1088.fromLong(pX), net.minecraft.core.Int1088.fromLong(pZ), pBuffer, pTag, pConsumer);
    }

    @Nullable
    public LevelChunk replaceWithPacketData(net.minecraft.core.Int1088 pX, net.minecraft.core.Int1088 pZ, FriendlyByteBuf pBuffer, CompoundTag pTag, Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> pConsumer) {
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
        this.updateViewCenter(net.minecraft.core.Int1088.fromLong(pX), net.minecraft.core.Int1088.fromLong(pZ));
    }

    public void updateViewCenter(net.minecraft.core.Int1088 pX, net.minecraft.core.Int1088 pZ) {
        this.storage.updateIndexOrigin(pX, pZ);
        this.storage.viewCenterX = pX;
        this.storage.viewCenterZ = pZ;
    }

    public void updateViewRadius(int pViewDistance) {
        int i = this.storage.chunkRadius;
        int j = calculateStorageRange(pViewDistance);
        if (i != j) {
            ClientChunkCache.Storage clientchunkcache$storage = new ClientChunkCache.Storage(j);
            clientchunkcache$storage.viewCenterX = this.storage.viewCenterX;
            clientchunkcache$storage.viewCenterZ = this.storage.viewCenterZ;
            clientchunkcache$storage.updateIndexOrigin(clientchunkcache$storage.viewCenterX, clientchunkcache$storage.viewCenterZ);

            for (int k = 0; k < this.storage.chunks.length(); ++k) {
                LevelChunk levelchunk = this.storage.chunks.get(k);
                if (levelchunk != null) {
                    ChunkPos chunkpos = levelchunk.getPos();
                    if (clientchunkcache$storage.inRange(chunkpos)) {
                        clientchunkcache$storage.replace(clientchunkcache$storage.getIndex(chunkpos), levelchunk);
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

    boolean isCurrentChunk(Int1088 x, Int1088 z, @Nullable LevelChunk expected) {
        return expected != null && this.getChunk(x, z, ChunkStatus.FULL, false) == expected;
    }

    static boolean inTrackingRange(Int1088 x, Int1088 z, Int1088 centerX, Int1088 centerZ, int radius) {
        return radius >= 0 && WorldBounds.distance(x, centerX) <= radius
                && WorldBounds.distance(z, centerZ) <= radius;
    }

    static int storageModulo(Int1088 value, int range) {
        return Math.floorMod(value.remainder(range), range);
    }

    /** Retains absolute ring indices: changing the anchor never relocates stored chunks. */
    static int anchoredStorageModulo(Int1088 value, Int1088 origin, int originModulo, int range) {
        // A few hardware divisions beat validating a local difference at small widths.
        if (value.limbCount() <= 3) return storageModulo(value, range);
        long delta = net.minecraft.core.Int1088Math.signedDifferenceAsLong(value, origin);
        if (delta >= -net.minecraft.core.Int1088Math.MAX_SAFE_LOCAL_SPAN
                && delta <= net.minecraft.core.Int1088Math.MAX_SAFE_LOCAL_SPAN) {
            return (int) Math.floorMod(delta + originModulo, (long) range);
        }
        return storageModulo(value, range);
    }

    private record IndexOrigin(Int1088 x, Int1088 z, int xModulo, int zModulo) { }

    @OnlyIn(Dist.CLIENT)
    final class Storage {
        final AtomicReferenceArray<LevelChunk> chunks;
        final int chunkRadius;
        private final int viewRange;
        // Publish coordinates and their residues together for concurrent chunk readers.
        private volatile IndexOrigin indexOrigin = new IndexOrigin(Int1088.ZERO, Int1088.ZERO, 0, 0);
        volatile net.minecraft.core.Int1088 viewCenterX = net.minecraft.core.Int1088.ZERO;
        volatile net.minecraft.core.Int1088 viewCenterZ = net.minecraft.core.Int1088.ZERO;
        int chunkCount;

        Storage(int p_104474_) {
            this.chunkRadius = p_104474_;
            this.viewRange = p_104474_ * 2 + 1;
            this.chunks = new AtomicReferenceArray<>(this.viewRange * this.viewRange);
        }

        int getIndex(long pX, long pZ) {
            return this.getIndex(net.minecraft.core.Int1088.fromLong(pX), net.minecraft.core.Int1088.fromLong(pZ));
        }

        int getIndex(net.minecraft.core.Int1088 pX, net.minecraft.core.Int1088 pZ) {
            IndexOrigin origin = this.indexOrigin;
            int x = anchoredStorageModulo(pX, origin.x(), origin.xModulo(), this.viewRange);
            int z = anchoredStorageModulo(pZ, origin.z(), origin.zModulo(), this.viewRange);
            return z * this.viewRange + x;
        }

        int getIndex(ChunkPos pos) {
            return this.getIndex(pos.x(), pos.z());
        }

        private void updateIndexOrigin(Int1088 x, Int1088 z) {
            IndexOrigin previous = this.indexOrigin;
            if (previous.x().equals(x) && previous.z().equals(z)) return;
            this.indexOrigin = new IndexOrigin(x, z, storageModulo(x, this.viewRange), storageModulo(z, this.viewRange));
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
            return this.inRange(net.minecraft.core.Int1088.fromLong(pX), net.minecraft.core.Int1088.fromLong(pZ));
        }

        boolean inRange(net.minecraft.core.Int1088 pX, net.minecraft.core.Int1088 pZ) {
            return inTrackingRange(pX, pZ, this.viewCenterX, this.viewCenterZ, this.chunkRadius);
        }

        boolean inRange(ChunkPos pos) {
            return inTrackingRange(pos.x(), pos.z(), this.viewCenterX, this.viewCenterZ, this.chunkRadius);
        }

        @Nullable
        protected LevelChunk getChunk(int pChunkIndex) {
            return this.chunks.get(pChunkIndex);
        }

        private void dumpChunks(String pFilePath) {
            try {
                FileOutputStream fileoutputstream = new FileOutputStream(pFilePath);

                try {
                    ClientChunkCache.Storage snapshot = ClientChunkCache.this.storage;
                    for (int index = 0; index < snapshot.chunks.length(); ++index) {
                        LevelChunk chunk = snapshot.chunks.get(index);
                        if (chunk != null && snapshot.inRange(chunk.getPos())) {
                            ChunkPos pos = chunk.getPos();
                            fileoutputstream.write((pos.x() + "\t" + pos.z() + "\t" + chunk.isEmpty() + "\n").getBytes(StandardCharsets.UTF_8));
                        }
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
