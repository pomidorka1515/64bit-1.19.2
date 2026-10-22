package net.minecraft.world.level.border;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.serialization.Dynamic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Int128;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;

class WorldBorderInt128Test {
    @Test
    void delegatedCenterChangesRetainFractionsAndDisabledCollision() {
        WorldBorder source = new WorldBorder();
        WorldBorder target = new WorldBorder();
        source.addListener(new BorderChangeListener.DelegateBorderChangeListener(target));
        SectorVec3 center = SectorVec3.fromBlockAndFraction(Int128.of(123456L, -3L), 0.125D,
                0.0D, Int128.of(-123456L, 9L), 0.875D);
        source.setCenter(center);
        assertEquals(center, target.getExactCenter());
        source.setSize(16.0D);
        assertEquals(16.0D, target.getSize());
        source.lerpSizeBetween(16.0D, 8.0D, 1000L);
        assertEquals(8.0D, target.getLerpTarget());
        assertTrue(source.getCollisionShape().isEmpty());
        assertTrue(target.getCollisionShape().isEmpty());
        assertTrue(target.isWithinBounds(new BlockPos(Int128.MAX, 64, Int128.MIN)));
    }

    @Test
    void settingsPreserveHighHalvesAndFractions() {
        WorldBorder border = new WorldBorder();
        SectorVec3 center = SectorVec3.fromBlockAndFraction(Int128.of(42, -7), 0.75D, 0.0D,
                Int128.MIN, 0.25D);
        border.setCenter(center);
        CompoundTag tag = new CompoundTag();
        border.createSettings().write(tag);
        WorldBorder restored = new WorldBorder();
        restored.applySettings(WorldBorder.Settings.read(new Dynamic<>(NbtOps.INSTANCE, tag), WorldBorder.DEFAULT_SETTINGS));
        assertEquals(center.blockX(), restored.getExactCenter().blockX());
        assertEquals(center.blockZ(), restored.getExactCenter().blockZ());
        assertEquals(0.75D, restored.getExactCenter().subX());
        assertEquals(0.25D, restored.getExactCenter().subZ());
        tag.remove("BorderCenterXHi");
        assertThrows(IllegalArgumentException.class, () -> WorldBorder.Settings.read(new Dynamic<>(NbtOps.INSTANCE, tag), WorldBorder.DEFAULT_SETTINGS));
    }

    @Test
    void disabledBorderAndSpawnBoundsAcceptEntireHorizontalDomain() {
        WorldBorder border = new WorldBorder();
        BlockPos pos = new BlockPos(Int128.MAX, 64, Int128.MIN);
        assertTrue(border.isWithinBounds(pos));
        assertTrue(border.getCollisionShape().isEmpty());
        assertEquals(Double.POSITIVE_INFINITY, border.getDistanceToBorder(SectorVec3.fromBlockAndFraction(pos.getX(), 0.5D, 64, pos.getZ(), 0.5D)));
        assertTrue(Level.isInSpawnableBounds(pos));
        assertFalse(Level.isInSpawnableBounds(new BlockPos(pos.getX(), 20000000, pos.getZ())));
        assertFalse(Level.isInSpawnableBounds(new BlockPos(pos.getX(), -20000001, pos.getZ())));
    }
}
