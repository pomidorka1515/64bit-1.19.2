package net.minecraft.commands.arguments.selector;

import com.mojang.brigadier.StringReader;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EntitySelectorCoordinateTest {
    @Test
    void wideSelectorsUseFiniteMirrorsAndRejectInvalidLiterals() {
        EntitySelectorParser parser = new EntitySelectorParser(new StringReader(""));
        String wide = BigInteger.ONE.shiftLeft(1024).add(BigInteger.ONE).toString();
        parser.setExactX(wide);
        parser.setExactZ("-" + wide);
        assertTrue(Double.isFinite(parser.getX()));
        assertTrue(Double.isFinite(parser.getZ()));
        assertNotNull(parser.getSelector());
        assertThrows(IllegalArgumentException.class, () -> parser.setExactX("NaN"));
        assertThrows(IllegalArgumentException.class, () -> parser.setExactZ(BigInteger.ONE.shiftLeft(1087).toString()));
    }
}
