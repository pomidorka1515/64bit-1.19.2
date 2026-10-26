package net.minecraft.world.phys.shapes;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationPoolTest {
    @Test
    void reusedTranslationsMatchImmutableMovesAndInvalidateFaces() {
        VoxelShape.TranslationPool pool = new VoxelShape.TranslationPool();
        VoxelShape previous = null;
        for (VoxelShape source : new VoxelShape[] {Shapes.block(),
                Shapes.box(0.125, 0.25, 0.375, 0.875, 0.75, 1),
                Shapes.or(Shapes.box(0, 0, 0, 1, 0.25, 1), Shapes.box(0, 0, 0, 0.25, 1, 1))}) {
            VoxelShape actual = pool.move(source, 0, 0, 0);
            if (previous != null) assertSame(previous, actual);
            VoxelShape expected = source.move(0, 0, 0);
            assertEquals(expected.toAabbs(), actual.toAabbs());
            for (Direction direction : Direction.values()) {
                assertEquals(expected.getFaceShape(direction).toAabbs(), actual.getFaceShape(direction).toAabbs());
            }
            VoxelShape shifted = pool.move(source, -2.5, 3.25, 4.5);
            VoxelShape expectedShifted = source.move(-2.5, 3.25, 4.5);
            assertEquals(expectedShifted.toAabbs(), shifted.toAabbs());
            AABB box = new AABB(-4, 3.5, 4.75, -3, 4, 5);
            for (Direction.Axis axis : Direction.Axis.values()) {
                assertEquals(expectedShifted.collide(axis, box, 5), shifted.collide(axis, box, 5));
                assertEquals(expectedShifted.collide(axis, box, -5), shifted.collide(axis, box, -5));
            }
            assertTrue(pool.move(Shapes.empty(), 1, 2, 3).isEmpty());
            previous = actual;
            pool.clear();
        }
    }
}
