package net.minecraft.world.level.storage;

import java.io.IOException;
import net.minecraft.nbt.CompoundTag;

public final class ForkSaveFormat {
    // Deliberately outside vanilla's Anvil/McRegion storage-version IDs.
    public static final int STORAGE_VERSION = 0x4C534303;
    public static final String SERIES = "mcp-int1088-coordinates";
    public static final String FORMAT_MARKER = "int1088-coordinates-v1";

    private ForkSaveFormat() {
    }

    public static Format inspect(CompoundTag data) {
        if (!data.contains("version", 3) || !data.contains("Version", 10) || !data.contains("ForkFormat", 8)) {
            return Format.UNKNOWN;
        }

        CompoundTag version = data.getCompound("Version");
        if (!version.contains("Series", 8)) {
            return Format.UNKNOWN;
        }

        int storageVersion = data.getInt("version");
        String series = version.getString("Series");
        if (storageVersion == STORAGE_VERSION && SERIES.equals(series)
                && FORMAT_MARKER.equals(data.getString("ForkFormat"))) {
            return Format.CURRENT;
        }
        if (storageVersion == 0x4C534302 && "mcp-int128-coordinates".equals(series)
                && "int128-coordinates-v1".equals(data.getString("ForkFormat"))) {
            return Format.INT128_FORK;
        }
        return Format.FOREIGN;
    }

    public static void validate(CompoundTag data) throws IOException {
        Format format = inspect(data);
        if (format != Format.CURRENT) {
            throw new IOException("Incompatible world format " + format + ": only " + FORMAT_MARKER + " saves are supported.");
        }
    }

    public enum Format {
        CURRENT,
        INT128_FORK {
            @Override
            public String toString() {
                return "128BIT_FORK";
            }
        },
        FOREIGN,
        UNKNOWN
    }
}
