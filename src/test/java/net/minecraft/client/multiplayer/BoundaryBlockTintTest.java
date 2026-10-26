package net.minecraft.client.multiplayer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
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
        for (Int128 x : new Int128[] {Int128.ZERO, Int128.MIN, Int128.MAX, Int128.of(7, -1)}) {
            for (Int128 z : new Int128[] {Int128.ZERO, Int128.MIN, Int128.MAX, Int128.of(-7, 0)}) {
                for (int radius : new int[] {0, 1, 7}) {
                    assertEquals(0x91BD59, ClientLevel.calculateBlockTint(new BlockPos(x, 64, z), radius, pos -> 0x91BD59));
                }
            }
        }
    }

    @Test
    void averagesOnlyRepresentableSamples() {
        assertEquals(0x202020, ClientLevel.calculateBlockTint(new BlockPos(Int128.MAX, 64, Int128.ZERO), 1,
                pos -> pos.getX().equals(Int128.MAX) ? 0x303030 : 0x101010));
        assertEquals(0x202020, ClientLevel.calculateBlockTint(new BlockPos(Int128.MIN, 64, Int128.ZERO), 1,
                pos -> pos.getX().equals(Int128.MIN) ? 0x303030 : 0x101010));
    }
}
