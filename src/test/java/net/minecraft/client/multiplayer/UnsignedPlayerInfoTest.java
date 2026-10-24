package net.minecraft.client.multiplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket;
import net.minecraft.world.entity.player.ProfilePublicKey;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.Test;

class UnsignedPlayerInfoTest {
    @Test
    void playerInfoNeverReadsOrValidatesChatSigningKeys() {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "Player");
        ClientboundPlayerInfoPacket.PlayerUpdate update = new ClientboundPlayerInfoPacket.PlayerUpdate(
                profile, 42, GameType.SURVIVAL, null, null) {
            @Override
            public ProfilePublicKey.Data getProfilePublicKey() {
                throw new AssertionError("Chat-signing keys must not be read");
            }
        };

        PlayerInfo info = new PlayerInfo(update);
        assertEquals(profile, info.getProfile());
        assertEquals(42, info.getLatency());
        assertNull(info.getProfilePublicKey());
    }
}
