package net.minecraft.client.gui.font;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FontSetCacheTest {
    @Test
    void metricsCachePreservesFilteredAndUnfilteredProviderSelection() throws Exception {
        FontSet fonts = new FontSet(null, new ResourceLocation("test"));
        CountingProvider first = new CountingProvider(new TestGlyph(100));
        CountingProvider fallback = new CountingProvider(new TestGlyph(6));
        providers(fonts).addAll(List.of(first, fallback));
        for (int i = 0; i < 1000; ++i) {
            assertSame(first.glyph, fonts.getGlyphInfo(0x1f600, false));
            assertSame(fallback.glyph, fonts.getGlyphInfo(0x1f600, true));
        }
        assertEquals(1, first.lookups);
        assertEquals(1, fallback.lookups);
    }

    @Test
    void bakedGlyphIsCreatedOnlyOnceAndMissingMetricsAreCached() throws Exception {
        FontSet fonts = new FontSet(null, new ResourceLocation("test"));
        CountingProvider provider = new CountingProvider(new TestGlyph(6));
        providers(fonts).add(provider);
        BakedGlyph baked = fonts.getGlyph(65);
        for (int i = 0; i < 1000; ++i) assertSame(baked, fonts.getGlyph(65));
        assertEquals(1, provider.lookups);
        assertEquals(1, provider.glyph.bakes);

        provider.missing = true;
        for (int i = 0; i < 1000; ++i) {
            assertSame(SpecialGlyphs.MISSING, fonts.getGlyphInfo(66, false));
            assertSame(SpecialGlyphs.MISSING, fonts.getGlyphInfo(66, true));
        }
        assertEquals(2, provider.lookups);
    }

    @SuppressWarnings("unchecked")
    private static List<GlyphProvider> providers(FontSet fonts) throws Exception {
        Field field = FontSet.class.getDeclaredField("providers");
        field.setAccessible(true);
        return (List<GlyphProvider>) field.get(fonts);
    }

    private static final class CountingProvider implements GlyphProvider {
        final TestGlyph glyph;
        int lookups;
        boolean missing;

        CountingProvider(TestGlyph glyph) {
            this.glyph = glyph;
        }

        public GlyphInfo getGlyph(int codePoint) {
            ++this.lookups;
            return this.missing ? null : this.glyph;
        }

        public it.unimi.dsi.fastutil.ints.IntSet getSupportedGlyphs() {
            return it.unimi.dsi.fastutil.ints.IntSets.EMPTY_SET;
        }
    }

    private static final class TestGlyph implements GlyphInfo {
        final float advance;
        int bakes;

        TestGlyph(float advance) {
            this.advance = advance;
        }

        public float getAdvance() {
            return this.advance;
        }

        public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> stitch) {
            ++this.bakes;
            return new BakedGlyph(null, null, null, 0, 1, 0, 1, 0, 6, 0, 9);
        }
    }
}
