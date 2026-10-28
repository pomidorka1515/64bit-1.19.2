package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Random;
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
        assertEquals(1.0E-17, negativeCarry.getAbsoluteFraction());
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

    @Test
    void signedLimbBoundariesParseAndFormatWithoutDoubleIdentity() throws Exception {
        for (int bit : new int[] {0, 53, 63, 64, 127, 128, 1024, 1086}) {
            for (int sign : new int[] {-1, 1}) {
                for (int offset = -1; offset <= 1; offset++) {
                    BigInteger integer = BigInteger.ONE.shiftLeft(bit).multiply(BigInteger.valueOf(sign)).add(BigInteger.valueOf(offset));
                    assertSplit(integer.toString(), false);
                    assertSplit(integer.toString(), true);
                    assertSplit(integer + ".125", false);
                }
            }
        }
        for (int bit = 64; bit < 1087; bit += 64) {
            assertSplit(BigInteger.ONE.shiftLeft(bit) + ".25", false);
            assertSplit("-" + BigInteger.ONE.shiftLeft(bit) + ".75", false);
        }
        for (String text : new String[] {Int1088.MIN.toDecimalString(), Int1088.MAX.toDecimalString(),
                new BigDecimal(Double.MAX_VALUE).toBigIntegerExact().toString()}) {
            assertSplit(text, false);
            WorldCoordinate coordinate = WorldCoordinate.parseInt(new StringReader(text));
            assertEquals(text, coordinate.toString());
        }
    }

    @Test
    void fractionsRoundIndependentlyWithTiesStickyDigitsAndSubnormals() throws Exception {
        String halfway = new BigDecimal(0.5D).add(new BigDecimal(Math.ulp(0.5D)).divide(BigDecimal.valueOf(2))).toPlainString();
        for (String fraction : new String[] {".0", ".00000000000000001", ".99999999999999999",
                halfway.substring(1), halfway.substring(1) + "00001",
                "." + "0".repeat(323) + "5", "." + "0".repeat(400) + "1"}) {
            assertSplit("99" + fraction, false);
            assertSplit("-99" + fraction, false);
        }
        Random random = new Random(0x5811088L);
        for (int i = 0; i < 500; i++) {
            StringBuilder text = new StringBuilder(i % 2 == 0 ? "9007199254740993." : "-9007199254740993.");
            for (int digit = 0, count = 1 + random.nextInt(400); digit < count; digit++)
                text.append((char)('0' + random.nextInt(10)));
            assertSplit(text.toString(), false);
        }
    }

    @Test
    void malformedTokensRejectWithoutPartialConsumption() {
        for (String token : new String[] {"", " ", "+", "-", ".", "1..2", "1x", "0x1p2", "NaN", "Infinity", "~NaN", "~Infinity", "~0x1p2"}) {
            StringReader reader = new StringReader(token);
            assertThrows(CommandSyntaxException.class, () -> WorldCoordinate.parseDouble(reader, false), token);
            assertEquals(token.startsWith("~") ? 1 : 0, reader.getCursor(), token);
            assertThrows(CommandSyntaxException.class, () -> WorldCoordinate.parseInt(new StringReader(token)), token);
        }
    }

    @Test
    void localOffsetsAreFiniteAndKeepReaderBoundaries() throws Exception {
        assertTrue(WorldCoordinate.parseInt(new StringReader("~.5")).isRelative());
        assertEquals(0.5D, WorldCoordinate.parseInt(new StringReader("~.5")).getValue());
        assertEquals(0.0D, WorldCoordinate.parseDouble(new StringReader("~"), true).getValue());
        assertEquals("~", WorldCoordinate.parseDouble(new StringReader("~"), false).toString());
        StringReader reader = new StringReader("9007199254740993.25 next");
        WorldCoordinate coordinate = WorldCoordinate.parseDouble(reader, false);
        assertEquals(" next", reader.getRemaining());
        assertEquals("9007199254740993.25", coordinate.toString());
        String tooLarge = BigInteger.ONE.shiftLeft(1024).toString();
        assertTrue(WorldCoordinate.parseDouble(new StringReader("~" + tooLarge), false).isRelative());
        assertTrue(WorldCoordinate.parseInt(new StringReader("~" + tooLarge)).isRelative());
        assertThrows(NumberFormatException.class, () -> new WorldCoordinate(true, Double.POSITIVE_INFINITY));
    }

    private static void assertSplit(String text, boolean center) throws Exception {
        BigDecimal expected = new BigDecimal(text);
        if (center && !text.contains(".")) expected = expected.add(new BigDecimal("0.5"));
        BigInteger block = expected.setScale(0, RoundingMode.FLOOR).toBigIntegerExact();
        double fraction = expected.subtract(new BigDecimal(block)).doubleValue();
        if (fraction == 1.0D) {
            if (expected.signum() < 0 || block.equals(new BigInteger(Int1088.MAX.toDecimalString()))) fraction = Math.nextDown(1.0D);
            else {
                block = block.add(BigInteger.ONE);
                fraction = 0.0D;
            }
        }
        WorldCoordinate actual = WorldCoordinate.parseDouble(new StringReader(text), center);
        assertEquals(block.toString(), actual.getAbsoluteBlock().toDecimalString(), text);
        assertEquals(fraction, actual.getAbsoluteFraction(), text);
        WorldCoordinate formatted = WorldCoordinate.parseDouble(new StringReader(actual.toString()), false);
        assertEquals(actual, formatted, text);
        assertEquals(actual.hashCode(), formatted.hashCode(), text);
    }
}
