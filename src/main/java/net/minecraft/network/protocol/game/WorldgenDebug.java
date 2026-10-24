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
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.EndRingsMode;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.GeneratorConfig;
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
        return sample(player.getLevel().dimension().location(), chunks.getGenerator(), chunks.randomState(), pos,
                player.getServer().getWorldData().generatorConfig());
    }

    static ClientboundCustomPayloadPacket sample(ResourceLocation dimension, ChunkGenerator generator, RandomState randomState,
                                                BlockPos pos, GeneratorConfig config) {
        List<String> lines = new ArrayList<>();
        BlendedNoise.beginDebugCapture();
        ImprovedNoise.beginDebugCapture();
        try (var ignored = net.minecraft.world.level.levelgen.synth.GenerationContext.open(config)) {
            // Include all generator F3 diagnostics in the same payload as the noise captures.
            generator.addDebugScreenInfo(lines, randomState, pos);
            lines.add(config.debugLine(EndRingsMode.isEnabled()));
            if (pos.getY() >= 256) {
                // The density router can skip blended noise at the upper slide boundary too.
                // Sample it directly for display only, retaining the player's full X/Z.
                var context = new DensityFunction.SinglePointContext(pos.getX(), 255, pos.getZ());
                randomState.router().finalDensity().mapAll(new DensityFunction.Visitor() {
                    public DensityFunction apply(DensityFunction function) {
                        if (function instanceof BlendedNoise noise) noise.compute(context);
                        return function;
                    }
                });
            }
            lines.add(BlendedNoise.getDebugString());
            lines.add(BlendedNoise.getDebugString2());
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
            lines.add(SimplexNoise.getDebugString());
            lines.add(SimplexNoise.getDebugString2());
        } finally {
            SimplexNoise.endDebugCapture();
        }
        FriendlyByteBuf data = new FriendlyByteBuf(Unpooled.buffer());
        data.writeResourceLocation(dimension);
        data.writeVarInt(lines.size());
        for (String line : lines) data.writeUtf(line);
        return new ClientboundCustomPayloadPacket(CHANNEL, data);
    }
}
