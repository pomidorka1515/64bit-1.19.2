package net.minecraft.world.level.levelgen.synth;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** One immutable, atomically published world-generation policy. */
public record GeneratorConfig(GeneratorMode mode, CoordinatePrecision precision) {
    public static final String MODE_KEY = "GeneratorMode";
    public static final String PRECISION_KEY = "GeneratorPrecision";
    public static final GeneratorConfig DEFAULT = new GeneratorConfig(GeneratorMode.BIT_32, CoordinatePrecision.LOSSY);

    public GeneratorConfig {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(precision);
    }

    public GeneratorConfig withMode(GeneratorMode value) { return new GeneratorConfig(value, this.precision); }
    public GeneratorConfig withPrecision(CoordinatePrecision value) { return new GeneratorConfig(this.mode, value); }

    public void write(CompoundTag tag) {
        tag.putString(MODE_KEY, this.mode.serializedName());
        tag.putString(PRECISION_KEY, this.precision.serializedName());
        tag.remove("FarlandsMode");
    }

    public static GeneratorConfig read(CompoundTag tag) {
        String legacy = tag.contains("FarlandsMode", Tag.TAG_STRING) ? tag.getString("FarlandsMode")
                : tag.contains("FarlandsMode") ? Boolean.toString(tag.getBoolean("FarlandsMode")) : "32bit";
        GeneratorMode mode = switch (legacy) {
            case "64bit", "false" -> GeneratorMode.BIT_64;
            case "off", "64bit-precise" -> GeneratorMode.PATCHED;
            default -> GeneratorMode.BIT_32;
        };
        CoordinatePrecision precision = "64bit-precise".equals(legacy) ? CoordinatePrecision.PRECISE : CoordinatePrecision.LOSSY;
        if (tag.contains(MODE_KEY)) {
            mode = GeneratorMode.parse(tag.getString(MODE_KEY));
            if (mode == null) {
                LogUtils.getLogger().warn("Invalid GeneratorMode in world data; using 32bit");
                mode = GeneratorMode.BIT_32;
            }
        }
        if (tag.contains(PRECISION_KEY)) {
            precision = CoordinatePrecision.parse(tag.getString(PRECISION_KEY));
            if (precision == null) {
                LogUtils.getLogger().warn("Invalid GeneratorPrecision in world data; using lossy");
                precision = CoordinatePrecision.LOSSY;
            }
        }
        return new GeneratorConfig(mode, precision);
    }

    public String debugLine(boolean endRings) {
        return "Config " + this.mode.generatorDescription() + " | " + this.precision.serializedName()
                + " | end rings: " + endRings;
    }
}
