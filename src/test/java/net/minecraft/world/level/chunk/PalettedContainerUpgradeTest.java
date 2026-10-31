package net.minecraft.world.level.chunk;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PalettedContainerUpgradeTest {
    @Test
    void allPaletteTransitionsPreserveArrayOracle() {
        for (PalettedContainer.Strategy strategy : PalettedContainerTest.strategies()) {
            PalettedContainerTest.Fixture fixture = new PalettedContainerTest.Fixture(strategy);
            PalettedContainer<Object> container = fixture.empty();
            Object[] oracle = new Object[strategy.size()];
            Arrays.fill(oracle, fixture.registry.byId(0));
            int entries = Math.min(512, strategy.size());
            for (int i = 1; i < entries; ++i) {
                // Reverse registry order distinguishes local palette IDs from global IDs.
                Object value = fixture.registry.byId(512 - i);
                fixture.set(container, i, value);
                oracle[i] = value;
                fixture.assertContents(oracle, container);
            }
            Random random = new Random(0xC0FFEE);
            for (int i = 0; i < 1000; ++i) {
                int index = random.nextInt(oracle.length);
                Object value = fixture.registry.byId(random.nextInt(512));
                assertSame(oracle[index], container.getAndSet(index % fixture.side,
                        index / (fixture.side * fixture.side), index / fixture.side % fixture.side, value));
                oracle[index] = value;
            }
            fixture.assertContents(oracle, container);
            PalettedContainer<Object> copy = container.copy();
            fixture.assertContents(oracle, copy);
            fixture.set(copy, 0, fixture.registry.byId(1));
            fixture.assertContents(oracle, container);
        }
    }

    @Test
    void copiesOwnTheirResizeHandlersAtEveryLocalBoundary() {
        for (PalettedContainer.Strategy strategy : PalettedContainerTest.strategies()) {
            PalettedContainerTest.Fixture fixture = new PalettedContainerTest.Fixture(strategy);
            int[] boundaries = strategy == PalettedContainer.Strategy.SECTION_STATES
                    ? new int[] {1, 16, 32, 64, 128, 256} : new int[] {1, 2, 4, 8};
            for (int entries : boundaries) {
                Object[] original = fixture.values(entries, 91);
                PalettedContainer<Object> source = fixture.filled(original);
                PalettedContainer<Object> copy = source.copy();
                byte[] sourceBefore = PalettedContainerTest.write(source);
                Object[] copyOracle = original.clone();
                Object newValue = fixture.registry.byId((entries * 73 + 91) % 512);
                fixture.set(copy, original.length - 1, newValue);
                copyOracle[original.length - 1] = newValue;
                fixture.assertContents(original, source);
                fixture.assertContents(copyOracle, copy);
                assertArrayEquals(sourceBefore, PalettedContainerTest.write(source));
                byte[] copyBefore = PalettedContainerTest.write(copy);
                Object[] sourceOracle = original.clone();
                fixture.set(source, 0, newValue);
                sourceOracle[0] = newValue;
                fixture.assertContents(sourceOracle, source);
                fixture.assertContents(copyOracle, copy);
                assertArrayEquals(copyBefore, PalettedContainerTest.write(copy));
                // A copy of a copy must also own subsequent growth.
                PalettedContainer<Object> secondCopy = copy.copy();
                Object nextValue = fixture.registry.byId(((entries + 1) * 73 + 91) % 512);
                fixture.set(secondCopy, 0, nextValue);
                fixture.assertContents(copyOracle, copy);
                copyOracle[0] = nextValue;
                fixture.assertContents(copyOracle, secondCopy);
            }
        }
    }
}
