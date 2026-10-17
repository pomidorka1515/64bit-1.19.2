package net.minecraft.world.level.storage;

import java.io.IOException;
import net.minecraft.nbt.CompoundTag;

public final class ForkSaveFormat {
    // Deliberately outside vanilla's Anvil/McRegion storage-version IDs.
    public static final int STORAGE_VERSION = 0x4C534301;
    public static final String SERIES = "mcp-long-coordinates";

    private ForkSaveFormat() {
    }

    public static void validate(CompoundTag data) throws IOException {
        if (!data.contains("version", 3) || data.getInt("version") != STORAGE_VERSION
                || !SERIES.equals(data.getCompound("Version").getString("Series"))) {
            throw new IOException("Incompatible world: only saves created with this fork's save format are supported.");
        }
    }
}
