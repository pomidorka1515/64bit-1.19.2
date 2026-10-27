package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.Int1088;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldCoordinateInt1088Test {
    @Test
    void integerEndpointsAndHighHalvesRemainExact() throws Exception {
        for (Int1088 value : new Int1088[] {Int1088.MIN, Int1088.MAX, Int1088.parseDecimal("18446744073709551617"), Int1088.ONE.shiftLeftSaturated(1024)}) {
            assertEquals(value, WorldCoordinate.parseInt(new StringReader(value.toString())).getAbsoluteBlock());
        }
        assertNotEquals(WorldCoordinate.absoluteDecimal("18446744073709551616", false),
                WorldCoordinate.absoluteDecimal("18446744073709551617", false));
    }

    @Test
    void decimalCenteringAndNegativeFloorRemainExact() throws Exception {
        WorldCoordinate centered = WorldCoordinate.parseDouble(new StringReader(Int1088.MAX.toString()), true);
        assertEquals(Int1088.MAX, centered.getAbsoluteBlock());
        assertEquals(Int1088.MAX + ".5", centered.getLiteral());
        assertEquals(Int1088.fromLong(-2), WorldCoordinate.absoluteDecimal("-1.25", false).getAbsoluteBlock());
        assertEquals(WorldCoordinate.absoluteDecimal("1.00", false).hashCode(),
                WorldCoordinate.absoluteDecimal("1", false).hashCode());
        assertEquals(WorldCoordinate.absoluteDecimal("1.00", false), WorldCoordinate.absoluteDecimal("1", false));
        WorldCoordinate negativeCentered = WorldCoordinate.parseDouble(new StringReader("-2"), true);
        assertEquals("-1.5", negativeCentered.getLiteral());
        assertEquals(Int1088.fromLong(-2), negativeCentered.getAbsoluteBlock());
        assertEquals(0.5, negativeCentered.getAbsoluteFraction());
        WorldCoordinate negative = WorldCoordinate.absoluteDecimal("-1.25", false);
        assertEquals(0.75, negative.getAbsoluteFraction());
        assertEquals(1.0E-16, WorldCoordinate.absoluteDecimal("-0.9999999999999999", false).getAbsoluteFraction());
        WorldCoordinate carry = WorldCoordinate.absoluteDecimal("99.99999999999999999", false);
        assertEquals(Int1088.fromLong(100), carry.getAbsoluteBlock());
        assertEquals(0.0, carry.getAbsoluteFraction());
        WorldCoordinate negativeCarry = WorldCoordinate.absoluteDecimal("-99.99999999999999999", false);
        assertEquals(Int1088.fromLong(-100), negativeCarry.getAbsoluteBlock());
        assertEquals(0.0, negativeCarry.getAbsoluteFraction());
        WorldCoordinate tinyNegative = WorldCoordinate.absoluteDecimal("-0." + "0".repeat(400) + "1", false);
        assertEquals(Int1088.fromLong(-1), tinyNegative.getAbsoluteBlock());
        assertEquals(Math.nextDown(1.0), tinyNegative.getAbsoluteFraction());
    }

    @Test
    void invalidCoordinatesFailDuringParsing() throws CommandSyntaxException {
        for (String value : new String[] {java.math.BigInteger.ONE.shiftLeft(1087).toString(),
                java.math.BigInteger.ONE.shiftLeft(1087).negate().subtract(java.math.BigInteger.ONE).toString(), "~NaN", "~Infinity"}) {
            assertThrows(CommandSyntaxException.class, () -> WorldCoordinate.parseInt(new StringReader(value)));
            assertThrows(CommandSyntaxException.class, () -> WorldCoordinate.parseDouble(new StringReader(value), false));
        }
        assertThrows(CommandSyntaxException.class, () -> WorldCoordinate.parseDouble(new StringReader(Int1088.MIN + ".0000000000000000000000001"), false));
        assertThrows(CommandSyntaxException.class, () -> WorldCoordinate.parseDouble(new StringReader(Int1088.MIN + "." + "0".repeat(400) + "1"), false));
        assertEquals(Int1088.MIN, WorldCoordinate.absoluteDecimal(Int1088.MIN + ".0", false).getAbsoluteBlock());
        assertEquals(Int1088.MAX, WorldCoordinate.absoluteDecimal(Int1088.MAX + ".99999999999999999", false).getAbsoluteBlock());
        assertEquals(Int1088.parseDecimal("170141183460469231731687303715884105728"),
                WorldCoordinate.parseInt(new StringReader("170141183460469231731687303715884105728")).getAbsoluteBlock());
    }
}
