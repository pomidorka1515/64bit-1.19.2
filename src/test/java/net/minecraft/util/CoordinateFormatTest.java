package net.minecraft.util;

import net.minecraft.core.Int1088;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoordinateFormatTest {
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
