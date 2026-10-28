package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Int1088;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec2;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommandCoordinatesTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void teleportDispatcherRetainsExactTargetsRelativeCarryAndFeedback() throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        TeleportCommand.register(dispatcher);
        for (Int1088 axis : new Int1088[] {Int1088.ZERO, Int1088.fromLong(-1), Int1088.parseDecimal("9007199254740993"),
                Int1088.ONE.shiftLeftSaturated(127), Int1088.ONE.shiftLeftSaturated(1024),
                Int1088.ONE.shiftLeftSaturated(1024).negate(), Int1088.MIN, Int1088.MAX}) {
            TestEntity entity = new TestEntity();
            Source source = new Source(entity);
            assertEquals(1, dispatcher.execute("tp @s " + axis + " 100 " + axis, source));
            assertEquals(axis, entity.exactPosition().blockX());
            assertEquals(axis, entity.exactPosition().blockZ());
            assertEquals(0.5D, entity.exactPosition().subX());
            assertEquals(100.0D, entity.exactPosition().y());
            TranslatableContents feedback = (TranslatableContents) source.messages.get(0).getContents();
            assertEquals("commands.teleport.success.location.single", feedback.getKey());
            assertEquals(entity.exactPosition().formatX(6), feedback.getArgs()[1]);
            assertEquals(entity.exactPosition().formatZ(6), feedback.getArgs()[3]);

            SectorVec3 origin = entity.exactPosition();
            CommandSourceStack relativeSource = new Source(entity);
            if (axis.equals(Int1088.MIN) || axis.equals(Int1088.MAX)) {
                assertThrows(net.minecraft.commands.CommandRuntimeException.class,
                        () -> dispatcher.execute("tp @s ~.75 ~1.25 ~-.75", relativeSource));
                assertEquals(origin, entity.exactPosition());
                continue;
            }
            assertEquals(1, dispatcher.execute("tp @s ~.75 ~1.25 ~-.75", relativeSource));
            assertEquals(origin.add(0.75D, 1.25D, -0.75D), entity.exactPosition());
        }
    }

    @Test
    void teleportExpressionsAndScientificNotationUseTheSameCommandPath() throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        TeleportCommand.register(dispatcher);
        TestEntity entity = new TestEntity();
        assertEquals(1, dispatcher.execute("tp @s (2^1099+1-2^1099) 100 2^^3", new Source(entity)));
        assertEquals(Int1088.ONE, entity.exactPosition().blockX());
        assertEquals(0.5D, entity.exactPosition().subX());
        assertEquals(Int1088.fromLong(16), entity.exactPosition().blockZ());
        assertEquals(1, dispatcher.execute("tp @s 5.123456e+306 100 -223.293E302", new Source(entity)));
        assertEquals(new java.math.BigDecimal("5.123456e+306").toBigIntegerExact().toString(), entity.exactPosition().blockX().toDecimalString());
        var invalid = dispatcher.parse("tp @s 2^1088+1 0 0", new Source(entity));
        assertTrue(invalid.getReader().canRead() || !invalid.getExceptions().isEmpty());
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> dispatcher.execute("tp @s 2^1088+1 0 0", new Source(entity)));
    }

    private static class Source extends CommandSourceStack {
        final List<Component> messages = new ArrayList<>();
        Source(Entity entity) {
            super(null, entity.exactPosition().toExactVec3(), Vec2.ZERO, null, 2, "test", Component.literal("test"), null, entity);
        }
        @Override
        public void sendSuccess(Component message, boolean broadcast) { messages.add(message); }
    }

    private static class TestEntity extends Entity {
        TestEntity() { super(EntityType.PIG, null); }
        @Override public Component getDisplayName() { return Component.literal("test"); }
        protected void defineSynchedData() { }
        protected void readAdditionalSaveData(CompoundTag tag) { }
        protected void addAdditionalSaveData(CompoundTag tag) { }
        public Packet<?> getAddEntityPacket() { throw new UnsupportedOperationException(); }
    }
}
