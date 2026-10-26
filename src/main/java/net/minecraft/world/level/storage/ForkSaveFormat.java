package net.minecraft.world.level.storage;

import java.io.IOException;
import net.minecraft.nbt.CompoundTag;

public final class ForkSaveFormat {
    // Deliberately outside vanilla's Anvil/McRegion storage-version IDs.
    public static final int STORAGE_VERSION = 0x4C534302;
    public static final String SERIES = "mcp-int128-coordinates";
    public static final String FORMAT_MARKER = "int128-coordinates-v1";

    private ForkSaveFormat() {
    }

    public static Format inspect(CompoundTag data) {
        if (!data.contains("version", 3) || !data.contains("Version", 10)) {
            return Format.UNKNOWN;
        }

        CompoundTag version = data.getCompound("Version");
        if (!version.contains("Series", 8)) {
            return Format.UNKNOWN;
        }

        int storageVersion = data.getInt("version");
        String series = version.getString("Series");
        if (storageVersion == STORAGE_VERSION && SERIES.equals(series)
                && data.contains("ForkFormat", 8) && FORMAT_MARKER.equals(data.getString("ForkFormat"))) {
            return Format.CURRENT;
        }

        // Old formats have no ForkFormat tag; mixed or partial markers are unknown.
        if (!data.contains("ForkFormat")) {
            if (storageVersion == 0x4C534301 && "mcp-long-coordinates".equals(series)) {
                return Format.LEGACY_64BIT_FORK;
            }
            if ((storageVersion == 19132 || storageVersion == 19133) && "main".equals(series)) {
                return Format.VANILLA;
            }
        }

        return Format.UNKNOWN;
    }

    public static void validate(CompoundTag data) throws IOException {
        Format format = inspect(data);
        if (format != Format.CURRENT) {
            throw new IOException("Incompatible world format " + format + ": only " + FORMAT_MARKER + " saves are supported.");
        }
    }

    public enum Format {
        CURRENT,
        LEGACY_64BIT_FORK,
        VANILLA,
        UNKNOWN
    }
}
