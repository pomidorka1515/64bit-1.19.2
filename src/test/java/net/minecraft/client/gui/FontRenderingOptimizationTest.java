package net.minecraft.client.gui;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FontRenderingOptimizationTest {
    @Test
    void plainTextResolvesFontOnceAndDoesNotCopyOrMutatePose() {
        RecordingGlyph glyph = new RecordingGlyph();
        FontSet set = fontSet(glyph);
        AtomicInteger resolutions = new AtomicInteger();
        Font font = new Font(location -> { resolutions.incrementAndGet(); return set; }, false);
        Matrix4f pose = Matrix4f.createTranslateMatrix(10, 20, 30);
        Matrix4f before = pose.copy();
        assertEquals(20, font.drawInBatch("abc", 2, 3, 0xffffff, false, pose, type -> null, false, 0, 15728880, false));
        assertEquals(1, resolutions.get());
        assertEquals(3, glyph.poses.size());
        glyph.poses.forEach(actual -> assertSame(pose, actual));
        assertEquals(before, pose);
    }

    @Test
    void formattedShadowStillOffsetsOnlyForegroundAndResolvesFontChanges() {
        RecordingGlyph glyph = new RecordingGlyph();
        FontSet set = fontSet(glyph);
        List<ResourceLocation> resolutions = new ArrayList<>();
        Font font = new Font(location -> { resolutions.add(location); return set; }, false);
        Style alternate = Style.EMPTY.withFont(new ResourceLocation("alternate"));
        FormattedCharSequence sequence = sink -> sink.accept(0, Style.EMPTY, 'a')
                && sink.accept(1, Style.EMPTY, 'b') && sink.accept(2, alternate, 'c');
        Matrix4f pose = Matrix4f.createTranslateMatrix(10, 20, 30);
        Matrix4f before = pose.copy();
        Matrix4f foreground = pose.copy();
        foreground.translate(new Vector3f(0, 0, 0.03F));
        assertEquals(21, font.drawInBatch(sequence, 2, 3, 0xffffff, true, pose, type -> null, false, 0, 15728880));
        assertEquals(List.of(Style.DEFAULT_FONT, alternate.getFont(), Style.DEFAULT_FONT, alternate.getFont()), resolutions);
        assertEquals(6, glyph.poses.size());
        for (int i = 0; i < 3; ++i) assertSame(pose, glyph.poses.get(i));
        for (int i = 3; i < 6; ++i) assertEquals(foreground, glyph.poses.get(i));
        assertEquals(before, pose);
    }

    private static FontSet fontSet(BakedGlyph glyph) {
        return new FontSet(null, Style.DEFAULT_FONT) {
            public GlyphInfo getGlyphInfo(int character, boolean filter) {
                return (GlyphInfo.SpaceGlyphInfo) () -> 6;
            }

            public BakedGlyph getGlyph(int character) {
                return glyph;
            }
        };
    }

    private static final class RecordingGlyph extends BakedGlyph {
        final List<Matrix4f> poses = new ArrayList<>();

        RecordingGlyph() {
            super(null, null, null, 0, 1, 0, 1, 0, 6, 0, 9);
        }

        @Override
        public void render(boolean italic, float x, float y, Matrix4f pose, VertexConsumer buffer,
                           float red, float green, float blue, float alpha, int light) {
            this.poses.add(pose);
        }
    }
}
