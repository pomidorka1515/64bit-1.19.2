package net.minecraft.client.multiplayer.chat;

import com.google.common.collect.Queues;
import java.util.Deque;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MessageSigner;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.chat.SignedMessageHeader;
import net.minecraft.util.StringDecomposer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.commons.lang3.StringUtils;

@OnlyIn(Dist.CLIENT)
public class ChatListener {
    private final Minecraft minecraft;
    private final Deque<ChatListener.Message> delayedMessageQueue = Queues.newArrayDeque();
    private long messageDelay;
    private long previousMessageTime;

    public ChatListener(Minecraft pMinecraft) {
        this.minecraft = pMinecraft;
    }

    public void tick() {
        if (this.messageDelay != 0L) {
            if (Util.getMillis() >= this.previousMessageTime + this.messageDelay) {
                for (ChatListener.Message chatlistener$message = this.delayedMessageQueue.poll(); chatlistener$message != null && !chatlistener$message.accept(); chatlistener$message = this.delayedMessageQueue.poll()) {
                }
            }
        }
    }

    public void setMessageDelay(double pDelaySeconds) {
        long i = (long) (pDelaySeconds * 1000.0D);
        if (i == 0L && this.messageDelay > 0L) {
            this.delayedMessageQueue.forEach(ChatListener.Message::accept);
            this.delayedMessageQueue.clear();
        }

        this.messageDelay = i;
    }

    public void acceptNextDelayedMessage() {
        this.delayedMessageQueue.remove().accept();
    }

    public long queueSize() {
        return this.delayedMessageQueue.stream().filter(ChatListener.Message::isVisible).count();
    }

    public void clearQueue() {
        this.delayedMessageQueue.forEach((p_242052_) -> {
            p_242052_.remove();
            p_242052_.accept();
        });
        this.delayedMessageQueue.clear();
    }

    public boolean removeFromDelayedMessageQueue(MessageSignature pSignature) {
        for (ChatListener.Message chatlistener$message : this.delayedMessageQueue) {
            if (chatlistener$message.removeIfSignatureMatches(pSignature)) {
                return true;
            }
        }

        return false;
    }

    private boolean willDelayMessages() {
        return this.messageDelay > 0L && Util.getMillis() < this.previousMessageTime + this.messageDelay;
    }

    private void handleMessage(ChatListener.Message pMessage) {
        if (this.willDelayMessages()) {
            this.delayedMessageQueue.add(pMessage);
        } else {
            pMessage.accept();
        }
    }

    public void handleChatMessage(final PlayerChatMessage pChatMessage, final ChatType.Bound pBoundChatType) {
        final PlayerChatMessage playerchatmessage = pChatMessage;
        final Component component = pBoundChatType.decorate(playerchatmessage.serverContent());
        MessageSigner messagesigner = pChatMessage.signer();
        if (!messagesigner.isSystem()) {
            this.handleMessage(new ChatListener.Message() {
                private boolean removed;

                public boolean accept() {
                    if (this.removed) {
                        ChatListener.this.markMessageAsProcessed(pChatMessage, false);
                        return false;
                    } else {
                        return ChatListener.this.processPlayerChatMessage(pBoundChatType, pChatMessage, component);
                    }
                }

                public boolean removeIfSignatureMatches(MessageSignature p_242335_) {
                    if (pChatMessage.headerSignature().equals(p_242335_)) {
                        this.removed = true;
                        return true;
                    } else {
                        return false;
                    }
                }

                public void remove() {
                    this.removed = true;
                }

                public boolean isVisible() {
                    return !this.removed;
                }
            });
        } else {
            this.handleMessage(new ChatListener.Message() {
                public boolean accept() {
                    return ChatListener.this.processNonPlayerChatMessage(pBoundChatType, playerchatmessage, component);
                }

                public boolean isVisible() {
                    return true;
                }
            });
        }
    }

    public void handleChatHeader(SignedMessageHeader pHeader, MessageSignature pHeaderSignature, byte[] pBodyDigest) {
        // Report-evidence headers are not retained or validated.
    }

    boolean processPlayerChatMessage(ChatType.Bound pBoundChatType, PlayerChatMessage pChatMessage, Component pDecoratedServerContent) {
        boolean flag = this.showMessageToPlayer(pBoundChatType, pChatMessage, pDecoratedServerContent);
        this.markMessageAsProcessed(pChatMessage, flag);
        return flag;
    }

    private void markMessageAsProcessed(PlayerChatMessage pChatMessage, boolean pVisible) {
        ClientPacketListener clientpacketlistener = this.minecraft.getConnection();
        if (clientpacketlistener != null) {
            clientpacketlistener.markMessageAsProcessed(pChatMessage, pVisible);
        }
    }

    private boolean showMessageToPlayer(ChatType.Bound pBoundChatType, PlayerChatMessage pChatMessage, Component pDecoratedServerContent) {
        if (!this.minecraft.isBlocked(pChatMessage.signer().profileId()) && !pChatMessage.isFullyFiltered()) {
            MessageSignature messagesignature = pChatMessage.headerSignature();
            FilterMask filtermask = pChatMessage.filterMask();
            if (filtermask.isEmpty()) {
                this.minecraft.gui.getChat().addMessage(pDecoratedServerContent, messagesignature, null);
                this.narrateChatMessage(pBoundChatType, pChatMessage.serverContent());
            } else {
                Component component = filtermask.apply(pChatMessage.signedContent());
                if (component != null) {
                    this.minecraft.gui.getChat().addMessage(pBoundChatType.decorate(component), messagesignature, null);
                    this.narrateChatMessage(pBoundChatType, component);
                }
            }

            this.previousMessageTime = Util.getMillis();
            return true;
        } else {
            return false;
        }
    }

    boolean processNonPlayerChatMessage(ChatType.Bound pBoundChatType, PlayerChatMessage pChatMessage, Component pDecoratedServerContent) {
        this.minecraft.gui.getChat().addMessage(pDecoratedServerContent);
        this.narrateChatMessage(pBoundChatType, pChatMessage.serverContent());
        this.previousMessageTime = Util.getMillis();
        return true;
    }

    private void narrateChatMessage(ChatType.Bound pBoundChatType, Component pMessage) {
        this.minecraft.getNarrator().sayChatNow(() -> {
            return pBoundChatType.decorateNarration(pMessage);
        });
    }

    public void handleSystemMessage(Component pMessage, boolean pIsOverlay) {
        if (!this.minecraft.options.hideMatchedNames().get() || !this.minecraft.isBlocked(this.guessChatUUID(pMessage))) {
            if (pIsOverlay) {
                this.minecraft.gui.setOverlayMessage(pMessage, false);
            } else {
                this.minecraft.gui.getChat().addMessage(pMessage);
            }

            this.minecraft.getNarrator().sayNow(pMessage);
        }
    }

    private UUID guessChatUUID(Component pMessage) {
        String s = StringDecomposer.getPlainText(pMessage);
        String s1 = StringUtils.substringBetween(s, "<", ">");
        return s1 == null ? Util.NIL_UUID : this.minecraft.getPlayerSocialManager().getDiscoveredUUID(s1);
    }

    @OnlyIn(Dist.CLIENT)
    interface Message {
        default boolean removeIfSignatureMatches(MessageSignature pSignature) {
            return false;
        }

        default void remove() {
        }

        boolean accept();

        default boolean isVisible() {
            return false;
        }
    }
}
