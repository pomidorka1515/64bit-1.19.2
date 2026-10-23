package net.minecraft.world.level.levelgen.synth;

public enum CoordinatePrecision {
    PRECISE("precise"), LOSSY("lossy");

    private final String name;

    CoordinatePrecision(String name) { this.name = name; }
    public String serializedName() { return this.name; }

    public static CoordinatePrecision parse(String name) {
        for (CoordinatePrecision precision : values()) if (precision.name.equals(name)) return precision;
        return null;
    }
}
