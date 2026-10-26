package net.minecraft.world.level.levelgen.synth;

/** Intentional generator arithmetic, independent of coordinate precision. */
public enum GeneratorMode {
    BIT_32("32bit", "32bit"),
    BIT_32_STABLE_SHIFTS("32bit-stable-shifts", "32bit (stable shifts)"),
    BIT_64("64bit", "64bit"),
    BIT_64_STABLE_SHIFTS("64bit-stable-shifts", "64bit (stable shifts)"),
    PATCHED("64bit-patched", "64bit (no farlands)");

    private final String name;
    private final String displayName;

    GeneratorMode(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
    }

    public String serializedName() { return this.name; }
    public String generatorDescription() { return this.displayName; }

    public boolean uses32BitArithmetic() { return this == BIT_32 || this == BIT_32_STABLE_SHIFTS; }
    public boolean usesStableShifts() { return this == BIT_32_STABLE_SHIFTS || this == BIT_64_STABLE_SHIFTS; }

    public static GeneratorMode parse(String name) {
        for (GeneratorMode mode : values()) if (mode.name.equals(name)) return mode;
        return null;
    }
}
