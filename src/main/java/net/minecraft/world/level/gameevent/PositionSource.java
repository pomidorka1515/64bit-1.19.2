package net.minecraft.world.level.gameevent;

import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public interface PositionSource {
   Codec<PositionSource> CODEC = Registry.POSITION_SOURCE_TYPE.byNameCodec().dispatch(PositionSource::getType, PositionSourceType::codec);

   /** Legacy compatibility position. Precision-sensitive callers use exactPosition. */
   Optional<Vec3> getPosition(Level p_157870_);

   /** Exact world position where one is available; defaults to the legacy boundary. */
   default Optional<SectorVec3> exactPosition(Level level) {
      return this.getPosition(level).map(position -> SectorVec3.fromApproximate(position.x, position.y, position.z));
   }

   PositionSourceType<?> getType();
}