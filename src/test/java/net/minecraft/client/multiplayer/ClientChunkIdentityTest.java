package net.minecraft.client.multiplayer;

import net.minecraft.core.Int1088;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientChunkIdentityTest {
    @Test
    void validationIdentityDoesNotMaterializeOrAliasCoordinates() {
        Int1088[] values = {Int1088.ZERO, Int1088.fromLong(-17),
                Int1088.fromLong(Long.MIN_VALUE), Int1088.fromLong(Long.MAX_VALUE),
                Int1088.of(0, 7), Int1088.of(0, -7), Int1088.ONE.shiftLeftSaturated(1024)};
        for (Int1088 x : values) for (Int1088 z : values) {
            ChunkPos position = new ChunkPos(x, z) {
                @Override public Int1088 x() { throw new AssertionError("materialized X"); }
                @Override public Int1088 z() { throw new AssertionError("materialized Z"); }
            };
            assertTrue(position.equals(x, z));
            assertFalse(position.equals(x.addSaturated(1), z));
            assertFalse(position.equals(x, z.addSaturated(1)));
            assertEquals(x.fitsLong() && z.fitsLong(), position.equals(x.lowLimb(), z.lowLimb()));
            assertFalse(position.equals(null, z));
            assertFalse(position.equals(x, null));
        }
    }
}
