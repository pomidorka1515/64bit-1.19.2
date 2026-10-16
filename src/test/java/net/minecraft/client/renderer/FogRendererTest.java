package net.minecraft.client.renderer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FogRendererTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void netherBackgroundNeverBlendsInUnusedBlueSkyColor() {
        DimensionSpecialEffects effects = new DimensionSpecialEffects.NetherEffects();
        for (int distance : new int[] {2, 4, 8, 16, 32, 64}) {
            assertEquals(0.0F, FogRenderer.getSkyBlendFactor(effects, distance), "Render distance " + distance);
        }
        assertEquals(DimensionSpecialEffects.SkyType.NONE, effects.skyType());
    }

    @Test
    void dimensionsWithSkiesKeepTheirOriginalBlend() {
        for (DimensionSpecialEffects effects : new DimensionSpecialEffects[] {
                new DimensionSpecialEffects.OverworldEffects(), new DimensionSpecialEffects.EndEffects()}) {
            for (int distance : new int[] {2, 4, 8, 16, 32, 64}) {
                float original = 0.25F + 0.75F * (float) distance / 32.0F;
                original = 1.0F - (float) Math.pow((double) original, 0.25D);
                assertEquals(original, FogRenderer.getSkyBlendFactor(effects, distance), "Render distance " + distance);
            }
        }
    }
}
