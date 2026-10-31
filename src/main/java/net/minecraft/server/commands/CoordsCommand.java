package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Int1088;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.util.CoordinateFormat;
import net.minecraft.world.level.chunk.storage.RegionPaths;
import net.minecraft.world.level.chunk.storage.RegionPos;
import net.minecraft.world.phys.SectorVec3;

public final class CoordsCommand {
    private CoordsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("coords")
                .executes(context -> show(context.getSource(), false))
                .then(Commands.literal("verbose").executes(context -> show(context.getSource(), true))));
    }

    private static int show(CommandSourceStack source, boolean verbose) {
        for (Component line : lines(source.getExactPosition(), verbose)) source.sendSuccess(line, false);
        return 1;
    }

    static List<Component> lines(SectorVec3 position, boolean verbose) {
        List<Component> lines = new ArrayList<>();
        Int1088 x = position.blockX();
        Int1088 z = position.blockZ();
        Int1088 regionX = x.shiftRight(9);
        Int1088 regionZ = z.shiftRight(9);
        lines.add(Component.literal("Click to copy any field"));
        lines.add(field("X: ", CoordinateFormat.coordinate(x, position.subX())));
        lines.add(field("Y: ", CoordinateFormat.y(position.y())));
        lines.add(field("Z: ", CoordinateFormat.coordinate(z, position.subZ())));
        lines.add(field("Region X: ", regionX.toDecimalString()));
        lines.add(field("Region Z: ", regionZ.toDecimalString()));
        if (verbose) {
            String path = RegionPaths.regionFile(Path.of(""), new RegionPos(regionX, regionZ)).toString();
            lines.add(field("Region Path: ", path));
        }
        lines.add(field("Chunk X: ", x.shiftRight(4).toDecimalString()));
        lines.add(field("Chunk Z: ", z.shiftRight(4).toDecimalString()));
        lines.add(Component.literal(CoordinateFormat.subPosition(position)));
        if (verbose) {
            lines.add(Component.literal("X Limbs:"));
            lines.add(limbs(x));
            lines.add(Component.literal("Z Limbs:"));
            lines.add(limbs(z));
        }
        return lines;
    }

    private static Component field(String label, String value) {
        return Component.literal(label + value).withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.copy.click"))));
    }

    // Show the actual used 64-bit limbs, without synthetic sign-extension limbs.
    static Component limbs(Int1088 value) {
        int count = value.limbCount();
        net.minecraft.network.chat.MutableComponent line = Component.literal(" ");
        for (int i = 0; i < count; i++) {
            if (i > 0) line.append(", ");
            line.append(field(i + "=", String.format(Locale.ROOT, "%016x", value.limb(i))));
        }
        return line;
    }
}
