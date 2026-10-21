package net.minecraft.world.level.levelgen.structure.pools;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.Int128;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import com.mojang.serialization.Dynamic;
import org.junit.jupiter.api.Test;

class JigsawJunctionInt128Test {
    @Test
    void fullWidthCoordinatesRoundTripAndHaveStructuralIdentity() {
        for (Int128 x : new Int128[] {Int128.MIN, Int128.MAX, Int128.of(42, -1), Int128.ZERO}) {
            JigsawJunction junction = new JigsawJunction(x, 64, Int128.of(-23, 4), 3, StructureTemplatePool.Projection.RIGID);
            JigsawJunction decoded = JigsawJunction.deserialize(junction.serialize(NbtOps.INSTANCE));
            assertEquals(junction, decoded);
            assertEquals(junction.hashCode(), decoded.hashCode());
            assertEquals(x, decoded.getSourceX());
            assertNotEquals(junction, new JigsawJunction(x, 65, decoded.getSourceZ(), 3, StructureTemplatePool.Projection.RIGID));
        }
        assertNotEquals(new JigsawJunction(Int128.of(1, 0), 64, Int128.ZERO, 0, StructureTemplatePool.Projection.RIGID),
                new JigsawJunction(Int128.ZERO, 64, Int128.ZERO, 0, StructureTemplatePool.Projection.RIGID));
    }

    @Test
    void lowOnlyAndCoercedCoordinateTagsAreRejected() {
        CompoundTag tag = (CompoundTag) new JigsawJunction(12, 64, -5, 0, StructureTemplatePool.Projection.RIGID)
                .serialize(NbtOps.INSTANCE).getValue();
        tag.remove("source_xHi");
        assertThrows(IllegalArgumentException.class, () -> JigsawJunction.deserialize(new Dynamic<>(NbtOps.INSTANCE, tag)));
        tag.putInt("source_xHi", 0);
        assertThrows(IllegalArgumentException.class, () -> JigsawJunction.deserialize(new Dynamic<>(NbtOps.INSTANCE, tag)));
    }
}
