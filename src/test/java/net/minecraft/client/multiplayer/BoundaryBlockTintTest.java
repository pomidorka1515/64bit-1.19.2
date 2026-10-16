package net.minecraft.client.multiplayer;

import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoundaryBlockTintTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void constantTintIsNotDarkenedByClippedBlendWindow() {
        for (long x : new long[] {0L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            for (long z : new long[] {0L, Long.MIN_VALUE, Long.MAX_VALUE}) {
                for (int radius : new int[] {0, 1, 7}) {
                    assertEquals(0x91BD59, ClientLevel.calculateBlockTint(new BlockPos(x, 64, z), radius, pos -> 0x91BD59));
                }
            }
        }
    }

    @Test
    void averagesOnlyRepresentableSamples() {
        assertEquals(0x202020, ClientLevel.calculateBlockTint(new BlockPos(Long.MAX_VALUE, 64, 0L), 1,
                pos -> pos.getX() == Long.MAX_VALUE ? 0x303030 : 0x101010));
        assertEquals(0x202020, ClientLevel.calculateBlockTint(new BlockPos(Long.MIN_VALUE, 64, 0L), 1,
                pos -> pos.getX() == Long.MIN_VALUE ? 0x303030 : 0x101010));
    }
}
