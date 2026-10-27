package net.minecraft.client.gui.components;

import java.util.ArrayList;
import java.util.List;
import java.math.BigInteger;
import net.minecraft.core.Int1088;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DebugPositionDisplayTest {
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
}
