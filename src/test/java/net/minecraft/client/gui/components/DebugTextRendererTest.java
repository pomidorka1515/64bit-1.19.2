package net.minecraft.client.gui.components;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DebugTextRendererTest {
    @Test
    void unchangedLinesAreMeasuredOnceAndUnusedLinesAreEvicted() {
        CountingFont font = new CountingFont();
        try (DebugTextRenderer renderer = new DebugTextRenderer(font)) {
            renderer.beginFrame();
            assertEquals(6, renderer.width("X: 123"));
            assertEquals(6, renderer.width(new String("X: 123")));
            renderer.endFrame();
            renderer.beginFrame();
            renderer.width("X: 123");
            renderer.endFrame();
            assertEquals(1, font.measurements);
            renderer.beginFrame();
            renderer.endFrame();
            renderer.beginFrame();
            renderer.width("X: 123");
            renderer.endFrame();
            assertEquals(2, font.measurements);
        }
    }

    @Test
    void wrappingIsCachedPerLineAndWidthAndInvalidatedOnResourceChanges() {
        CountingFont font = new CountingFont();
        try (DebugTextRenderer renderer = new DebugTextRenderer(font)) {
            renderer.beginFrame();
            var first = renderer.wrap("hardware", 100);
            renderer.width("X: 123");
            renderer.endFrame();
            renderer.beginFrame();
            assertSame(first, renderer.wrap(new String("hardware"), 100));
            renderer.width("X: 123");
            renderer.wrap("hardware", 50);
            renderer.wrap("changed", 100);
            renderer.endFrame();
            assertEquals(3, font.splits);
            assertEquals(1, font.measurements);

            font.version++;
            renderer.beginFrame();
            assertNotSame(first, renderer.wrap("hardware", 100));
            renderer.width("X: 123");
            renderer.endFrame();
            assertEquals(4, font.splits);
            assertEquals(2, font.measurements);

            renderer.close();
            renderer.beginFrame();
            renderer.wrap("hardware", 100);
            renderer.endFrame();
            assertEquals(5, font.splits);
        }
    }

    private static final class CountingFont extends Font {
        int measurements;
        int splits;
        long version;

        CountingFont() {
            super(location -> null, false);
        }

        @Override
        public int width(String text) {
            ++this.measurements;
            return text.length();
        }

        @Override
        public List<FormattedCharSequence> split(FormattedText text, int width) {
            ++this.splits;
            return List.of((sink) -> true);
        }

        @Override
        public long resourceVersion() {
            return this.version;
        }
    }
}
