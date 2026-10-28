package net.minecraft.client.multiplayer;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

class ClientChunkCoordinatesTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void ringIndicesMatchSignedFullWidthModulo() {
        for (Int1088 value : new Int1088[] {Int1088.ZERO, Int1088.MIN, Int1088.MAX,
                Int1088.of(-1, 7), Int1088.of(0, -7), Int1088.ONE.shiftLeftSaturated(1024), Int1088.fromLong(Long.MIN_VALUE)}) {
            BigInteger integer = new BigInteger(value.toDecimalString());
            for (int range : new int[] {7, 11, 17, 65}) {
                assertEquals(integer.mod(BigInteger.valueOf(range)).intValue(),
                        ClientChunkCache.storageModulo(value, range));
            }
        }
    }

    @Test
    void anchoredRingIndicesRemainAbsoluteAcrossSeamsAndTeleports() {
        for (Int1088 origin : new Int1088[]{Int1088.ZERO, Int1088.of(-1, 7), Int1088.of(0, -7),
                Int1088.ONE.shiftLeftSaturated(512), Int1088.MIN, Int1088.MAX}) {
            for (int range : new int[]{7, 11, 17, 65}) {
                int residue = ClientChunkCache.storageModulo(origin, range);
                for (long delta : new long[]{-1048577, -1048576, -65, -1, 0, 1, 65, 1048576, 1048577}) {
                    Int1088 value = origin.addSaturated(Int1088.fromLong(delta));
                    assertEquals(ClientChunkCache.storageModulo(value, range),
                            ClientChunkCache.anchoredStorageModulo(value, origin, residue, range));
                }
                for (Int1088 value : new Int1088[]{Int1088.MIN, Int1088.MAX, Int1088.of(0, 8), Int1088.of(0, -8)}) {
                    assertEquals(ClientChunkCache.storageModulo(value, range),
                            ClientChunkCache.anchoredStorageModulo(value, origin, residue, range));
                }
            }
        }
    }

    @Test
    void trackingUsesBothHalvesAndDoesNotWrapAtEdges() {
        for (Int1088 center : new Int1088[] {Int1088.ZERO, Int1088.of(-1, 7), Int1088.of(-1, -7), Int1088.ONE.shiftLeftSaturated(1024),
                WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK}) {
            assertTrue(ClientChunkCache.inTrackingRange(center, center, center, center, 5));
            assertTrue(ClientChunkCache.inTrackingRange(center.add(Int1088.fromLong(5)), center,
                    center, center, 5));
            assertFalse(ClientChunkCache.inTrackingRange(center.add(Int1088.fromLong(6)), center,
                    center, center, 5));
            Int1088 distant = center.signum() < 0 ? center.addSaturated(Int1088.ONE.shiftLeftSaturated(1024))
                    : center.subtractSaturated(Int1088.ONE.shiftLeftSaturated(1024));
            assertFalse(ClientChunkCache.inTrackingRange(distant, center,
                    center, center, 5));
        }
        assertFalse(ClientChunkCache.inTrackingRange(Int1088.MIN, Int1088.MIN, Int1088.MAX, Int1088.MAX, 5));
        assertFalse(ClientChunkCache.inTrackingRange(Int1088.MAX, Int1088.MAX, Int1088.MIN, Int1088.MIN, 5));
    }

    @Test
    void clientSpawnRetainsExactIdentityAndLegacySettersSignExtend() {
        ClientLevel.ClientLevelData data = new ClientLevel.ClientLevelData(Difficulty.NORMAL, false, false);
        BlockPos position = new BlockPos(Int1088.of(-1, 7), 64, Int1088.of(0, -8));
        data.setSpawn(position, 37.0F);
        assertEquals(position.getX(), data.getXSpawn());
        assertEquals(position.getZ(), data.getZSpawn());
        assertEquals(64, data.getYSpawn());
        assertEquals(37.0F, data.getSpawnAngle());
        data.setXSpawn(-1L);
        data.setZSpawn(Long.MIN_VALUE);
        assertEquals(Int1088.fromLong(-1), data.getXSpawn());
        assertEquals(Int1088.fromLong(Long.MIN_VALUE), data.getZSpawn());
    }
}
