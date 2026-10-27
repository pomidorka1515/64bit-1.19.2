package net.minecraft.server.level;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ChunkPosMap;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkMapTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void ticketRemovalAndHolderLookupRetainEveryAddressLimb() {
        TestDistanceManager manager = new TestDistanceManager();
        try {
            List<ChunkPos> positions = new ArrayList<>();
            positions.add(ChunkPos.ZERO);
            for (int limb = 1; limb < Int1088.LIMBS; ++limb) {
                long[] words = new long[Int1088.LIMBS];
                words[limb] = 1L;
                Int1088 coordinate = Int1088.fromFixedLimbs(words);
                for (Int1088 signed : List.of(coordinate, coordinate.negate())) {
                    positions.add(new ChunkPos(signed, Int1088.ZERO));
                    positions.add(new ChunkPos(Int1088.ZERO, signed));
                }
            }
            positions.add(new ChunkPos(WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK));
            positions.add(new ChunkPos(WorldBounds.MAX_CHUNK, WorldBounds.MIN_CHUNK));
            for (ChunkPos pos : positions) {
                manager.addTicket(TicketType.FORCED, pos, 31, pos);
                manager.holders.put(pos, new ChunkHolder(pos, 31,
                        LevelHeightAccessor.create(-64, 384), null, null, null));
            }
            assertEquals(positions.size(), manager.tickets.size());
            for (ChunkPos pos : positions) {
                ChunkPos copy = new ChunkPos(pos.x(), pos.z());
                assertEquals(1, manager.tickets.get(copy.x(), copy.z()).size());
                assertEquals(pos, manager.getChunk(copy).getPos());
                Int1088 neighborX = pos.x().equals(WorldBounds.MAX_CHUNK)
                        ? pos.x().subtract(Int1088.ONE) : pos.x().add(Int1088.ONE);
                assertEquals("no_ticket", manager.getTicketDebugString(new ChunkPos(neighborX, pos.z())));
            }
            for (int i = 0; i < positions.size(); i += 2) {
                ChunkPos pos = positions.get(i);
                manager.removeTicket(TicketType.FORCED, new ChunkPos(pos.x(), pos.z()), 31, pos);
                manager.holders.remove(pos);
            }
            for (int i = 0; i < positions.size(); ++i) {
                ChunkPos pos = positions.get(i);
                assertEquals((i & 1) == 0, manager.tickets.get(pos.x(), pos.z()) == null);
                assertEquals((i & 1) == 0, manager.getChunk(pos) == null);
            }
        } finally {
            manager.ticketThrottler.close();
        }
    }

    @Test
    void chunkTrackingRejectsAliasesInEveryUpperLimb() {
        for (int limb = 1; limb < Int1088.LIMBS; ++limb) {
            long[] words = new long[Int1088.LIMBS];
            words[limb] = 1L;
            Int1088 coordinate = Int1088.fromFixedLimbs(words);
            for (Int1088 center : List.of(coordinate, coordinate.negate())) {
                assertTrue(ChunkMap.isChunkInRange(center, center, center, center, 8));
                assertTrue(ChunkMap.isChunkInRange(center.add(Int1088.ONE), center, center, center, 8));
                assertFalse(ChunkMap.isChunkInRange(Int1088.ZERO, center, center, center, 8));
                assertFalse(ChunkMap.isChunkInRange(center, Int1088.ZERO, center, center, 8));
            }
        }
    }

    private static final class TestDistanceManager extends DistanceManager {
        final ChunkPosMap<ChunkHolder> holders = new ChunkPosMap<>();

        TestDistanceManager() {
            super(Runnable::run, Runnable::run);
        }

        @Override
        protected boolean isChunkToRemove(ChunkPos pos) {
            return false;
        }

        @Override
        protected ChunkHolder getChunk(ChunkPos pos) {
            return this.holders.get(pos.x(), pos.z());
        }

        @Override
        protected ChunkHolder updateChunkScheduling(ChunkPos pos, int newLevel, ChunkHolder holder, int oldLevel) {
            return holder;
        }
    }
}
