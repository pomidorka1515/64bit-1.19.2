package net.minecraft.server.commands;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.Int1088;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.storage.RegionPaths;
import net.minecraft.world.level.chunk.storage.RegionPos;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoordsCommandTest {
    @Test
    void defaultOutputAndClipboardContainExactValues() {
        SectorVec3 position = SectorVec3.fromBlockAndFraction(Int1088.MAX, 0.125, 5.5444, Int1088.MIN, 0.25);
        List<Component> lines = CoordsCommand.lines(position, false);
        assertEquals(9, lines.size());
        assertEquals("Click to copy any field", lines.get(0).getString());
        assertEquals("X: " + Int1088.MAX + ".125", lines.get(1).getString());
        assertEquals("Y: 5.54440", lines.get(2).getString());
        assertEquals("Region X: " + Int1088.MAX.shiftRight(9), lines.get(4).getString());
        assertEquals("Chunk Z: " + Int1088.MIN.shiftRight(4), lines.get(7).getString());
        for (int i = 1; i <= 7; i++) {
            ClickEvent click = lines.get(i).getStyle().getClickEvent();
            assertNotNull(click);
            assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD, click.getAction());
            assertEquals(lines.get(i).getString().split(": ", 2)[1], click.getValue());
        }
        assertEquals("SubPos [X: +0.1250000, Y: +0.5444000, Z: +0.2500000]", lines.get(8).getString());
        assertNull(lines.get(8).getStyle().getClickEvent());
    }

    @Test
    void verboseIncludesUnshortenedPathAndUsedWords() {
        List<Component> lines = CoordsCommand.lines(SectorVec3.fromBlockAndFraction(-1, 0.5, 0, 0, 0), true);
        assertEquals(14, lines.size());
        String path = RegionPaths.regionFile(Path.of(""), new RegionPos(Int1088.fromLong(-1), Int1088.ZERO)).toString();
        assertEquals("Region Path: " + path, lines.get(6).getString());
        assertEquals(path, lines.get(6).getStyle().getClickEvent().getValue());
        assertEquals("X Limbs:", lines.get(10).getString());
        assertEquals(" 0=ffffffffffffffff", lines.get(11).getString());
        assertEquals("Z Limbs:", lines.get(12).getString());
        assertEquals(" 0=0000000000000000", lines.get(13).getString());
        assertEquals(" 0=0000000100000000", CoordsCommand.limbs(Int1088.ONE.shiftLeftSaturated(32)).getString());
        assertEquals(" 0=0123456789abcdef, 1=123456789abcdef0",
                CoordsCommand.limbs(Int1088.of(0x0123456789abcdefL, 0x123456789abcdef0L)).getString());
        for (Int1088 endpoint : new Int1088[] {Int1088.MIN, Int1088.MAX}) {
            String[] words = CoordsCommand.limbs(endpoint).getString().trim().split(", ");
            assertEquals(17, words.length);
            for (int i = 0; i < words.length; i++) {
                assertTrue(words[i].matches(i + "=[0-9a-f]{16}"));
                assertEquals(endpoint.limb(i), Long.parseUnsignedLong(words[i].split("=")[1], 16));
            }
        }
    }
}
