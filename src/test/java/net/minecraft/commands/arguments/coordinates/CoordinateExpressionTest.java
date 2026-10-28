package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import net.minecraft.commands.CommandRuntimeException;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.core.Int1088;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoordinateExpressionTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static void result(String expected, String expression) {
        assertEquals(CoordinateExpression.parse(expected), CoordinateExpression.parse(expression), expression);
    }

    @Test
    void arithmeticIsExactAndRespectsPrecedence() {
        result("1", "(2^1099+1-2^1099)");
        result("1", "(1/3)*3");
        result("0", "1/3+1/3+1/3-1");
        result("14", "2+3*4");
        result("-10", "-(5+5)");
        result("-4", "-2^2");
        result("4", "(-2)^2");
        result("512", "2^3^2");
        result("0.125", "2^-3");
        result("-3", "-5//2");
        result("-3", "5//-2");
        result("2", "-5//-2");
        result("16", "2^^3");
        result("16", "2^^+3");
        result("1", "2^^-(0)");
        result("65536", "2^^4");
        result("1", "5^^0");
        result("65536", "2^^2^^2");
        result("256", "2^2^^3/256");
        result("6", "2^^3^2-250");
        result("1", "0^^0");
        result("0", "0^^3");
        result("1", "0^^4");
        result("1", "1^^999999999999999999999999999999999999");
        result("11", "(10^1000+(1+1*(10)))-10^1000");
    }

    @Test
    void scientificNotationIsAnExactStandaloneLiteral() throws Exception {
        for (String text : new String[] {"5.123456e+306", "-223.293e302", "1E3", "+1e+3", "1e-3", "-.125E2"}) {
            var decimal = new BigDecimal(text);
            assertEquals(CoordinateExpression.parse(decimal.toPlainString()), CoordinateExpression.parse(text));
            WorldCoordinate.parseDouble(new StringReader(text), false);
        }
        assertEquals(Int1088.fromLong(1000), WorldCoordinate.parseInt(new StringReader("1e3")).getAbsoluteBlock());
        result("1", "1.00000000000000000000000000000000000000000001-0.00000000000000000000000000000000000000000001");
    }

    @Test
    void malformedAndUnsupportedExpressionsReject() {
        for (String text : new String[] {"", "1(10)", "(1)(2)", "1e3+2", "(1e3)", "2*1E3", "1 e3", "1+ 2", "2^", "2^^", "()", "(1", "1)", "1..2", "1/0", "1//0", "0^-1", "2^0.5", "2^^-1", "2^^1.5", "NaN", "Infinity", "0x1p2"})
            assertThrows(NumberFormatException.class, () -> CoordinateExpression.parse(text), text);
    }

    @Test
    void resourceLimitsRejectBeforeExpensiveEvaluation() {
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            for (String text : new String[] {"2^^5", "2^999999999999999999999999999999", "2^^99999999999999999", "1e2147483647", "1e-2147483647", "(".repeat(1000) + "1" + ")".repeat(1000), "-".repeat(1000) + "1", "1+".repeat(1100) + "1", "1".repeat(4097)})
                assertThrows(NumberFormatException.class, () -> CoordinateExpression.parse(text), text);
        });
    }

    @Test
    void finalBoundsAreCheckedDuringBrigadierParsingNotIntermediateEvaluation() throws Exception {
        assertEquals(Int1088.ONE, WorldCoordinate.parseDouble(new StringReader("(2^1099+1-2^1099)"), false).getAbsoluteBlock());
        assertEquals(Int1088.MIN, WorldCoordinate.parseInt(new StringReader("-2^1087")).getAbsoluteBlock());
        assertEquals(Int1088.MAX, WorldCoordinate.parseInt(new StringReader("2^1087-1")).getAbsoluteBlock());
        for (String text : new String[] {"2^1088+1", "2^1087", "-2^1087-1", "-2^1087-1/3", "1e400"})
            assertThrows(CommandSyntaxException.class, () -> WorldCoordinate.parseDouble(new StringReader(text), false));

        CommandDispatcher<Object> dispatcher = new CommandDispatcher<>();
        dispatcher.register(LiteralArgumentBuilder.<Object>literal("tp").then(
                RequiredArgumentBuilder.<Object, Coordinates>argument("pos", Vec3Argument.vec3()).executes(context -> 1)));
        var invalid = dispatcher.parse("tp 2^1088+1 0 0", new Object());
        assertFalse(invalid.getExceptions().isEmpty(), "The chat parser must see an error before execution");
        assertEquals(1, dispatcher.execute("tp (2^1099+1-2^1099) 0 2^^3", new Object()));
    }

    @Test
    void horizontalEntryPointsShareEvaluatorButYAndLocalCoordinatesStayUnchanged() throws Exception {
        WorldCoordinates.parseDouble(new StringReader("2^10 0 1E3"), false);
        WorldCoordinates.parseInt(new StringReader("2^10 0 1E3"));
        Vec2Argument.vec2().parse(new StringReader("2^10 1E3"));
        ColumnPosArgument.columnPos().parse(new StringReader("2^10 1E3"));
        new EntitySelectorParser(new StringReader("@e[x=2^10,z=1E3,dx=(1/3)*3,dz=2^^3]")).parse();
        for (String selector : new String[] {"@e[x=2^1088]", "@e[z=1e3+2]", "@e[dx=1(2)]", "@e[dz=1/0]", "@e[x=1 2]", "@e[z=~1]"})
            assertThrows(CommandSyntaxException.class, () -> new EntitySelectorParser(new StringReader(selector)).parse());
        assertThrows(CommandSyntaxException.class, () -> WorldCoordinates.parseDouble(new StringReader("0 2^3 0"), false));
        assertThrows(CommandSyntaxException.class, () -> RotationArgument.rotation().parse(new StringReader("2^3 0")));
        assertThrows(CommandSyntaxException.class, () -> Vec3Argument.vec3().parse(new StringReader("^2^3 ^ ^")));
    }

    @Test
    void relativeOffsetsKeepLowBitsAndRejectResolvedOverflow() throws Exception {
        var offset = WorldCoordinate.parseDouble(new StringReader("~(2^1024+1)"), false);
        var resolved = offset.resolve(Int1088.ONE.shiftLeftSaturated(1024).negate(), 0.25D);
        assertEquals(Int1088.ONE, resolved.block());
        assertEquals(0.25D, resolved.fraction());
        assertNotEquals(offset, WorldCoordinate.parseDouble(new StringReader("~2^1024"), false));
        assertEquals(Int1088.ZERO, WorldCoordinate.parseDouble(new StringReader("~2^1087"), false).resolve(Int1088.MIN, 0.0D).block());
        assertEquals(Int1088.MAX, WorldCoordinate.parseDouble(new StringReader("~(2^1088-1)"), false).resolve(Int1088.MIN, 0.0D).block());
        assertThrows(CommandRuntimeException.class, () -> WorldCoordinate.parseDouble(new StringReader("~1"), false).resolve(Int1088.MAX, 0.0D));
        var fractional = WorldCoordinate.parseDouble(new StringReader("~1/3"), false).resolve(Int1088.ZERO, 0.0D);
        assertEquals(1.0D / 3.0D, fractional.fraction());
    }

    @Test
    void rationalFractionsAreCorrectlyRoundedEvenPastDecimalPrecision() {
        var half = CoordinateExpression.parse("0.5");
        var tie = CoordinateExpression.parse("0.5+2^-54");
        assertEquals(0.5D, tie.split().fraction());
        assertEquals(Math.nextUp(0.5D), tie.add(CoordinateExpression.parse("2^-2000")).split().fraction());
        assertEquals(half, CoordinateExpression.parse("1/2"));
        assertEquals(Double.MIN_VALUE, CoordinateExpression.parse("2^-1074").split().fraction());
        assertEquals(0.0D, CoordinateExpression.parse("2^-1075").split().fraction());
        assertEquals(Double.MIN_VALUE, CoordinateExpression.parse("2^-1075+2^-2000").split().fraction());
        assertEquals(BigInteger.ONE.shiftLeft(1000), CoordinateExpression.parse("2^1000").numerator());
    }
}
