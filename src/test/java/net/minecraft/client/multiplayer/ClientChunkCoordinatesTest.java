package net.minecraft.client.multiplayer;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.WorldBounds;
import org.junit.jupiter.api.Test;

class ClientChunkCoordinatesTest {
    @Test
    void ringIndicesMatchSignedFullWidthModulo() {
        for (Int128 value : new Int128[] {Int128.ZERO, Int128.MIN, Int128.MAX,
                Int128.of(7, -1), Int128.of(-7, 0), Int128.fromLong(Long.MIN_VALUE)}) {
            BigInteger integer = BigInteger.valueOf(value.hi()).shiftLeft(64)
                    .add(new BigInteger(Long.toUnsignedString(value.lo())));
            for (int range : new int[] {7, 11, 17, 65}) {
                assertEquals(integer.mod(BigInteger.valueOf(range)).intValue(),
                        ClientChunkCache.storageModulo(value, range));
            }
        }
    }

    @Test
    void trackingUsesBothHalvesAndDoesNotWrapAtEdges() {
        for (Int128 center : new Int128[] {Int128.ZERO, Int128.of(7, -1), Int128.of(-7, -1),
                WorldBounds.MIN_CHUNK, WorldBounds.MAX_CHUNK}) {
            assertTrue(ClientChunkCache.inTrackingRange(center, center, center, center, 5));
            assertTrue(ClientChunkCache.inTrackingRange(center.add(Int128.fromLong(5)), center,
                    center, center, 5));
            assertFalse(ClientChunkCache.inTrackingRange(center.add(Int128.fromLong(6)), center,
                    center, center, 5));
            assertFalse(ClientChunkCache.inTrackingRange(Int128.of(center.hi() + 1, center.lo()), center,
                    center, center, 5));
        }
        assertFalse(ClientChunkCache.inTrackingRange(Int128.MIN, Int128.MIN, Int128.MAX, Int128.MAX, 5));
        assertFalse(ClientChunkCache.inTrackingRange(Int128.MAX, Int128.MAX, Int128.MIN, Int128.MIN, 5));
    }

    @Test
    void clientSpawnRetainsExactIdentityAndLegacySettersSignExtend() {
        ClientLevel.ClientLevelData data = new ClientLevel.ClientLevelData(Difficulty.NORMAL, false, false);
        BlockPos position = new BlockPos(Int128.of(7, -1), 64, Int128.of(-8, 0));
        data.setSpawn(position, 37.0F);
        assertEquals(position.getX(), data.getXSpawn());
        assertEquals(position.getZ(), data.getZSpawn());
        assertEquals(64, data.getYSpawn());
        assertEquals(37.0F, data.getSpawnAngle());
        data.setXSpawn(-1L);
        data.setZSpawn(Long.MIN_VALUE);
        assertEquals(Int128.fromLong(-1), data.getXSpawn());
        assertEquals(Int128.fromLong(Long.MIN_VALUE), data.getZSpawn());
    }
}
