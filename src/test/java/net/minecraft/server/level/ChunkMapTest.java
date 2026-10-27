package net.minecraft.server.level;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.SortedArraySet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ChunkPosMap;
import net.minecraft.world.level.ForcedChunksSavedData;
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

    private static List<ChunkPos> ticketPositions() {
        Set<ChunkPos> positions = new java.util.LinkedHashSet<>();
        positions.add(ChunkPos.ZERO);
        for (int bit : new int[] {53, 63, 127, 1024}) {
            BigInteger value = BigInteger.ONE.shiftLeft(bit);
            positions.add(chunk(value, value.negate()));
            positions.add(chunk(value.negate(), value));
        }
        // Adjacent chunks straddle every signed limb seam, on each axis separately.
        for (int limb = 1; limb < Int1088.LIMBS; ++limb) {
            BigInteger seam = BigInteger.ONE.shiftLeft(limb * 64);
            for (BigInteger signed : List.of(seam, seam.negate())) {
                for (int offset : new int[] {-1, 0, 1}) {
                    BigInteger axis = signed.add(BigInteger.valueOf(offset));
                    positions.add(chunk(axis, BigInteger.ZERO));
                    positions.add(chunk(BigInteger.ZERO, axis));
                }
            }
        }
        positions.add(new ChunkPos(WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK));
        positions.add(new ChunkPos(WorldBounds.MAX_CHUNK, WorldBounds.MIN_CHUNK));
        return new ArrayList<>(positions);
    }

    private static ChunkPos chunk(BigInteger x, BigInteger z) {
        return new ChunkPos(Int1088.parseDecimal(x.toString()), Int1088.parseDecimal(z.toString()));
    }

    private static ChunkPos decoded(ChunkPos pos) {
        return new ChunkPos(Int1088.parseDecimal(pos.x().toDecimalString()), Int1088.parseDecimal(pos.z().toDecimalString()));
    }

    @Test
    void ticketOrderingAndDeduplicationReadBothCompleteAxes() {
        List<ChunkPos> positions = ticketPositions();
        for (TicketType<ChunkPos> type : List.of(TicketType.PLAYER, TicketType.FORCED, TicketType.LIGHT, TicketType.UNKNOWN)) {
            SortedArraySet<Ticket<?>> tickets = SortedArraySet.create(4);
            for (ChunkPos pos : positions) {
                Ticket<ChunkPos> ticket = new Ticket<>(type, 31, pos);
                assertTrue(tickets.add(ticket));
                Ticket<ChunkPos> copy = new Ticket<>(type, 31, decoded(pos));
                assertEquals(ticket, copy);
                assertEquals(ticket.hashCode(), copy.hashCode());
                assertEquals(0, ticket.compareTo(copy));
                assertFalse(tickets.add(copy));
            }
            assertEquals(positions.size(), tickets.size());
            List<ChunkPos> ordered = new ArrayList<>(positions);
            ordered.sort((a, b) -> {
                int x = new BigInteger(a.x().toDecimalString()).compareTo(new BigInteger(b.x().toDecimalString()));
                return x != 0 ? x : new BigInteger(a.z().toDecimalString()).compareTo(new BigInteger(b.z().toDecimalString()));
            });
            List<Ticket<?>> expected = ordered.stream().<Ticket<?>>map(pos -> new Ticket<>(type, 31, pos)).toList();
            assertEquals(expected, tickets.stream().toList());
            for (ChunkPos pos : positions) assertTrue(tickets.remove(new Ticket<>(type, 31, decoded(pos))));
            assertTrue(tickets.isEmpty());
        }
        SortedArraySet<Ticket<?>> portals = SortedArraySet.create(4);
        for (ChunkPos pos : positions) {
            BlockPos block = pos.getMiddleBlockPosition(64);
            assertTrue(portals.add(new Ticket<>(TicketType.PORTAL, 31, block)));
            assertFalse(portals.add(new Ticket<>(TicketType.PORTAL, 31, new BlockPos(block.getX(), 64, block.getZ()))));
        }
        assertEquals(positions.size(), portals.size());
    }

    @Test
    void forcedReloadPropagatesLocalTicketLevelsAcrossSeamsAndClipsAtEdges() {
        TestDistanceManager manager = new TestDistanceManager();
        try {
            for (ChunkPos center : ticketPositions()) {
                ForcedChunksSavedData saved = new ForcedChunksSavedData();
                saved.getChunks().add(center);
                CompoundTag tag = saved.save(new CompoundTag());
                ForcedChunksSavedData loaded = ForcedChunksSavedData.load(tag);
                assertEquals(Set.of(center), loaded.getChunks());
                ChunkPos restored = loaded.getChunks().iterator().next();
                manager.updateChunkForced(restored, true);
                manager.tickingTracker().runAllUpdates();
                assertLocalTicketLevels(manager, center, 31);
                assertEquals(1, manager.tickets.size());
                assertEquals(31, manager.tickets.get(decoded(center)).first().getTicketLevel());
                manager.updateChunkForced(decoded(center), false);
                manager.tickingTracker().runAllUpdates();
                assertFalse(manager.hasTickets());
                assertFalse(manager.inBlockTickingRange(center));
                assertTrue(manager.tickingTracker().chunks.values().stream().allMatch(level -> level >= 33));
            }
        } finally {
            manager.ticketThrottler.close();
        }
    }

    private static void assertLocalTicketLevels(TestDistanceManager manager, ChunkPos center, int sourceLevel) {
        BigInteger min = new BigInteger(WorldBounds.MIN_CHUNK.toDecimalString());
        BigInteger max = new BigInteger(WorldBounds.MAX_CHUNK.toDecimalString());
        BigInteger x = new BigInteger(center.x().toDecimalString());
        BigInteger z = new BigInteger(center.z().toDecimalString());
        Set<ChunkPos> expectedTicking = new HashSet<>();
        for (int dx = -3; dx <= 3; ++dx) {
            for (int dz = -3; dz <= 3; ++dz) {
                BigInteger nx = x.add(BigInteger.valueOf(dx));
                BigInteger nz = z.add(BigInteger.valueOf(dz));
                if (nx.compareTo(min) < 0 || nx.compareTo(max) > 0 || nz.compareTo(min) < 0 || nz.compareTo(max) > 0) continue;
                ChunkPos neighbor = chunk(nx, nz);
                int expected = Math.min(33, sourceLevel + Math.max(Math.abs(dx), Math.abs(dz)));
                assertEquals(expected, manager.tickingTracker().getLevel(neighbor), () -> "level near " + center);
                assertEquals(expected < 32, manager.inEntityTickingRange(neighbor));
                assertEquals(expected < 33, manager.inBlockTickingRange(neighbor));
                if (expected < 33) expectedTicking.add(neighbor);
            }
        }
        Set<ChunkPos> actualTicking = new HashSet<>();
        manager.tickingTracker().chunks.forEach((pos, level) -> {
            if (level < 33) actualTicking.add(pos);
        });
        assertEquals(expectedTicking, actualTicking);
    }

    @Test
    void regionTicketRemovalAndSimulationDistanceReplacementRetainExactSources() {
        TestDistanceManager manager = new TestDistanceManager();
        try {
            for (ChunkPos center : ticketPositions()) {
                manager.addRegionTicket(TicketType.PORTAL, center, 2, center.getMiddleBlockPosition(64));
                manager.tickingTracker().runAllUpdates();
                assertLocalTicketLevels(manager, center, 31);
                manager.removeRegionTicket(TicketType.PORTAL, decoded(center), 2, center.getMiddleBlockPosition(64));
                manager.tickingTracker().runAllUpdates();
                assertFalse(manager.inBlockTickingRange(center));
                manager.tickingTracker().addTicket(TicketType.PLAYER, center, 32, decoded(center));
                manager.tickingTracker().runAllUpdates();
                assertLocalTicketLevels(manager, center, 32);
                // Simulation radius stays an int; only the source address is wide.
                manager.updateSimulationDistance(0);
                manager.tickingTracker().runAllUpdates();
                assertLocalTicketLevels(manager, center, 31);
                manager.tickingTracker().removeTicket(TicketType.PLAYER, decoded(center), 31, decoded(center));
                manager.tickingTracker().runAllUpdates();
                manager.updateSimulationDistance(10);
                assertFalse(manager.inBlockTickingRange(center));
            }
        } finally {
            manager.ticketThrottler.close();
        }
    }

    @Test
    void ticketRefreshExpiryAndClosingRemoveOnlyTheExactKeys() {
        TestDistanceManager manager = new TestDistanceManager();
        List<ChunkPos> positions = ticketPositions();
        try {
            for (ChunkPos pos : positions) {
                manager.addTicket(TicketType.UNKNOWN, pos, 33, pos);
                manager.addTicket(TicketType.FORCED, pos, 31, pos);
            }
            manager.purgeStaleTickets(); // UNKNOWN lives through age 1.
            for (int i = 0; i < positions.size(); ++i) {
                ChunkPos pos = positions.get(i);
                assertEquals(2, manager.tickets.get(decoded(pos)).size());
                if ((i & 1) == 0) manager.addTicket(TicketType.UNKNOWN, decoded(pos), 33, decoded(pos));
            }
            manager.purgeStaleTickets();
            for (int i = 0; i < positions.size(); ++i) {
                assertEquals((i & 1) == 0 ? 2 : 1, manager.tickets.get(decoded(positions.get(i))).size());
            }
            manager.removeTicketsOnClosing(); // Retain only UNKNOWN, LIGHT and POST_TELEPORT.
            for (int i = 0; i < positions.size(); ++i) {
                var tickets = manager.tickets.get(decoded(positions.get(i)));
                if ((i & 1) == 0) {
                    assertEquals(1, tickets.size());
                    assertSame(TicketType.UNKNOWN, tickets.first().getType());
                } else assertNull(tickets);
            }
            manager.purgeStaleTickets();
            assertFalse(manager.hasTickets());
        } finally {
            manager.ticketThrottler.close();
        }
    }

    @Test
    void taskQueueResortsGroupsAndClearsOnlyFullWidthAddresses() {
        List<ChunkPos> positions = ticketPositions();
        ChunkTaskPriorityQueue<Integer> queue = new ChunkTaskPriorityQueue<>("wide", Integer.MAX_VALUE);
        for (int i = 0; i < positions.size(); ++i) {
            queue.submit(Optional.of(i), positions.get(i), 10);
            queue.submit(Optional.of(i + positions.size()), decoded(positions.get(i)), 10);
        }
        List<Integer> expected = new ArrayList<>();
        for (int i = 0; i < positions.size(); ++i) {
            if (i % 3 == 0) queue.release(decoded(positions.get(i)), true);
            else if (i % 3 == 1) queue.resortChunkTasks(10, decoded(positions.get(i)), 5);
        }
        for (int remainder : new int[] {1, 2}) {
            for (int i = 0; i < positions.size(); ++i) {
                if (i % 3 == remainder) {
                    expected.add(i);
                    expected.add(i + positions.size());
                }
            }
        }
        List<Integer> ran = new ArrayList<>();
        while (queue.hasWork()) queue.pop().forEach(task -> ran.add(task.left().orElseThrow()));
        assertEquals(expected, ran);
        assertNull(queue.pop());
    }

    @Test
    void taskAcquisitionAndPartialReleaseDoNotAliasUpperLimbs() {
        List<ChunkPos> positions = ticketPositions();
        ChunkTaskPriorityQueue<Integer> queue = new ChunkTaskPriorityQueue<>("wide-throttled", positions.size());
        for (int i = 0; i < positions.size(); ++i) {
            queue.submit(Optional.of(i), positions.get(i), 0);
            queue.submit(Optional.empty(), decoded(positions.get(i)), 0);
        }
        for (int i = 0; i < positions.size(); ++i) {
            List<Integer> ran = new ArrayList<>();
            queue.pop().forEach(task -> task.ifLeft(ran::add).ifRight(Runnable::run));
            assertEquals(List.of(i), ran);
        }
        assertEquals(new HashSet<>(positions), queue.getAcquired());
        for (int i = 0; i < positions.size(); ++i) {
            ChunkPos pos = decoded(positions.get(i));
            queue.submit(Optional.of(i), pos, 0);
            queue.submit(Optional.empty(), pos, 0);
            assertNull(queue.pop()); // All permits still held.
            queue.release(pos, false); // Preserve work, remove its acquire marker.
            assertFalse(queue.getAcquired().contains(pos));
            List<Integer> ran = new ArrayList<>();
            queue.pop().forEach(task -> task.ifLeft(ran::add).ifRight(marker -> fail("Released marker ran")));
            assertEquals(List.of(i), ran);
            queue.submit(Optional.empty(), pos, 0);
            queue.pop().forEach(task -> task.ifRight(Runnable::run));
            assertTrue(queue.getAcquired().contains(pos));
        }
        for (ChunkPos pos : positions) queue.release(decoded(pos), true);
        assertTrue(queue.getAcquired().isEmpty());
        assertFalse(queue.hasWork());
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
