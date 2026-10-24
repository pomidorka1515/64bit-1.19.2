package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.synth.CoordinatePrecision;
import net.minecraft.world.level.levelgen.synth.GeneratorConfig;
import net.minecraft.world.level.levelgen.synth.GeneratorMode;

public final class GeneratorCommand {
    private static final Map<Requester, Change> PENDING = new ConcurrentHashMap<>();

    private GeneratorCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var mode = Commands.literal("mode");
        for (GeneratorMode value : GeneratorMode.values()) {
            mode.then(Commands.literal(value.serializedName()).executes(context -> request(context.getSource(), new Change(value, null))));
        }
        mode.then(Commands.literal("help").executes(context -> {
            context.getSource().sendSuccess(Component.literal("Generator modes:\n32bit: original 32-bit arithmetic with Far Lands; terrain stops at ~16.8 billion blocks.\n64bit: 64-bit arithmetic with Far Lands; terrain stops at ~16.8 billion × 2^32 blocks.\n64bit-stable-shifts: 64-bit Far Lands with only shift_x/z overflow patched in both stacks; other noise limits remain.\n64bit-patched: patched arithmetic without Far Lands."), false);
            return 1;
        }));
        var precision = Commands.literal("precision");
        for (CoordinatePrecision value : CoordinatePrecision.values()) {
            precision.then(Commands.literal(value.serializedName()).executes(context -> request(context.getSource(), new Change(null, value))));
        }
        precision.then(Commands.literal("help").executes(context -> {
            context.getSource().sendSuccess(Component.literal("Coordinate precision:\nprecise: no terrain quantization/insane stretching at large distances.\nlossy: absolute-double coordinates with their rounding and precision loss."), false);
            return 1;
        }));
        dispatcher.register(Commands.literal("generator").requires(source -> source.hasPermission(3))
                .then(mode).then(precision).then(Commands.literal("confirm").executes(context -> confirm(context.getSource()))));
    }

    private static int request(CommandSourceStack source, Change change) {
        PENDING.put(Requester.of(source), change);
        source.sendSuccess(Component.literal(change.label() + " change to " + change.value() + " requested. Run /generator confirm."), false);
        return 1;
    }

    private static int confirm(CommandSourceStack source) {
        Change change = PENDING.remove(Requester.of(source));
        if (change == null) {
            source.sendFailure(Component.literal("No generator change is awaiting confirmation."));
            return 0;
        }
        var data = source.getServer().getWorldData();
        GeneratorConfig previous = data.generatorConfig();
        data.setGeneratorConfig(change.mode != null ? previous.withMode(change.mode) : previous.withPrecision(change.precision));
        try {
            if (!source.getServer().saveEverything(false, true, true)) throw new IllegalStateException("World save did not complete");
        } catch (RuntimeException exception) {
            data.setGeneratorConfig(previous);
            com.mojang.logging.LogUtils.getLogger().error("Could not save generator settings", exception);
            source.sendFailure(Component.literal("Could not save generator settings."));
            return 0;
        }
        source.sendSuccess(Component.literal(change.label() + " changed to " + change.value() + " and saved."), true);
        return 1;
    }

    public static void clear(MinecraftServer server) { PENDING.keySet().removeIf(key -> key.server == server); }
    public static void clear(MinecraftServer server, UUID player) { PENDING.keySet().removeIf(key -> key.server == server && player.equals(key.player)); }

    private record Change(GeneratorMode mode, CoordinatePrecision precision) {
        String label() { return this.mode != null ? "Generator mode" : "Coordinate precision"; }
        String value() { return this.mode != null ? this.mode.serializedName() : this.precision.serializedName(); }
    }

    private record Requester(MinecraftServer server, UUID player, String name) {
        static Requester of(CommandSourceStack source) {
            var player = source.getPlayer();
            return new Requester(source.getServer(), player == null ? null : player.getUUID(), player == null ? source.getTextName() : "");
        }
    }
}
