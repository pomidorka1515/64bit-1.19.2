package net.minecraft.client.gui.components;

import java.util.ArrayList;
import java.util.List;
import java.math.BigInteger;
import net.minecraft.core.Int1088;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DebugPositionDisplayTest {
    @Test
    void targetPositionsUseSameStrictCutoffForBothAxes() {
        Int1088 limit = Int1088.ONE.shiftLeftSaturated(127);
        List<String> lines = new ArrayList<>();
        DebugScreenOverlay.addTargetPositionLines(lines, new BlockPos(limit, 64, limit.negate()));
        assertEquals(List.of("X: " + limit, "Y: 64", "Z: " + limit.negate()), lines);
        lines.clear();
        DebugScreenOverlay.addTargetPositionLines(lines, new BlockPos(limit.addSaturated(Int1088.ONE), -12,
                limit.negate().subtractSaturated(Int1088.ONE)));
        assertEquals(List.of("X: 1.7014118e38", "Y: -12", "Z: -1.7014118e38"), lines);
        lines.clear();
        DebugScreenOverlay.addTargetPositionLines(lines, new BlockPos(-1, 70, 123));
        assertEquals(List.of("X: -1", "Y: 70", "Z: 123"), lines);
    }

    @Test
    void separatesAxesWithoutLosingFullWidthCoordinates() {
        SectorVec3 exact = SectorVec3.fromBlockAndFraction(Int1088.MAX, 0.125D, 5.5444D, Int1088.MIN, 0.25D);
        List<String> lines = new ArrayList<>();
        DebugScreenOverlay.addExactPositionLines(lines, exact);
        assertEquals(List.of(
                "X: " + BigInteger.ONE.shiftLeft(1087).subtract(BigInteger.ONE) + ".125",
                "Y: 5.54440",
                "Z: -" + BigInteger.ONE.shiftLeft(1087).subtract(BigInteger.ONE) + ".750"), lines);
    }

    @Test
    void formatsRoundingAtFullWidthDomainEdges() {
        BigInteger magnitude = BigInteger.ONE.shiftLeft(1087);
        assertEquals(magnitude + ".000", DebugScreenOverlay.formatCoordinate(Int1088.MAX, 0.9999));
        assertEquals("-" + magnitude + ".000", DebugScreenOverlay.formatCoordinate(Int1088.MIN, 0));
        assertEquals("-0.500", DebugScreenOverlay.formatCoordinate(Int1088.fromLong(-1), 0.5));
    }

    @Test
    void subPositionShowsSevenDecimalPlaces() {
        SectorVec3 exact = SectorVec3.fromBlockAndFraction(123L, 0.12345678D, 5.5444D, 456L, 0.0000001D);
        assertEquals("SubPos: [X: +0.1234568, Y: +0.5444000, Z: +0.0000001]",
                DebugScreenOverlay.formatSubPosition(exact));
    }

    @Test
    void overlayAbbreviatesAndRestoresExactDisplayAcrossThreshold() {
        Int1088 limit = Int1088.ONE.shiftLeftSaturated(127);
        DebugScreenOverlay.PositionDisplayCache cache = new DebugScreenOverlay.PositionDisplayCache();
        cache.update(SectorVec3.fromBlockAndFraction(limit, 0, 5, limit.negate(), 0));
        assertFalse(cache.abbreviated);
        assertEquals("X: " + limit + ".000", cache.xLine);
        cache.update(SectorVec3.fromBlockAndFraction(limit, 0.5, 5, limit.negate().subtractSaturated(Int1088.ONE), 0.5));
        assertTrue(cache.abbreviated);
        assertEquals("X: 1.7014118e38", cache.xLine);
        assertEquals("Z: -1.7014118e38", cache.zLine);
        cache.update(SectorVec3.fromBlockAndFraction(1, 0.125, 5, -1, 0.5));
        assertFalse(cache.abbreviated);
        assertEquals("X: 1.125", cache.xLine);
        assertEquals("Z: -0.500", cache.zLine);
    }
}
