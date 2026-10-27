package net.minecraft.world.level.levelgen.structure.pools;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.core.Int1088;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import com.mojang.serialization.Dynamic;
import org.junit.jupiter.api.Test;

class JigsawJunctionInt1088Test {
    @Test
    void fullWidthCoordinatesRoundTripAndHaveStructuralIdentity() {
        for (Int1088 x : new Int1088[] {Int1088.MIN, Int1088.MAX, Int1088.of(-1, 42), Int1088.ZERO}) {
            JigsawJunction junction = new JigsawJunction(x, 64, Int1088.of(4, -23), 3, StructureTemplatePool.Projection.RIGID);
            JigsawJunction decoded = JigsawJunction.deserialize(junction.serialize(NbtOps.INSTANCE));
            assertEquals(junction, decoded);
            assertEquals(junction.hashCode(), decoded.hashCode());
            assertEquals(x, decoded.getSourceX());
            assertNotEquals(junction, new JigsawJunction(x, 65, decoded.getSourceZ(), 3, StructureTemplatePool.Projection.RIGID));
        }
        assertNotEquals(new JigsawJunction(Int1088.of(0, 1), 64, Int1088.ZERO, 0, StructureTemplatePool.Projection.RIGID),
                new JigsawJunction(Int1088.ZERO, 64, Int1088.ZERO, 0, StructureTemplatePool.Projection.RIGID));
    }

    @Test
    void lowOnlyAndCoercedCoordinateTagsAreRejected() {
        CompoundTag tag = (CompoundTag) new JigsawJunction(12, 64, -5, 0, StructureTemplatePool.Projection.RIGID)
                .serialize(NbtOps.INSTANCE).getValue();
        tag.remove("source_x");
        assertThrows(IllegalArgumentException.class, () -> JigsawJunction.deserialize(new Dynamic<>(NbtOps.INSTANCE, tag)));
        tag.putInt("source_x", 0);
        assertThrows(IllegalArgumentException.class, () -> JigsawJunction.deserialize(new Dynamic<>(NbtOps.INSTANCE, tag)));
    }
}
