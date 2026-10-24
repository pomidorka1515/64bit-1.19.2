package net.minecraft.world.level.levelgen.synth;

/** Test adapter for pre-refactor reference fixtures; not shipped in production. */
public final class FarlandsMode extends GenerationPolicy {
    public static void setMode(Mode mode) {
        GeneratorMode generator = mode == Mode.BIT_64 ? GeneratorMode.BIT_64
                : mode == Mode.OFF || mode == Mode.BIT_64_PRECISE ? GeneratorMode.PATCHED : GeneratorMode.BIT_32;
        GenerationContext.setStandalone(new GeneratorConfig(generator,
                mode == Mode.BIT_64_PRECISE ? CoordinatePrecision.PRECISE : CoordinatePrecision.LOSSY));
    }

    public static Mode getMode() {
        return switch (GenerationContext.current().mode()) {
            case BIT_32 -> Mode.BIT_32;
            case BIT_64, BIT_64_STABLE_SHIFTS -> Mode.BIT_64;
            case PATCHED -> usesPreciseCoordinates() ? Mode.BIT_64_PRECISE : Mode.OFF;
        };
    }

    public static Mode fromSerializedName(String name) {
        for (Mode mode : Mode.values()) if (mode.serializedName().equals(name)) return mode;
        return "false".equals(name) ? Mode.BIT_64 : Mode.BIT_32;
    }

    public enum Mode {
        BIT_32("32bit", "32-bit"), BIT_32_HYBRID("32bit-hybrid", "32-bit (hybrid)"),
        BIT_64("64bit", "64-bit"), OFF("off", "64-bit (no farlands)"),
        BIT_64_PRECISE("64bit-precise", "64-bit (exact lattice)");
        private final String name;
        private final String description;
        Mode(String name, String description) { this.name = name; this.description = description; }
        public String serializedName() { return name; }
        public String generatorDescription() { return description; }
    }
}
