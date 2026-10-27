package net.minecraft.world.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CoordinatePersistenceTest {
    private static Level level;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        level = EntityLoadingTestLevel.create();
    }

    @Test
    void entityTypeSaveLoadAndSpawnPacketsPreserveEveryLimbAndIgnorePosMirror() {
        for (String id : new String[] {"minecraft:marker", "minecraft:boat", "minecraft:armor_stand",
                "minecraft:snowball", "minecraft:item", "minecraft:tnt", "minecraft:experience_orb"}) {
            for (Int1088 coordinate : SectorEntityCoordinatesTest.coordinates()) {
                SectorVec3 expected = SectorEntityCoordinatesTest.position(coordinate);
                Entity entity = spawn(id, expected);
                CompoundTag saved = new CompoundTag();
                assertTrue(entity.save(saved), id);
                CompoundTag exact = saved.getCompound("ExactPos");
                assertEquals(17, exact.getLongArray("BlockX").length);
                assertEquals(17, exact.getLongArray("BlockZ").length);
                assertEquals(expected.blockX(), NbtUtils.readInt1088(exact, "BlockX"));
                assertEquals(expected.blockZ(), NbtUtils.readInt1088(exact, "BlockZ"));
                ListTag mirror = new ListTag();
                mirror.add(DoubleTag.valueOf(0.0D));
                mirror.add(DoubleTag.valueOf(-20.0D));
                mirror.add(DoubleTag.valueOf(0.0D));
                saved.put("Pos", mirror);
                Entity restored = EntityType.create(saved, level).orElseThrow();
                assertEquals(expected, restored.exactPosition(), id);
                assertEquals(expected, restored.oldExactPosition(), id);
                assertEquals(entity.blockPosition(), restored.blockPosition(), id);
                FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    new ClientboundAddEntityPacket(restored).write(buffer);
                    ClientboundAddEntityPacket packet = new ClientboundAddEntityPacket(buffer);
                    assertEquals(expected, packet.getExactPosition(), id);
                    assertEquals(restored.getType(), packet.getType());
                    assertEquals(0, buffer.readableBytes());
                    // Exercise base entity ingress without projectile owner/world lookups.
                    Entity receiver = spawn("minecraft:marker", SectorEntityCoordinatesTest.position(Int1088.ZERO));
                    receiver.recreateFromPacket(packet);
                    assertEquals(expected, receiver.exactPosition(), id);
                } finally {
                    buffer.release();
                }
            }
        }
    }

    @Test
    void malformedExactCoordinatesNeverFallBackToPosMirror() {
        Entity entity = spawn("minecraft:marker", SectorEntityCoordinatesTest.position(Int1088.MAX));
        CompoundTag valid = new CompoundTag();
        assertTrue(entity.save(valid));
        for (String axis : new String[] {"BlockX", "BlockZ"}) {
            for (int length : new int[] {0, 2, 16, 18}) {
                CompoundTag invalid = valid.copy();
                invalid.getCompound("ExactPos").putLongArray(axis, new long[length]);
                assertThrows(net.minecraft.ReportedException.class, () -> EntityType.create(invalid, level));
            }
            CompoundTag invalid = valid.copy();
            invalid.getCompound("ExactPos").putLong(axis, 7L);
            assertThrows(net.minecraft.ReportedException.class, () -> EntityType.create(invalid, level));
            invalid.getCompound("ExactPos").remove(axis);
            assertThrows(net.minecraft.ReportedException.class, () -> EntityType.create(invalid, level));
        }
        for (String key : new String[] {"SubX", "SubZ", "Y"}) {
            for (double number : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
                CompoundTag invalid = valid.copy();
                invalid.getCompound("ExactPos").putDouble(key, number);
                assertThrows(net.minecraft.ReportedException.class, () -> EntityType.create(invalid, level));
            }
        }
        for (String key : new String[] {"SubX", "SubZ"}) {
            for (double number : new double[] {-0.125D, 1.0D}) {
                CompoundTag invalid = valid.copy();
                invalid.getCompound("ExactPos").putDouble(key, number);
                assertThrows(net.minecraft.ReportedException.class, () -> EntityType.create(invalid, level));
            }
        }
        CompoundTag missing = valid.copy();
        missing.remove("ExactPos");
        assertThrows(net.minecraft.ReportedException.class, () -> EntityType.create(missing, level));
    }

    private static Entity spawn(String id, SectorVec3 position) {
        CompoundTag template = new CompoundTag();
        template.putString("id", id);
        if (id.equals("minecraft:item")) {
            template.put("Item", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE)
                    .save(new CompoundTag()));
        }
        Entity entity = EntityType.loadEntityRecursive(template, level, position, loaded -> loaded);
        assertNotNull(entity, id);
        assertEquals(position, entity.exactPosition(), id);
        return entity;
    }
}
