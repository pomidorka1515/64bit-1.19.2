package net.minecraft.world.level.levelgen.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Int1088;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoundingBox1088Test {
    @Test
    void fullWidthEndpointsRoundTripAndKeepLocalGeometry() {
        Int1088 x = Int1088.ONE.shiftLeftSaturated(1024);
        Int1088 z = x.negate();
        BoundingBox box = new BoundingBox(x, 4, z, x.addSaturated(Int1088.fromLong(6)), 8,
                z.addSaturated(Int1088.fromLong(3)));
        assertEquals(new AABB(0, 0, 0, 7, 5, 4), box.toLocalAABB(new BlockPos(x, 4, z)));
        var encoded = BoundingBox.CODEC.encodeStart(NbtOps.INSTANCE, box).result().orElseThrow();
        assertEquals(box, BoundingBox.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow());
        assertTrue(BoundingBox.infinite().isInside(new BlockPos(Int1088.MIN, 0, Int1088.MAX)));
    }

    @Test
    void junctionPersistsAllLimbs() {
        JigsawJunction junction = new JigsawJunction(Int1088.MAX, 64, Int1088.MIN, 2,
                StructureTemplatePool.Projection.RIGID);
        assertEquals(junction, JigsawJunction.deserialize(junction.serialize(NbtOps.INSTANCE)));
    }
}
