package net.minecraft.server.commands;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.datafixers.util.Unit;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldBounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class ResetChunksCommand {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register(CommandDispatcher<CommandSourceStack> pDispatcher) {
        pDispatcher.register(Commands.literal("resetchunks").requires((p_183683_) -> {
                                                              return p_183683_.hasPermission(2);
                                                          })
                        .executes((p_183693_) -> {
                            return resetChunks(p_183693_.getSource(), 0, true);
                        })
                        .then(Commands.argument("range", IntegerArgumentType.integer(0, 5)).executes((p_183689_) -> {
                                                                                               return resetChunks(p_183689_.getSource(), IntegerArgumentType.getInteger(p_183689_, "range"), true);
                                                                                           })
                                        .then(Commands.argument("skipOldChunks", BoolArgumentType.bool()).executes((p_183669_) -> {
                                            return resetChunks(p_183669_.getSource(), IntegerArgumentType.getInteger(p_183669_, "range"), BoolArgumentType.getBool(p_183669_, "skipOldChunks"));
                                        }))));
    }

    private static int resetChunks(CommandSourceStack pSource, int pRange, boolean pSkipOldChunks) {
        ServerLevel serverlevel = pSource.getLevel();
        ServerChunkCache serverchunkcache = serverlevel.getChunkSource();
        serverchunkcache.chunkMap.debugReloadGenerator();
        net.minecraft.world.phys.SectorVec3 exact = pSource.getExactPosition();
        ChunkPos chunkpos = new ChunkPos(net.minecraft.core.SectionPos.blockToSectionCoord(exact.blockX()), net.minecraft.core.SectionPos.blockToSectionCoord(exact.blockZ()));
        long i = WorldBounds.addChunkOffset(chunkpos.z, -(long) pRange);
        long j = WorldBounds.addChunkOffset(chunkpos.z, pRange);
        long k = WorldBounds.addChunkOffset(chunkpos.x, -(long) pRange);
        long l = WorldBounds.addChunkOffset(chunkpos.x, pRange);

        boolean moreZ = i <= j;
        for (long i1 = i; moreZ; moreZ = i1 < j, i1 = WorldBounds.addChunkOffset(i1, 1L)) {
            boolean moreX = k <= l;
            for (long j1 = k; moreX; moreX = j1 < l, j1 = WorldBounds.addChunkOffset(j1, 1L)) {
                ChunkPos chunkpos1 = new ChunkPos(j1, i1);
                LevelChunk levelchunk = serverchunkcache.getChunk(j1, i1, false);
                if (levelchunk != null && (!pSkipOldChunks || !levelchunk.isOldNoiseGeneration())) {
                    for (BlockPos blockpos : BlockPos.betweenClosed(chunkpos1.getMinBlockX(), serverlevel.getMinBuildHeight(), chunkpos1.getMinBlockZ(), chunkpos1.getMaxBlockX(), serverlevel.getMaxBuildHeight() - 1, chunkpos1.getMaxBlockZ())) {
                        serverlevel.setBlock(blockpos, Blocks.AIR.defaultBlockState(), 16);
                    }
                }
            }
        }

        ProcessorMailbox<Runnable> processormailbox = ProcessorMailbox.create(Util.backgroundExecutor(), "worldgen-resetchunks");
        long j3 = System.currentTimeMillis();
        int k3 = (pRange * 2 + 1) * (pRange * 2 + 1);

        for (ChunkStatus chunkstatus : ImmutableList.of(ChunkStatus.BIOMES, ChunkStatus.NOISE, ChunkStatus.SURFACE, ChunkStatus.CARVERS, ChunkStatus.LIQUID_CARVERS, ChunkStatus.FEATURES)) {
            long k1 = System.currentTimeMillis();
            CompletableFuture<Unit> completablefuture = CompletableFuture.supplyAsync(() -> {
                return Unit.INSTANCE;
            }, processormailbox::tell);

            boolean moreStageZ = i <= j;
            for (long i2 = i; moreStageZ; moreStageZ = i2 < j, i2 = WorldBounds.addChunkOffset(i2, 1L)) {
                boolean moreStageX = k <= l;
                for (long j2 = k; moreStageX; moreStageX = j2 < l, j2 = WorldBounds.addChunkOffset(j2, 1L)) {
                    ChunkPos chunkpos2 = new ChunkPos(j2, i2);
                    LevelChunk levelchunk1 = serverchunkcache.getChunk(j2, i2, false);
                    if (levelchunk1 != null && (!pSkipOldChunks || !levelchunk1.isOldNoiseGeneration())) {
                        List<ChunkAccess> list = Lists.newArrayList();
                        int k2 = Math.max(1, chunkstatus.getRange());

                        // Generation requires a square, center-indexed neighborhood even at a world edge.
                        for (int zOffset = -k2; zOffset <= k2; ++zOffset) {
                            long l2 = WorldBounds.addChunkOffset(chunkpos2.z, zOffset);
                            for (int xOffset = -k2; xOffset <= k2; ++xOffset) {
                                long i3 = WorldBounds.addChunkOffset(chunkpos2.x, xOffset);
                                ChunkAccess chunkaccess = serverchunkcache.getChunk(i3, l2, chunkstatus.getParent(), true);
                                ChunkAccess chunkaccess1;
                                if (chunkaccess instanceof ImposterProtoChunk) {
                                    chunkaccess1 = new ImposterProtoChunk(((ImposterProtoChunk) chunkaccess).getWrapped(), true);
                                } else if (chunkaccess instanceof LevelChunk) {
                                    chunkaccess1 = new ImposterProtoChunk((LevelChunk) chunkaccess, true);
                                } else {
                                    chunkaccess1 = chunkaccess;
                                }

                                list.add(chunkaccess1);
                            }
                        }

                        completablefuture = completablefuture.thenComposeAsync((p_183678_) -> {
                            return chunkstatus.generate(processormailbox::tell, serverlevel, serverchunkcache.getGenerator(), serverlevel.getStructureManager(), serverchunkcache.getLightEngine(), (p_183691_) -> {
                                                  throw new UnsupportedOperationException("Not creating full chunks here");
                                              }, list, true).thenApply((p_183681_) -> {
                                if (chunkstatus == ChunkStatus.NOISE) {
                                    p_183681_.left().ifPresent((p_183671_) -> {
                                        Heightmap.primeHeightmaps(p_183671_, ChunkStatus.POST_FEATURES);
                                    });
                                }

                                return Unit.INSTANCE;
                            });
                        }, processormailbox::tell);
                    }
                }
            }

            pSource.getServer().managedBlock(completablefuture::isDone);
            LOGGER.debug(chunkstatus.getName() + " took " + (System.currentTimeMillis() - k1) + " ms");
        }

        long l3 = System.currentTimeMillis();

        moreZ = i <= j;
        for (long i4 = i; moreZ; moreZ = i4 < j, i4 = WorldBounds.addChunkOffset(i4, 1L)) {
            boolean moreX = k <= l;
            for (long l1 = k; moreX; moreX = l1 < l, l1 = WorldBounds.addChunkOffset(l1, 1L)) {
                ChunkPos chunkpos3 = new ChunkPos(l1, i4);
                LevelChunk levelchunk2 = serverchunkcache.getChunk(l1, i4, false);
                if (levelchunk2 != null && (!pSkipOldChunks || !levelchunk2.isOldNoiseGeneration())) {
                    for (BlockPos blockpos1 : BlockPos.betweenClosed(chunkpos3.getMinBlockX(), serverlevel.getMinBuildHeight(), chunkpos3.getMinBlockZ(), chunkpos3.getMaxBlockX(), serverlevel.getMaxBuildHeight() - 1, chunkpos3.getMaxBlockZ())) {
                        serverchunkcache.blockChanged(blockpos1);
                    }
                }
            }
        }

        LOGGER.debug("blockChanged took " + (System.currentTimeMillis() - l3) + " ms");
        long j4 = System.currentTimeMillis() - j3;
        pSource.sendSuccess(Component.literal(String.format(Locale.ROOT, "%d chunks have been reset. This took %d ms for %d chunks, or %02f ms per chunk", k3, j4, k3, (float) j4 / (float) k3)), true);
        return 1;
    }
}
