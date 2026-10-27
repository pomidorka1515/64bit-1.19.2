package net.minecraft.client.sounds;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Int1088;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;

class SoundPositionInt1088Test {
    @Test
    void worldSourcesSubtractBeforeConversionAndRebaseImmutableSnapshots() {
        for (Int1088 base : new Int1088[] {Int1088.ZERO, Int1088.of(-1, 123),
                Int1088.of(Long.MAX_VALUE, -123), Int1088.MIN.add(Int1088.fromLong(8)),
                Int1088.MAX.subtract(Int1088.fromLong(8))}) {
            SectorVec3 listener = SectorVec3.fromBlockAndFraction(base, 0.75D, 64, base, 0.25D);
            SectorVec3 source = listener.add(1.5D, 2, -0.5D);
            SimpleSoundInstance sound = new SimpleSoundInstance(new ResourceLocation("test", "sound"),
                    SoundSource.BLOCKS, 1, 1, RandomSource.create(0), false, 0,
                    SoundInstance.Attenuation.LINEAR, source, false);
            assertSame(source, sound.getExactPosition());
            SoundEngine.SourcePosition first = SoundEngine.sourcePosition(sound, listener);
            SoundEngine.SourcePosition second = SoundEngine.sourcePosition(sound, listener.add(1, 0, -1));
            assertPosition(first, 1.5D, 2, -0.5D);
            assertPosition(second, 0.5D, 2, 0.5D);
            assertPosition(SoundEngine.sourcePosition(sound, null), 0, 0, 0);
        }
    }

    @Test
    void uiOffsetsStayLocalAndLegacyWorldInputsAreRebased() {
        SectorVec3 listener = SectorVec3.fromApproximate(10, 20, 30);
        SimpleSoundInstance ui = legacy(true, 1, 2, 3);
        assertPosition(SoundEngine.sourcePosition(ui, listener), 1, 2, 3);
        assertPosition(SoundEngine.sourcePosition(legacy(false, 11, 22, 33), listener), 1, 2, 3);
        assertPosition(SoundEngine.sourcePosition(legacy(false, Double.NaN, 0, 0), listener), 0, 0, 0);
    }

    private static SimpleSoundInstance legacy(boolean relative, double x, double y, double z) {
        return new SimpleSoundInstance(new ResourceLocation("test", "sound"), SoundSource.MASTER,
                1, 1, RandomSource.create(0), false, 0, SoundInstance.Attenuation.NONE, x, y, z, relative);
    }

    private static void assertPosition(SoundEngine.SourcePosition position, double x, double y, double z) {
        assertEquals(x, position.x);
        assertEquals(y, position.y);
        assertEquals(z, position.z);
    }
}
