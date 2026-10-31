package net.minecraft.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import net.minecraft.core.Int1088;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoordinateFormatTest {
    @Test
    void exactDisplayFormattingRetainsEveryCoordinateDigit() {
        for (Int1088 block : new Int1088[]{Int1088.MIN, Int1088.MAX,
                Int1088.ONE.shiftLeftSaturated(512), Int1088.fromLong(-1), Int1088.ZERO}) {
            for (double fraction : new double[]{0, 0.0005, 0.5, 0.9995, Math.nextDown(1.0)}) {
                String expected = new BigDecimal(block.toDecimalString()).add(BigDecimal.valueOf(fraction))
                        .setScale(3, RoundingMode.HALF_UP).toPlainString();
                assertEquals(expected, CoordinateFormat.coordinate(block, fraction));
            }
        }
    }

    @Test
    void cutoffIsStrictAndSymmetric() {
        Int1088 limit = Int1088.ONE.shiftLeftSaturated(127);
        assertFalse(CoordinateFormat.abbreviated(limit));
        assertFalse(CoordinateFormat.abbreviated(limit.negate()));
        assertFalse(CoordinateFormat.abbreviated(limit.negate(), 0.5));
        assertTrue(CoordinateFormat.abbreviated(limit, 0.5));
        assertTrue(CoordinateFormat.abbreviated(limit.addSaturated(Int1088.ONE)));
        assertTrue(CoordinateFormat.abbreviated(limit.negate().subtractSaturated(Int1088.ONE), 0.5));
        assertEquals(limit.toDecimalString(), CoordinateFormat.overlayInteger(limit));
    }

    @Test
    void scientificHasSevenDecimalPlacesAndSupportsBeyondDoubleRange() {
        assertEquals("1.7014118e38", CoordinateFormat.overlayInteger(Int1088.ONE.shiftLeftSaturated(127).addSaturated(Int1088.ONE)));
        assertEquals("5.1231234e40", CoordinateFormat.overlayInteger(Int1088.parseDecimal("51231234000000000000000000000000000000000")));
        assertTrue(CoordinateFormat.overlayInteger(Int1088.MAX).matches("[1-9]\\.[0-9]{7}e[0-9]+"));
        assertTrue(CoordinateFormat.overlayInteger(Int1088.MIN).matches("-[1-9]\\.[0-9]{7}e[0-9]+"));
    }
}
