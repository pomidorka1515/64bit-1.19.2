package net.minecraft.world.level.levelgen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.material.MaterialRuleList;
import org.junit.jupiter.api.Test;

class MaterialRuleListTest {
    @Test
    void preservesOrderNullFallbackAndShortCircuit() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var context = new DensityFunction.SinglePointContext(0, 0, 0);
        int[] calls = {0};
        var rules = new MaterialRuleList(List.of(
                c -> { assertSame(context, c); assertEquals(0, calls[0]++); return null; },
                c -> { assertEquals(1, calls[0]++); return Blocks.STONE.defaultBlockState(); },
                c -> { fail("Rules after a match must not execute"); return null; }));
        assertEquals(Blocks.STONE.defaultBlockState(), rules.calculate(context));
        assertEquals(2, calls[0]);
        assertNull(new MaterialRuleList(List.of()).calculate(context));
        assertNull(new MaterialRuleList(List.of(c -> null, c -> null)).calculate(context));
    }
}
