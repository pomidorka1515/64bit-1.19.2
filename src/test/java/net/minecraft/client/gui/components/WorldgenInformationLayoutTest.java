package net.minecraft.client.gui.components;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldgenInformationLayoutTest {
    private static List<String> wrap(String text, int width) {
        int characters = Math.max(1, width / 6);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < text.length(); i += characters)
            result.add(text.substring(i, Math.min(text.length(), i + characters)));
        return result.isEmpty() ? List.of("") : result;
    }

    @Test
    void lowResolutionUsesColumnsInsteadOfFractionalGlyphs() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 80; i++) lines.add("Group value: 1.234e+20 other: -2.123e+18");
        lines.add("GPU " + "long hardware description ".repeat(8));
        var layout = DebugInformationLayout.arrange(lines, 854, 480, 2, WorldgenInformationLayoutTest::wrap);
        assertEquals(1, layout.pixelScale());
        assertTrue(layout.columns() > 1);
        assertTrue(layout.lines().size() <= layout.rows() * layout.columns());
        assertTrue(layout.columnWidth() * layout.columns() <= 854);
        assertTrue(layout.rows() * 9 + 4 <= 480);
    }

    @Test
    void retainsPreferredIntegerFontSizeWhenItFits() {
        var layout = DebugInformationLayout.arrange(List.of("Group value: 1"), 1920, 1080, 3,
                WorldgenInformationLayoutTest::wrap);
        assertEquals(3, layout.pixelScale());
        assertEquals(1, layout.columns());
    }

    @Test
    void fullHdWorldgenScalesOneColumnWithOneBottomLine() {
        int divider = DebugScreenOverlay.informationDivider(1920);
        List<String> worldgen = new ArrayList<>();
        for (int i = 0; i < 70; i++) worldgen.add("Group value: 1.234e+20 other: -2.123e+18");
        float scale = DebugInformationLayout.singleColumnScale(240, worldgen.size(), divider, 1080, 2);
        assertTrue(scale < 2);
        assertTrue(244 * scale <= divider);
        // The last background ends at y = 9 * lineCount + 1.
        assertEquals(10 * scale, 1080 - (9 * worldgen.size() + 1) * scale, 0.001);
        var right = DebugInformationLayout.arrange(List.of("GPU " + "hardware ".repeat(20)),
                1920 - divider, 1080, 2, WorldgenInformationLayoutTest::wrap);
        assertTrue(right.lines().size() > 1);
        assertEquals(2, right.pixelScale());
    }

    @Test
    void singleColumnFitsLongLinesWithoutWrapping() {
        float scale = DebugInformationLayout.singleColumnScale(1200, 70, 598, 480, 2);
        assertEquals(598.0 / 1204, scale, 0.000001);
        assertTrue((2 + 9 * 71) * scale <= 480);
    }

    @Test
    void singleColumnDoesNotEnlargeShortLists() {
        assertEquals(2.0F, DebugInformationLayout.singleColumnScale(240, 10, 1344, 1080, 2));
        assertEquals(2.5F, DebugInformationLayout.singleColumnScale(240, 10, 1344, 1080, 2.5));
    }

    @Test
    void rightAlignedTextEndsTwoLogicalPixelsFromEdge() {
        for (int width : new int[] {576, 577, 578, 579}) {
            var layout = DebugInformationLayout.arrange(List.of("Hardware"), width, 1080, 2,
                    WorldgenInformationLayoutTest::wrap);
            assertEquals(width / layout.pixelScale() - 2, layout.textX(0, 48, true) + 48);
            assertEquals(2, layout.textX(0, 48, false));
        }
    }

    @Test
    void multipleRightColumnsStayInsideTheirRegion() {
        var layout = DebugInformationLayout.arrange(java.util.Collections.nCopies(60, "Hardware"),
                854, 480, 2, WorldgenInformationLayoutTest::wrap);
        assertEquals(2, layout.pixelScale());
        assertTrue(layout.columns() > 1);
        for (int i = 0; i < layout.lines().size(); i++) {
            int x = layout.textX(i, 48, true);
            assertTrue(x >= 2);
            assertTrue(x + 48 <= layout.logicalWidth() - 2);
        }
        int lastColumn = (layout.columns() - 1) * layout.rows();
        assertEquals(layout.logicalWidth() - 2, layout.textX(lastColumn, 48, true) + 48);
    }
}
