package net.minecraft.client.multiplayer;

import com.google.common.primitives.Longs;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.exceptions.InsufficientPrivilegesException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.authlib.exceptions.UserBannedException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.logging.LogUtils;
import java.security.PublicKey;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.login.ClientLoginPacketListener;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ClientboundGameProfilePacket;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.ServerboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ServerboundKeyPacket;
import net.minecraft.realms.DisconnectedRealmsScreen;
import net.minecraft.realms.RealmsScreen;
import net.minecraft.util.Crypt;
import net.minecraft.util.HttpUtil;
import net.minecraft.util.Signer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class ClientHandshakePacketListenerImpl implements ClientLoginPacketListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Minecraft minecraft;
    @Nullable
    private final Screen parent;
    private final Consumer<Component> updateStatus;
    private final Connection connection;
    private GameProfile localGameProfile;

    public ClientHandshakePacketListenerImpl(Connection pConnection, Minecraft pMinecraft, @Nullable Screen pParent, Consumer<Component> pUpdateStatus) {
        this.connection = pConnection;
        this.minecraft = pMinecraft;
        this.parent = pParent;
        this.updateStatus = pUpdateStatus;
    }

    public void handleHello(ClientboundHelloPacket pPacket) {
        Cipher cipher;
        Cipher cipher1;
        String s;
        ServerboundKeyPacket serverboundkeypacket;
        try {
            SecretKey secretkey = Crypt.generateSecretKey();
            PublicKey publickey = pPacket.getPublicKey();
            s = Crypt.digestData(pPacket.getServerId(), publickey, secretkey);
            cipher = Crypt.getCipher(2, secretkey);
            cipher1 = Crypt.getCipher(1, secretkey);
            byte[] abyte = pPacket.getNonce();
            serverboundkeypacket = new ServerboundKeyPacket(secretkey, publickey, abyte);
        } catch (Exception exception) {
            throw new IllegalStateException("Protocol error", exception);
        }

        this.updateStatus.accept(Component.translatable("connect.authorizing"));
        HttpUtil.DOWNLOAD_EXECUTOR.submit(() -> {
            Component component = this.authenticateServer(s);
            if (component != null) {
                if (this.minecraft.getCurrentServer() == null || !this.minecraft.getCurrentServer().isLan()) {
                    this.connection.disconnect(component);
                    return;
                }

                LOGGER.warn(component.getString());
            }

            this.updateStatus.accept(Component.translatable("connect.encrypting"));
            this.connection.send(serverboundkeypacket, PacketSendListener.thenRun(() -> {
                this.connection.setEncryptionKey(cipher, cipher1);
            }));
        });
    }

    @Nullable
    private Component authenticateServer(String pServerHash) {
        try {
            this.getMinecraftSessionService().joinServer(this.minecraft.getUser().getGameProfile(), this.minecraft.getUser().getAccessToken(), pServerHash);
            return null;
        } catch (AuthenticationUnavailableException authenticationunavailableexception) {
            return Component.translatable("disconnect.loginFailedInfo", Component.translatable("disconnect.loginFailedInfo.serversUnavailable"));
        } catch (InvalidCredentialsException invalidcredentialsexception) {
            return Component.translatable("disconnect.loginFailedInfo", Component.translatable("disconnect.loginFailedInfo.invalidSession"));
        } catch (InsufficientPrivilegesException insufficientprivilegesexception) {
            return Component.translatable("disconnect.loginFailedInfo", Component.translatable("disconnect.loginFailedInfo.insufficientPrivileges"));
        } catch (UserBannedException userbannedexception) {
            return Component.translatable("disconnect.loginFailedInfo", Component.translatable("disconnect.loginFailedInfo.userBanned"));
        } catch (AuthenticationException authenticationexception) {
            return Component.translatable("disconnect.loginFailedInfo", authenticationexception.getMessage());
        }
    }

    private MinecraftSessionService getMinecraftSessionService() {
        return this.minecraft.getMinecraftSessionService();
    }

    public void handleGameProfile(ClientboundGameProfilePacket pPacket) {
        this.updateStatus.accept(Component.translatable("connect.joining"));
        this.localGameProfile = pPacket.getGameProfile();
        this.connection.setProtocol(ConnectionProtocol.PLAY);
        this.connection.setListener(new ClientPacketListener(this.minecraft, this.parent, this.connection, this.localGameProfile, this.minecraft.createTelemetryManager()));
    }

    public void onDisconnect(Component pReason) {
        if (this.parent != null && this.parent instanceof RealmsScreen) {
            this.minecraft.setScreen(new DisconnectedRealmsScreen(this.parent, CommonComponents.CONNECT_FAILED, pReason));
        } else {
            this.minecraft.setScreen(new DisconnectedScreen(this.parent, CommonComponents.CONNECT_FAILED, pReason));
        }
    }

    public Connection getConnection() {
        return this.connection;
    }

    public void handleDisconnect(ClientboundLoginDisconnectPacket pPacket) {
        Component reason = pPacket.getReason();
        if (reason.getContents() instanceof TranslatableContents contents
                && (contents.getKey().equals("multiplayer.disconnect.incompatible")
                || contents.getKey().equals("multiplayer.disconnect.outdated_client")
                || contents.getKey().equals("multiplayer.disconnect.outdated_server"))) {
            LOGGER.info("Server rejected network protocol: {}", reason.getString());
            reason = Component.literal("Incompatible server\n\nThis client requires a server running the same fork.\nVanilla Minecraft servers are not supported.");
        }
        this.connection.disconnect(reason);
    }

    public void handleCompression(ClientboundLoginCompressionPacket pPacket) {
        if (!this.connection.isMemoryConnection()) {
            this.connection.setupCompression(pPacket.getCompressionThreshold(), false);
        }
    }

    public void handleCustomQuery(ClientboundCustomQueryPacket pPacket) {
        this.updateStatus.accept(Component.translatable("connect.negotiating"));
        this.connection.send(new ServerboundCustomQueryPacket(pPacket.getTransactionId(), (FriendlyByteBuf) null));
    }
}
