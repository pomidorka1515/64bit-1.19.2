package net.minecraft.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.Test;

class KeyboardHandlerTest {
    @Test
    void copyLocationPreservesCoordinatesBeyondDoubleAndLongPrecision() {
        SectorVec3 position = SectorVec3.fromDecimal("18446744073709551617.25", 64.5D,
                "-18446744073709551617.75");

        assertEquals("/execute in minecraft:overworld run tp @s 18446744073709551617.25 64.50 -18446744073709551617.75 90.00 -45.00",
                KeyboardHandler.formatDebugTeleportCommand(new ResourceLocation("minecraft:overworld"),
                        position, 90.0F, -45.0F));
    }

    @Test
    void copyLocationRetainsTwoDecimalFormatting() {
        SectorVec3 position = SectorVec3.fromDecimal("12.126", -3.125D, "-12.126");

        assertEquals("/execute in minecraft:the_nether run tp @s 12.13 -3.13 -12.13 1.25 -2.50",
                KeyboardHandler.formatDebugTeleportCommand(new ResourceLocation("minecraft:the_nether"),
                        position, 1.25F, -2.5F));
    }
}
