package net.minecraft.world.level.storage;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ForkSaveFormatTest {
    private static final DataFixer IDENTITY_FIXER = new DataFixer() {
        public <T> Dynamic<T> update(DSL.TypeReference type, Dynamic<T> input, int version, int newVersion) {
            return input;
        }

        public Schema getSchema(int key) {
            throw new UnsupportedOperationException();
        }
    };

    @TempDir
    Path directory;

    @BeforeAll
    static void initializeVersion() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private CompoundTag metadata(int storageVersion, String series) {
        CompoundTag data = new CompoundTag();
        data.putInt("version", storageVersion);
        CompoundTag version = new CompoundTag();
        version.putString("Series", series);
        data.put("Version", version);
        return data;
    }

    private CompoundTag metadata(ForkSaveFormat.Format format) {
        return switch (format) {
            case CURRENT -> {
                CompoundTag data = metadata(ForkSaveFormat.STORAGE_VERSION, ForkSaveFormat.SERIES);
                data.putString("ForkFormat", ForkSaveFormat.FORMAT_MARKER);
                yield data;
            }
            case LEGACY_64BIT_FORK -> metadata(0x4C534301, "mcp-long-coordinates");
            case VANILLA -> metadata(19133, "main");
            case UNKNOWN -> metadata(ForkSaveFormat.STORAGE_VERSION, ForkSaveFormat.SERIES);
        };
    }

    private LevelStorageSource storage() {
        return new LevelStorageSource(directory, directory.resolve("backups"), IDENTITY_FIXER);
    }

    private void write(String name, CompoundTag data) throws IOException {
        Path world = Files.createDirectories(directory.resolve("world"));
        CompoundTag root = new CompoundTag();
        root.put("Data", data);
        NbtIo.writeCompressed(root, world.resolve(name).toFile());
    }

    @Test
    void requiresAllThreeTypedIdentifiers() {
        assertDoesNotThrow(() -> ForkSaveFormat.validate(metadata(ForkSaveFormat.Format.CURRENT)));
        assertThrows(IOException.class, () -> ForkSaveFormat.validate(metadata(19133, ForkSaveFormat.SERIES)));
        assertThrows(IOException.class, () -> ForkSaveFormat.validate(metadata(ForkSaveFormat.STORAGE_VERSION, "main")));
        assertThrows(IOException.class, () -> ForkSaveFormat.validate(new CompoundTag()));
        for (String field : new String[] {"version", "Version", "ForkFormat"}) {
            CompoundTag data = metadata(ForkSaveFormat.Format.CURRENT);
            data.remove(field);
            assertEquals(ForkSaveFormat.Format.UNKNOWN, ForkSaveFormat.inspect(data));
            data.putLong(field, 0L);
            assertEquals(ForkSaveFormat.Format.UNKNOWN, ForkSaveFormat.inspect(data));
        }
        for (ForkSaveFormat.Format format : ForkSaveFormat.Format.values()) {
            assertEquals(format, ForkSaveFormat.inspect(metadata(format)));
            if (format != ForkSaveFormat.Format.CURRENT) {
                IOException error = assertThrows(IOException.class, () -> ForkSaveFormat.validate(metadata(format)));
                assertTrue(error.getMessage().contains(format.name()));
            }
        }
        assertEquals(ForkSaveFormat.Format.VANILLA, ForkSaveFormat.inspect(metadata(19132, "main")));
        CompoundTag mixed = metadata(ForkSaveFormat.Format.LEGACY_64BIT_FORK);
        mixed.putString("ForkFormat", ForkSaveFormat.FORMAT_MARKER);
        assertEquals(ForkSaveFormat.Format.UNKNOWN, ForkSaveFormat.inspect(mixed));
    }

    @Test
    void rejectsEveryForeignFormatWithoutChangingFiles() throws Exception {
        for (ForkSaveFormat.Format format : ForkSaveFormat.Format.values()) {
            if (format != ForkSaveFormat.Format.CURRENT) {
                write("level.dat", metadata(format));
                assertReadOnlyRejection(format);
            }
        }
    }

    @Test
    void rejectsForeignBackupsWithCurrentOrMissingPrimary() throws Exception {
        for (ForkSaveFormat.Format format : ForkSaveFormat.Format.values()) {
            if (format != ForkSaveFormat.Format.CURRENT) {
                write("level.dat", metadata(ForkSaveFormat.Format.CURRENT));
                write("level.dat_old", metadata(format));
                assertReadOnlyRejection(format);
                Files.delete(directory.resolve("world/level.dat"));
                assertReadOnlyRejection(format);
            }
        }
    }

    private void assertReadOnlyRejection(ForkSaveFormat.Format format) throws Exception {
        Path world = directory.resolve("world");
        for (String folder : new String[] {"region", "entities", "poi"}) {
            Path region = Files.createDirectories(world.resolve(folder));
            Files.write(region.resolve("r.-1.2.mca"), new byte[] {1, 2, 3, 4});
            Files.write(region.resolve("c.-1.2.mcc"), new byte[] {5, 6, 7, 8});
        }
        Map<String, String> before = checksums(world);
        IOException error = assertThrows(IOException.class, () -> storage().createAccess("world"));
        assertTrue(error.getMessage().contains(format.name()));
        assertTrue(error.getMessage().contains(world.toString()));
        assertEquals(before, checksums(world));
        assertFalse(Files.exists(world.resolve("session.lock")));
    }

    private Map<String, String> checksums(Path world) throws IOException, NoSuchAlgorithmException {
        Map<String, String> result = new TreeMap<>();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var paths = Files.walk(world)) {
            for (Path path : paths.toList()) {
                result.put(world.relativize(path).toString(), Files.isDirectory(path) ? "directory"
                        : HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path))));
            }
        }
        return result;
    }

    @Test
    void summariesListKnownFormatsButOnlyCurrentIsPlayable() throws IOException {
        LevelStorageSource storage = storage();
        var world = new LevelStorageSource.LevelDirectory(directory.resolve("world"));
        for (ForkSaveFormat.Format format : ForkSaveFormat.Format.values()) {
            write("level.dat", metadata(format));
            for (boolean locked : new boolean[] {false, true}) {
                LevelSummary summary = storage.levelSummaryReader(world, locked).apply(world.dataFile(), IDENTITY_FIXER);
                if (format == ForkSaveFormat.Format.UNKNOWN) {
                    assertNull(summary);
                } else {
                    assertNotNull(summary);
                    assertEquals(format == ForkSaveFormat.Format.CURRENT, summary.isCompatible());
                    assertEquals(locked || format != ForkSaveFormat.Format.CURRENT, summary.isDisabled());
                    assertFalse(summary.requiresManualConversion());
                    if (format != ForkSaveFormat.Format.CURRENT) {
                        assertTrue(summary.getInfo().getString().contains(format.name()));
                    }
                }
            }
        }
    }

    @Test
    void acceptsForkAndNewWorlds() throws IOException {
        write("level.dat", metadata(ForkSaveFormat.Format.CURRENT));
        write("level.dat_old", metadata(ForkSaveFormat.Format.CURRENT));
        LevelStorageSource storage = storage();
        try (var access = storage.createAccess("world")) {
            assertEquals("world", access.getLevelId());
        }
        try (var access = storage.createAccess("new-world")) {
            assertEquals("new-world", access.getLevelId());
        }
    }
}
