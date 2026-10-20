package net.minecraft.world.level.chunk;

import javax.annotation.Nullable;
import net.minecraft.core.Int128;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LightLayer;

public interface LightChunkGetter {
    @Nullable
    BlockGetter getChunkForLighting(Int128 pChunkX, Int128 pChunkZ);

    @Nullable
    default BlockGetter getChunkForLighting(long pChunkX, long pChunkZ) {
        return this.getChunkForLighting(Int128.fromLong(pChunkX), Int128.fromLong(pChunkZ));
    }

    default void onLightUpdate(LightLayer pLayer, SectionPos pPos) {
    }

    BlockGetter getLevel();
}
