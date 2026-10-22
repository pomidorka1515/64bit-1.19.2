package net.minecraft.client.gui.components;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DebugPrecisionDisplayTest {
    @Test
    void formatsPrecisionWithoutColorsAndWithRequestedDigits() {
        assertEquals("Precision double: 1.1231231234e12 float: 1.123123e22",
                DebugScreenOverlay.formatPrecision(1.1231231234e12, 1.123123e22));
    }

    @Test
    void formatsSmallAndZeroExponentsWithoutPadding() {
        assertEquals("Precision double: 1.2500000000e-3 float: 1.000000e0",
                DebugScreenOverlay.formatPrecision(0.00125, 1.0));
    }
}
