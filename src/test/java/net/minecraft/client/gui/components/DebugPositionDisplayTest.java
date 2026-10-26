package net.minecraft.client.gui.components;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Int128;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DebugPositionDisplayTest {
    @Test
    void separatesAxesWithoutLosingFullWidthCoordinates() {
        SectorVec3 exact = SectorVec3.fromBlockAndFraction(Int128.MAX, 0.125D, 5.5444D, Int128.MIN, 0.25D);
        List<String> lines = new ArrayList<>();
        DebugScreenOverlay.addExactPositionLines(lines, exact);
        assertEquals(List.of(
                "X: 170141183460469231731687303715884105727.125",
                "Y: 5.54440",
                "Z: -170141183460469231731687303715884105727.750"), lines);
    }

    @Test
    void formatsRawHalvesInLowercaseHexWithoutPrefix() {
        assertEquals("xLo/Hi: ffffffffffffffff / 7fffffffffffffff",
                DebugScreenOverlay.formatCoordinateHalves("x", Int128.MAX));
        assertEquals("zLo/Hi: f7ba291 / abcabc123",
                DebugScreenOverlay.formatCoordinateHalves("z", Int128.of(0xabcabc123L, 0xf7ba291L)));
        assertEquals("xLo/Hi: 0 / 8000000000000000",
                DebugScreenOverlay.formatCoordinateHalves("x", Int128.MIN));
    }

    @Test
    void subPositionShowsSevenDecimalPlaces() {
        SectorVec3 exact = SectorVec3.fromBlockAndFraction(123L, 0.12345678D, 5.5444D, 456L, 0.0000001D);
        assertEquals("SubPos: [X: +0.1234568, Y: +0.5444000, Z: +0.0000001]",
                DebugScreenOverlay.formatSubPosition(exact));
    }
}
