package net.minecraft.world.level.levelgen.synth;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratorConfigTest {
    @TempDir Path directory;

    @Test void allCombinationsPersistToCompressedNbt() throws Exception {
        for (GeneratorMode mode : GeneratorMode.values()) {
            for (CoordinatePrecision precision : CoordinatePrecision.values()) {
                GeneratorConfig expected = new GeneratorConfig(mode, precision);
                CompoundTag tag = new CompoundTag();
                expected.write(tag);
                var file = directory.resolve(mode.serializedName() + precision.serializedName() + ".dat").toFile();
                NbtIo.writeCompressed(tag, file);
                CompoundTag saved = NbtIo.readCompressed(file);
                assertEquals(mode.serializedName(), saved.getString("GeneratorMode"));
                assertEquals(precision.serializedName(), saved.getString("GeneratorPrecision"));
                assertFalse(saved.contains("FarlandsMode"));
                assertEquals(expected, GeneratorConfig.read(saved));
                assertEquals("Config End Rings: true Precision: " + precision.serializedName() + " Generator: " + mode.generatorDescription(), expected.debugLine(true));
                assertEquals("Config End Rings: false Precision: " + precision.serializedName() + " Generator: " + mode.generatorDescription(), expected.debugLine(false));
            }
        }
    }

    @Test void migrateAllLegacyRepresentations() {
        String[] values = {"32bit", "64bit", "off", "64bit-precise", "32bit-hybrid", "true", "false"};
        GeneratorMode[] modes = {GeneratorMode.BIT_32, GeneratorMode.BIT_64, GeneratorMode.PATCHED, GeneratorMode.PATCHED, GeneratorMode.BIT_32, GeneratorMode.BIT_32, GeneratorMode.BIT_64};
        for (int i = 0; i < values.length; i++) {
            CompoundTag tag = new CompoundTag();
            tag.putString("FarlandsMode", values[i]);
            assertEquals(new GeneratorConfig(modes[i], i == 3 ? CoordinatePrecision.PRECISE : CoordinatePrecision.LOSSY), GeneratorConfig.read(tag));
        }
        for (boolean value : new boolean[] {true, false}) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("FarlandsMode", value);
            assertEquals(value ? GeneratorMode.BIT_32 : GeneratorMode.BIT_64, GeneratorConfig.read(tag).mode());
        }
    }

    @Test void independentFieldsTakePrecedenceAndInvalidFieldsUseDefaults() {
        CompoundTag tag = new CompoundTag();
        tag.putString("FarlandsMode", "64bit-precise");
        tag.putString("GeneratorMode", "64bit");
        assertEquals(new GeneratorConfig(GeneratorMode.BIT_64, CoordinatePrecision.PRECISE), GeneratorConfig.read(tag));
        tag.putString("GeneratorMode", "invalid");
        tag.putInt("GeneratorPrecision", 17);
        assertEquals(GeneratorConfig.DEFAULT, GeneratorConfig.read(tag));
        assertEquals(GeneratorConfig.DEFAULT, GeneratorConfig.read(new CompoundTag()));
    }

    @Test void scopesAndAsyncTasksKeepIndependentSnapshots() {
        GeneratorConfig first = new GeneratorConfig(GeneratorMode.BIT_32, CoordinatePrecision.PRECISE);
        GeneratorConfig second = new GeneratorConfig(GeneratorMode.BIT_64, CoordinatePrecision.LOSSY);
        java.util.function.Supplier<GeneratorConfig> task;
        try (var ignored = GenerationContext.open(first)) {
            task = GenerationContext.capture(GenerationContext::current);
            assertTrue(FarlandsMode.isEnabled());
            assertTrue(FarlandsMode.usesPreciseCoordinates());
            try (var nested = GenerationContext.open(second)) {
                assertEquals(second, GenerationContext.current());
                assertEquals(first, task.get());
                assertEquals(second, GenerationContext.current());
            }
            assertEquals(first, GenerationContext.current());
        }
        assertEquals(first, task.get());
        assertEquals(CoordinatePrecision.PRECISE, first.withMode(GeneratorMode.PATCHED).precision());
        assertEquals(GeneratorMode.BIT_32, first.withPrecision(CoordinatePrecision.LOSSY).mode());
    }
}
