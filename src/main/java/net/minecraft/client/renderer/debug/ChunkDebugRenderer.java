package net.minecraft.client.renderer.debug;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Int1088;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ChunkDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    final Minecraft minecraft;
    private double lastUpdateTime = Double.MIN_VALUE;
    private final int radius = 12;
    @Nullable
    private ChunkDebugRenderer.ChunkData data;

    public ChunkDebugRenderer(Minecraft pMinecraft) {
        this.minecraft = pMinecraft;
    }

    public void render(PoseStack pPoseStack, MultiBufferSource pBufferSource, double pCamX, double pCamY, double pCamZ) {
        double d0 = (double) Util.getNanos();
        if (d0 - this.lastUpdateTime > 3.0E9D) {
            this.lastUpdateTime = d0;
            IntegratedServer integratedserver = this.minecraft.getSingleplayerServer();
            if (integratedserver != null) {
                this.data = new ChunkDebugRenderer.ChunkData(integratedserver, new ChunkPos(this.minecraft.gameRenderer.getMainCamera().getBlockPosition()));
            } else {
                this.data = null;
            }
        }

        if (this.data != null) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.lineWidth(2.0F);
            RenderSystem.disableTexture();
            RenderSystem.depthMask(false);
            Map<ChunkPos, String> map = this.data.serverData.getNow((Map<ChunkPos, String>) null);
            double d1 = this.minecraft.gameRenderer.getMainCamera().getPosition().y * 0.85D;

            for (Map.Entry<ChunkPos, String> entry : this.data.clientData.entrySet()) {
                ChunkPos chunkpos = entry.getKey();
                String s = entry.getValue();
                if (map != null) {
                    s = s + (String) map.get(chunkpos);
                }

                String[] astring = s.split("\n");
                int i = 0;

                for (String s1 : astring) {
                    DebugRenderer.renderFloatingText(s1, new net.minecraft.core.BlockPos(chunkpos.getBlockX(8), 0, chunkpos.getBlockZ(8)), 0, d1 + i, 0, -1, 0.15F, true, 0, false);
                    i -= 2;
                }
            }

            RenderSystem.depthMask(true);
            RenderSystem.enableTexture();
            RenderSystem.disableBlend();
        }
    }

    @OnlyIn(Dist.CLIENT)
    final class ChunkData {
        final Map<ChunkPos, String> clientData;
        final CompletableFuture<Map<ChunkPos, String>> serverData;

        ChunkData(IntegratedServer p_113382_, ChunkPos center) {
            ClientLevel clientlevel = ChunkDebugRenderer.this.minecraft.level;
            ResourceKey<Level> resourcekey = clientlevel.dimension();
            Int1088 i = center.x();
            Int1088 j = center.z();
            ImmutableMap.Builder<ChunkPos, String> builder = ImmutableMap.builder();
            ClientChunkCache clientchunkcache = clientlevel.getChunkSource();

            Int1088 minX = WorldBounds.addChunkOffset(i, -12L);
            Int1088 maxX = WorldBounds.addChunkOffset(i, 12L);
            Int1088 minZ = WorldBounds.addChunkOffset(j, -12L);
            Int1088 maxZ = WorldBounds.addChunkOffset(j, 12L);
            for (Int1088 k = minX;; k = WorldBounds.addChunkOffset(k, 1L)) {
                for (Int1088 l = minZ;; l = WorldBounds.addChunkOffset(l, 1L)) {
                    ChunkPos chunkpos = new ChunkPos(k, l);
                    String s = "";
                    LevelChunk levelchunk = clientchunkcache.getChunk(k, l, net.minecraft.world.level.chunk.ChunkStatus.FULL, false);
                    s = s + "Client: ";
                    if (levelchunk == null) {
                        s = s + "0n/a\n";
                    } else {
                        s = s + (levelchunk.isEmpty() ? " E" : "");
                        s = s + "\n";
                    }

                    builder.put(chunkpos, s);
                    if (l.equals(maxZ))
                        break;
                }
                if (k.equals(maxX))
                    break;
            }

            this.clientData = builder.build();
            this.serverData = p_113382_.submit(() -> {
                ServerLevel serverlevel = p_113382_.getLevel(resourcekey);
                if (serverlevel == null) {
                    return ImmutableMap.of();
                } else {
                    ImmutableMap.Builder<ChunkPos, String> builder1 = ImmutableMap.builder();
                    ServerChunkCache serverchunkcache = serverlevel.getChunkSource();

                    for (Int1088 i1 = minX;; i1 = WorldBounds.addChunkOffset(i1, 1L)) {
                        for (Int1088 j1 = minZ;; j1 = WorldBounds.addChunkOffset(j1, 1L)) {
                            ChunkPos chunkpos1 = new ChunkPos(i1, j1);
                            builder1.put(chunkpos1, "Server: " + serverchunkcache.getChunkDebugData(chunkpos1));
                            if (j1.equals(maxZ))
                                break;
                        }
                        if (i1.equals(maxX))
                            break;
                    }

                    return builder1.build();
                }
            });
        }
    }
}
