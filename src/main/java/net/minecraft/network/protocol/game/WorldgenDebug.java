package net.minecraft.network.protocol.game;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.EndRingsMode;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.FarlandsMode;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/** On-demand server diagnostics. Sampling never requests or generates chunks. */
public final class WorldgenDebug {
    public static final ResourceLocation CHANNEL = new ResourceLocation("minecraft", "worldgen_debug");

    private WorldgenDebug() {
    }

    public static ClientboundCustomPayloadPacket sample(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        ServerChunkCache chunks = player.getLevel().getChunkSource();
        List<String> lines = new ArrayList<>();
        BlendedNoise.beginDebugCapture();
        ImprovedNoise.beginDebugCapture();
        try (var ignored = net.minecraft.world.level.levelgen.synth.GenerationContext.open(player.getServer().getWorldData().generatorConfig())) {
            chunks.getGenerator().addDebugScreenInfo(lines, chunks.randomState(), pos);
            lines.add(player.getServer().getWorldData().generatorConfig().debugLine(EndRingsMode.isEnabled()));
            lines.add("BlendedNoise");
            lines.add(BlendedNoise.getDebugString());
            lines.add(BlendedNoise.getDebugString2());
            lines.add("ImprovedNoise");
            lines.add(ImprovedNoise.getDebugString());
            lines.add(ImprovedNoise.getDebugString2());
        } finally {
            ImprovedNoise.endDebugCapture();
            BlendedNoise.endDebugCapture();
        }
        SimplexNoise.beginDebugCapture();
        try {
            // Match the explicit low-64 worldgen seam, including its diagnostic sampling.
            Biome.BIOME_INFO_NOISE.getValue((double) pos.getXLo(), (double) pos.getZLo(), false);
            lines.add("SimplexNoise");
            lines.add(SimplexNoise.getDebugString());
            lines.add(SimplexNoise.getDebugString2());
        } finally {
            SimplexNoise.endDebugCapture();
        }
        FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.buffer());
        data.writeResourceLocation(player.getLevel().dimension().location());
        data.writeVarInt(lines.size());
        for (String line : lines) data.writeUtf(line);
        return new ClientboundCustomPayloadPacket(CHANNEL, data);
    }
}
