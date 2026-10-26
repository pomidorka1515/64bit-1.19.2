package net.minecraft.client.gui.components;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/** Layout calculations in framebuffer pixels, independent of the GUI scale. */
final class DebugInformationLayout {
    static float singleColumnScale(int widestLine, int lineCount, int width, int height, double preferredScale) {
        // Keep the original glyph proportions and one extra line of bottom space.
        double widthScale = (double) width / (widestLine + 4.0);
        double heightScale = (double) height / (2.0 + 9.0 * (lineCount + 1));
        return (float) Math.min(preferredScale, Math.min(widthScale, heightScale));
    }

    record Layout<T>(int pixelScale, int columns, int rows, int columnWidth, int logicalWidth, List<T> lines) {
        int textX(int line, int textWidth, boolean right) {
            int column = line / rows;
            return right ? logicalWidth - 2 - (columns - 1 - column) * columnWidth - textWidth
                    : 2 + column * columnWidth;
        }
    }

    static <T> Layout<T> arrange(List<String> source, int width, int height, int preferredScale,
                                BiFunction<String, Integer, List<T>> wrap) {
        Layout<T> fallback = null;
        int maxColumns = Math.max(1, Math.min(6, width / 120));
        // Use available horizontal space before reducing the font size.
        for (int scale = Math.max(1, preferredScale); scale >= 1; scale--) {
            int logicalWidth = Math.max(1, width / scale);
            int rows = Math.max(1, (height / scale - 4) / 9);
            for (int columns = 1; columns <= maxColumns; columns++) {
                if (columns > 1 && logicalWidth / columns < 120) continue;
                int columnWidth = Math.max(1, (logicalWidth - 4) / columns);
                List<T> lines = new ArrayList<>();
                for (String line : source) lines.addAll(wrap.apply(line, Math.max(1, columnWidth - 6)));
                var layout = new Layout<>(scale, columns, rows, columnWidth, logicalWidth, List.copyOf(lines));
                if (lines.size() <= rows * columns) return layout;
                if (scale == 1 && (fallback == null ||
                        lines.size() - rows * columns < fallback.lines().size() - fallback.rows() * fallback.columns())) {
                    fallback = layout;
                }
            }
        }
        return fallback;
    }
}
