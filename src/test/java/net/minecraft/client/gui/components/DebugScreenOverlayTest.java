package net.minecraft.client.gui.components;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Int1088;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.SectorVec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DebugScreenOverlayTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void exactPositionLinesMatchDecimalOracleThroughEveryLimbAndBothEdges() {
        List<BigInteger> blocks = new ArrayList<>(List.of(BigInteger.ZERO, BigInteger.valueOf(-1)));
        for (int bit = 64; bit <= 1024; bit += 64) {
            var value = BigInteger.ONE.shiftLeft(bit).add(BigInteger.ONE);
            blocks.add(value);
            blocks.add(value.negate());
        }
        blocks.add(BigInteger.ONE.shiftLeft(1087).subtract(BigInteger.ONE));
        blocks.add(BigInteger.ONE.shiftLeft(1087).negate());
        for (var block : blocks) {
            for (double fraction : new double[] {0, 0.0005, 0.25, 0.9995, Math.nextDown(1.0)}) {
                var coordinate = Int1088.parseDecimal(block.toString());
                var position = SectorVec3.fromBlockAndFraction(coordinate, fraction, 64.125, coordinate, fraction);
                var expected = new BigDecimal(block).add(BigDecimal.valueOf(fraction))
                        .setScale(3, RoundingMode.HALF_UP).toPlainString();
                List<String> lines = new ArrayList<>();
                DebugScreenOverlay.addExactPositionLines(lines, position);
                assertEquals(List.of("X: " + expected, "Y: 64.12500", "Z: " + expected), lines);
            }
        }
    }

    @Test
    void positionCacheDoesNotAliasUpperLimbsEvenWhenDoubleProjectionsMatch() {
        var base = BigInteger.ONE.shiftLeft(1024);
        var cache = new DebugScreenOverlay.PositionDisplayCache();
        for (var value : List.of(base, base.add(BigInteger.ONE), base.negate(), base.negate().subtract(BigInteger.ONE))) {
            var coordinate = Int1088.parseDecimal(value.toString());
            cache.update(SectorVec3.fromBlockAndFraction(coordinate, 0.25, 80, coordinate, 0.25));
            String expected = new BigDecimal(value).add(new BigDecimal("0.25")).setScale(3).toPlainString();
            assertEquals("X: " + expected, cache.xLine);
            assertEquals("Z: " + expected, cache.zLine);
            var rebuilt = Int1088.parseDecimal(value.toString());
            cache.update(SectorVec3.fromBlockAndFraction(rebuilt, 0.25, 80, rebuilt, 0.25));
            assertEquals("X: " + expected, cache.xLine);
        }
    }
}
