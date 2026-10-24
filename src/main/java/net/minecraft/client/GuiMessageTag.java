package net.minecraft.client;

import javax.annotation.Nullable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record GuiMessageTag(int indicatorColor, @Nullable String logTag) {
    private static final GuiMessageTag SYSTEM = new GuiMessageTag(10526880, "System");

    public static GuiMessageTag system() {
        return SYSTEM;
    }
}
