package net.minecraft.world.level.gameevent;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public interface PositionSource {
    Codec<PositionSource> CODEC = Registry.POSITION_SOURCE_TYPE.byNameCodec().dispatch(PositionSource::getType, PositionSourceType::codec);

    Optional<SectorVec3> exactPosition(Level level);

    PositionSourceType<?> getType();
}
