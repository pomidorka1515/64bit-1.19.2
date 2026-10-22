package net.minecraft.world.phys;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyAbsoluteCoordinateApiTest {
    @Test
    void worldEffectsRequireExactOrigins() throws Exception {
        for (String owner : List.of("world.level.Level", "world.level.LevelAccessor",
                "server.level.ServerLevel", "client.multiplayer.ClientLevel",
                "client.particle.ParticleEngine", "client.renderer.LevelRenderer")) {
            Class<?> type = Class.forName("net.minecraft." + owner, false, getClass().getClassLoader());
            for (Method method : type.getDeclaredMethods()) {
                if (!List.of("playSound", "playSeededSound", "playLocalSound", "addParticle",
                        "addAlwaysVisibleParticle", "explode", "sendParticles", "createParticle")
                        .contains(method.getName())) continue;
                Class<?>[] parameters = method.getParameterTypes();
                for (int i = 0; i + 2 < parameters.length; i++) {
                    if (parameters[i] == double.class && parameters[i + 1] == double.class
                            && parameters[i + 2] == double.class) {
                        // Exact overloads also take triples of local particle velocities/spreads.
                        assertTrue(List.of(parameters).contains(SectorVec3.class), method.toString());
                    }
                }
            }
        }
    }

    @Test
    void auditedHorizontalMirrorsCannotBeReintroduced() throws Exception {
        for (String owner : List.of(
                "world.entity.Entity", "core.Position", "core.PositionImpl",
                "core.BlockSource", "core.BlockSourceImpl",
                "world.level.block.entity.Hopper", "world.level.block.entity.HopperBlockEntity",
                "client.resources.sounds.SoundInstance", "client.resources.sounds.AbstractSoundInstance",
                "world.level.border.WorldBorder")) {
            Class<?> type = Class.forName("net.minecraft." + owner, false, getClass().getClassLoader());
            for (Method method : type.getDeclaredMethods()) {
                assertFalse(List.of("getX", "getZ", "x", "z", "getLevelX", "getLevelZ",
                        "getCenterX", "getCenterZ", "getMinX", "getMaxX", "getMinZ", "getMaxZ")
                        .contains(method.getName()), method.toString());
            }
        }
    }

    @Test
    void auditedPacketMirrorsCannotBeReintroduced() throws Exception {
        for (String packet : List.of("ClientboundAddEntityPacket", "ClientboundAddExperienceOrbPacket",
                "ClientboundAddPlayerPacket", "ClientboundMoveVehiclePacket", "ServerboundMoveVehiclePacket",
                "ClientboundTeleportEntityPacket", "ClientboundSoundPacket", "ClientboundCustomSoundPacket",
                "ClientboundExplodePacket", "ClientboundLevelParticlesPacket", "ClientboundPlayerPositionPacket",
                "ServerboundMovePlayerPacket", "ClientboundSetBorderCenterPacket", "ClientboundInitializeBorderPacket")) {
            Class<?> type = Class.forName("net.minecraft.network.protocol.game." + packet, false,
                    getClass().getClassLoader());
            for (Method method : type.getDeclaredMethods()) {
                assertFalse(List.of("getX", "getZ", "getNewCenterX", "getNewCenterZ")
                        .contains(method.getName()), method.toString());
            }
        }
    }

    @Test
    void absolutePositionAndOldEntityMirrorsAreRemoved() throws Exception {
        Class<?> entity = net.minecraft.world.entity.Entity.class;
        for (String name : List.of("xo", "yo", "zo", "xOld", "yOld", "zOld")) {
            assertThrows(NoSuchFieldException.class, () -> entity.getDeclaredField(name));
        }
        for (String owner : List.of("commands.CommandSourceStack", "commands.arguments.coordinates.Coordinates",
                "world.level.gameevent.PositionSource", "network.protocol.game.ClientboundPlayerLookAtPacket")) {
            Class<?> type = Class.forName("net.minecraft." + owner, false, getClass().getClassLoader());
            for (Method method : type.getDeclaredMethods()) {
                assertNotEquals("getPosition", method.getName(), method.toString());
            }
        }
    }
}
