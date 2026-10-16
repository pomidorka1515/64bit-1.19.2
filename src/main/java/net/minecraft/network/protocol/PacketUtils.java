package net.minecraft.network.protocol;

import com.mojang.logging.LogUtils;
import net.minecraft.network.PacketListener;
import net.minecraft.server.RunningOnDifferentThreadException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.BlockableEventLoop;
import org.slf4j.Logger;

public class PacketUtils {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static <T extends PacketListener> void ensureRunningOnSameThread(Packet<T> pPacket, T pProcessor, ServerLevel pLevel) throws RunningOnDifferentThreadException {
        ensureRunningOnSameThread(pPacket, pProcessor, pLevel.getServer());
    }

    public static <T extends PacketListener> void ensureRunningOnSameThread(Packet<T> pPacket, T pProcessor, BlockableEventLoop<?> pExecutor) throws RunningOnDifferentThreadException {
        if (!pExecutor.isSameThread()) {
            pExecutor.executeIfPossible(() -> {
                if (pProcessor.getConnection().isConnected()) {
                    try {
                        pPacket.handle(pProcessor);
                    } catch (Exception exception) {
                        if (pProcessor.shouldPropagateHandlingExceptions()) {
                            throw exception;
                        }

                        LOGGER.error("Failed to handle packet {}, suppressing error", pPacket, exception);
                    }
                } else {
                    LOGGER.debug("Ignoring packet due to disconnection: {}", (Object) pPacket);
                }
            });
            throw RunningOnDifferentThreadException.RUNNING_ON_DIFFERENT_THREAD;
        }
    }
}
