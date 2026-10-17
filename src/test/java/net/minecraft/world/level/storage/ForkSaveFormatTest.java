package net.minecraft.world.level.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ForkSaveFormatTest {
    @TempDir
    Path directory;

    private CompoundTag metadata(int storageVersion, String series) {
        CompoundTag data = new CompoundTag();
        data.putInt("version", storageVersion);
        CompoundTag version = new CompoundTag();
        version.putString("Series", series);
        data.put("Version", version);
        return data;
    }

    private void write(String name, CompoundTag data) throws IOException {
        Path world = Files.createDirectories(directory.resolve("world"));
        CompoundTag root = new CompoundTag();
        root.put("Data", data);
        NbtIo.writeCompressed(root, world.resolve(name).toFile());
    }

    @Test
    void requiresBothIdentifiers() {
        assertDoesNotThrow(() -> ForkSaveFormat.validate(metadata(ForkSaveFormat.STORAGE_VERSION, ForkSaveFormat.SERIES)));
        assertThrows(IOException.class, () -> ForkSaveFormat.validate(metadata(19133, ForkSaveFormat.SERIES)));
        assertThrows(IOException.class, () -> ForkSaveFormat.validate(metadata(ForkSaveFormat.STORAGE_VERSION, "main")));
        assertThrows(IOException.class, () -> ForkSaveFormat.validate(new CompoundTag()));
    }

    @Test
    void rejectsVanillaBeforeCreatingLock() throws IOException {
        write("level.dat", metadata(19133, "main"));
        LevelStorageSource storage = new LevelStorageSource(directory, directory.resolve("backups"), null);
        assertThrows(IOException.class, () -> storage.createAccess("world"));
        assertFalse(Files.exists(directory.resolve("world/session.lock")));
    }

    @Test
    void rejectsIncompatibleBackupEvenWithCompatiblePrimary() throws IOException {
        write("level.dat", metadata(ForkSaveFormat.STORAGE_VERSION, ForkSaveFormat.SERIES));
        write("level.dat_old", metadata(19133, "main"));
        LevelStorageSource storage = new LevelStorageSource(directory, directory.resolve("backups"), null);
        assertThrows(IOException.class, () -> storage.createAccess("world"));
    }

    @Test
    void acceptsForkAndNewWorlds() throws IOException {
        write("level.dat", metadata(ForkSaveFormat.STORAGE_VERSION, ForkSaveFormat.SERIES));
        LevelStorageSource storage = new LevelStorageSource(directory, directory.resolve("backups"), null);
        try (var access = storage.createAccess("world")) {
            assertEquals("world", access.getLevelId());
        }
        try (var access = storage.createAccess("new-world")) {
            assertEquals("new-world", access.getLevelId());
        }
    }
}
