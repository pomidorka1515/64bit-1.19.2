package net.minecraft.client.multiplayer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
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
        for (Int1088 x : new Int1088[] {Int1088.ZERO, Int1088.MIN, Int1088.MAX, Int1088.of(-1, 7)}) {
            for (Int1088 z : new Int1088[] {Int1088.ZERO, Int1088.MIN, Int1088.MAX, Int1088.of(0, -7)}) {
                for (int radius : new int[] {0, 1, 7}) {
                    assertEquals(0x91BD59, ClientLevel.calculateBlockTint(new BlockPos(x, 64, z), radius, pos -> 0x91BD59));
                }
            }
        }
    }

    @Test
    void averagesOnlyRepresentableSamples() {
        assertEquals(0x202020, ClientLevel.calculateBlockTint(new BlockPos(Int1088.MAX, 64, Int1088.ZERO), 1,
                pos -> pos.getX().equals(Int1088.MAX) ? 0x303030 : 0x101010));
        assertEquals(0x202020, ClientLevel.calculateBlockTint(new BlockPos(Int1088.MIN, 64, Int1088.ZERO), 1,
                pos -> pos.getX().equals(Int1088.MIN) ? 0x303030 : 0x101010));
    }
}
