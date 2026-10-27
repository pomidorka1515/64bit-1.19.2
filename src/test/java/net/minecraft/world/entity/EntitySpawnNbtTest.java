package net.minecraft.world.entity;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EntitySpawnNbtTest {
    private static net.minecraft.world.level.Level level;
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        level = EntityLoadingTestLevel.create();
    }

    @Test
    void lightningLoadsFromMinimalSpawnNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:lightning_bolt");
        for (Int1088 coordinate : coordinates()) {
            SectorVec3 position = position(coordinate);
            Entity entity = EntityType.loadEntityRecursive(tag, level, position, loaded -> loaded);
            assertNotNull(entity);
            assertEquals(position, entity.exactPosition());
            assertEquals(position, entity.oldExactPosition());
        }
        assertFalse(tag.contains("ExactPos"));
    }

    @Test
    void otherEntityTypesLoadFromMinimalSpawnNbt() {
        for (String id : new String[] {"minecraft:armor_stand", "minecraft:area_effect_cloud",
                "minecraft:marker", "minecraft:end_crystal", "minecraft:boat", "minecraft:falling_block"}) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", id);
            SectorVec3 position = position(Int1088.of(7L, 123L));
            Entity entity = EntityType.loadEntityRecursive(tag, level, position, loaded -> loaded);
            assertNotNull(entity, id);
            assertEquals(position, entity.exactPosition(), id);
        }
    }

    @Test
    void nestedPassengersReceivePositionBeforeLoadingWithoutChangingTemplate() {
        CompoundTag root = new CompoundTag();
        root.putString("id", "minecraft:lightning_bolt");
        root.putBoolean("Silent", true);
        CompoundTag child = root.copy();
        CompoundTag grandchild = root.copy();
        ListTag children = new ListTag();
        children.add(grandchild);
        child.put("Passengers", children);
        ListTag passengers = new ListTag();
        passengers.add(child);
        root.put("Passengers", passengers);
        CompoundTag original = root.copy();
        SectorVec3 position = position(Int1088.MIN);
        int[] loadedCount = {0};
        Entity entity = EntityType.loadEntityRecursive(root, level, position, loaded -> {
            assertEquals(position, loaded.exactPosition());
            assertTrue(loaded.isSilent());
            loadedCount[0]++;
            return loaded;
        });
        assertNotNull(entity);
        assertEquals(3, loadedCount[0]);
        assertEquals(3L, entity.getSelfAndPassengers().count());
        assertEquals(original, root);
    }

    @Test
    void savedEntityDecoderStillRejectsMissingExactPosition() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:lightning_bolt");
        assertThrows(net.minecraft.ReportedException.class, () -> EntityType.create(tag, level));
    }

    @Test
    void spawnedEntityRoundTripsThroughStrictDecoder() {
        SectorVec3 position = position(Int1088.MAX);
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:marker");
        Entity entity = EntityType.loadEntityRecursive(tag, level, position, loaded -> loaded);
        assertNotNull(entity);
        CompoundTag saved = new CompoundTag();
        assertTrue(entity.save(saved));
        Entity restored = EntityType.create(saved, level).orElseThrow();
        assertEquals(position, restored.exactPosition());
    }

    private static SectorVec3 position(Int1088 coordinate) {
        return SectorVec3.fromBlockAndFraction(coordinate, 0.25D, 64.5D, coordinate, 0.75D);
    }

    private static Int1088[] coordinates() {
        return new Int1088[] {Int1088.ZERO, Int1088.fromLong(-10L), Int1088.fromLong(1L << 54),
                Int1088.of(7L, 123L), Int1088.of(7L, -123L), Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX};
    }
}
