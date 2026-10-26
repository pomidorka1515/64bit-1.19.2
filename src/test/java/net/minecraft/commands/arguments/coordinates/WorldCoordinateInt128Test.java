package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.Int128;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldCoordinateInt128Test {
    @Test
    void integerEndpointsAndHighHalvesRemainExact() throws Exception {
        for (Int128 value : new Int128[] {Int128.MIN, Int128.MAX, Int128.parseDecimal("18446744073709551617")}) {
            assertEquals(value, WorldCoordinate.parseInt(new StringReader(value.toString())).getAbsoluteBlock());
        }
        assertNotEquals(WorldCoordinate.absoluteDecimal("18446744073709551616", false),
                WorldCoordinate.absoluteDecimal("18446744073709551617", false));
    }

    @Test
    void decimalCenteringAndNegativeFloorRemainExact() throws Exception {
        WorldCoordinate centered = WorldCoordinate.parseDouble(new StringReader(Int128.MAX.toString()), true);
        assertEquals(Int128.MAX, centered.getAbsoluteBlock());
        assertEquals(Int128.MAX + ".5", centered.getLiteral());
        assertEquals(Int128.fromLong(-2), WorldCoordinate.absoluteDecimal("-1.25", false).getAbsoluteBlock());
        assertEquals(WorldCoordinate.absoluteDecimal("1.00", false).hashCode(),
                WorldCoordinate.absoluteDecimal("1", false).hashCode());
    }

    @Test
    void invalidCoordinatesFailDuringParsing() {
        for (String value : new String[] {"170141183460469231731687303715884105728",
                "-170141183460469231731687303715884105729", "~NaN", "~Infinity"}) {
            assertThrows(CommandSyntaxException.class, () -> WorldCoordinate.parseInt(new StringReader(value)));
            assertThrows(CommandSyntaxException.class, () -> WorldCoordinate.parseDouble(new StringReader(value), false));
        }
    }
}
