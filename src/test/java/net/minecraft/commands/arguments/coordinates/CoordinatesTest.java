package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.math.BigInteger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Int1088;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoordinatesTest {
    @Test
    void absoluteRelativeAndTwoDimensionalArgumentsKeepExactOrigins() throws Exception {
        for (Int1088 axis : axes()) {
            CommandSourceStack source = source(axis);
            Coordinates relative = Vec3Argument.vec3(false).parse(new StringReader("~.5 ~1.25 ~-.5"));
            SectorVec3 result = relative.getExactPosition(source);
            assertEquals(axis, result.blockX());
            assertEquals(0.75D, result.subX());
            assertEquals(65.75D, result.y());
            assertEquals(axis, result.blockZ());
            assertEquals(0.25D, result.subZ());
            assertEquals(result.blockPosition(), relative.getBlockPos(source));

            String xText = axis + (axis.equals(Int1088.MIN) ? "" : ".25");
            String zText = axis + (axis.equals(Int1088.MIN) ? "" : ".75");
            Coordinates absolute = Vec3Argument.vec3(false).parse(new StringReader(xText + " 100 " + zText));
            SectorVec3 exact = absolute.getExactPosition(source);
            WorldCoordinate x = WorldCoordinate.absoluteDecimal(xText, false);
            WorldCoordinate z = WorldCoordinate.absoluteDecimal(zText, false);
            assertEquals(x.getAbsoluteBlock(), exact.blockX());
            assertEquals(x.getAbsoluteFraction(), exact.subX());
            assertEquals(z.getAbsoluteBlock(), exact.blockZ());
            assertEquals(z.getAbsoluteFraction(), exact.subZ());
            assertEquals(100.0D, exact.y());
            assertFalse(absolute.isXRelative());

            SectorVec3 two = Vec2Argument.vec2().parse(new StringReader(axis + " ~")).getExactPosition(source);
            assertEquals(axis, two.blockX());
            assertEquals(0.5D, two.subX());
            assertEquals(source.getExactPosition().y(), two.y());
            assertEquals(source.getExactPosition().blockZ(), two.blockZ());
            assertEquals(source.getExactPosition().subZ(), two.subZ());
        }
    }

    @Test
    void relativeCarrySaturatesAndNeverAliasesTheOppositeEdge() throws Exception {
        for (Int1088 axis : axes()) {
            SectorVec3 plus = WorldCoordinates.parseDouble(new StringReader("~1 ~ ~-1"), false).getExactPosition(source(axis));
            BigInteger integer = new BigInteger(axis.toDecimalString());
            BigInteger min = BigInteger.ONE.shiftLeft(1087).negate();
            BigInteger max = min.negate().subtract(BigInteger.ONE);
            assertEquals(integer.add(BigInteger.ONE).min(max).toString(), plus.blockX().toDecimalString());
            assertEquals(integer.subtract(BigInteger.ONE).max(min).toString(), plus.blockZ().toDecimalString());
        }
    }

    @Test
    void localRotationAddsOnlyLocalDeltasToExactAnchor() throws Exception {
        Coordinates local = Vec3Argument.vec3().parse(new StringReader("^1 ^2 ^3"));
        SectorVec3 originResult = local.getExactPosition(source(Int1088.ZERO));
        for (Int1088 axis : axes()) {
            // Avoid the intentional clipping of outward offsets at the domain edges.
            if (axis.equals(Int1088.MIN) || axis.equals(Int1088.MAX)) continue;
            SectorVec3 result = local.getExactPosition(source(axis));
            assertEquals(axis.addSaturated(originResult.blockX()), result.blockX());
            assertEquals(axis.addSaturated(originResult.blockZ()), result.blockZ());
            assertEquals(originResult.subX(), result.subX());
            assertEquals(originResult.subZ(), result.subZ());
            assertEquals(originResult.y(), result.y());
        }
        assertTrue(local.isXRelative());
        assertTrue(local.isYRelative());
        assertTrue(local.isZRelative());
    }

    @Test
    void incompleteMixedAndMalformedTriplesReject() {
        for (String text : new String[] {"", "1", "1 2", "1  2", " 1 2", "1 2 ", "^ ^", "^ ^ ~", "~ ^ ~", "^NaN ^ ^", "^Infinity ^ ^", "^1x ^ ^", "^1e2 ^ ^", "^" + "9".repeat(400) + " ^ ^"})
            assertThrows(CommandSyntaxException.class, () -> Vec3Argument.vec3().parse(new StringReader(text)), text);
        assertThrows(CommandSyntaxException.class, () -> Vec2Argument.vec2().parse(new StringReader("1 ")));
        assertThrows(CommandSyntaxException.class, () -> WorldCoordinates.parseInt(new StringReader("1.5 2 3")));
    }

    private static Int1088[] axes() {
        return new Int1088[] {Int1088.ZERO, Int1088.fromLong(-1), Int1088.parseDecimal("9007199254740993"),
                Int1088.ONE.shiftLeftSaturated(127), Int1088.ONE.shiftLeftSaturated(1024),
                Int1088.ONE.shiftLeftSaturated(1024).negate(), Int1088.MIN, Int1088.MAX};
    }

    private static CommandSourceStack source(Int1088 axis) {
        return new CommandSourceStack(null, Vec3.ZERO, Vec2.ZERO, null, 0, "test", Component.literal("test"), null, null)
                .withExactPosition(SectorVec3.fromBlockAndFraction(axis, 0.25D, 64.5D, axis, 0.75D));
    }
}
