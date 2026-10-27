package net.minecraft.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class Int1088ScratchTest {
    @Test
    void nestedArithmeticKeepsOuterLimbsAndCopiesValues() {
        try (Int1088Scratch a = Int1088Scratch.acquire();
             Int1088Scratch b = Int1088Scratch.acquire()) {
            assertNotSame(a.limbs(), b.limbs());
            assertEquals(0, a.offset());
            assertEquals(Int1088Scratch.CAPACITY, a.limbs().length);
            for (Int1088 value : new Int1088[]{Int1088.ZERO, Int1088.fromLong(-1),
                    Int1088.ONE.shiftLeftSaturated(1024), Int1088.MIN, Int1088.MAX}) {
                a.copyFrom(value);
                assertEquals(value.limbCount(), a.length());
                for (int i = 0; i < Int1088.LIMBS; ++i) assertEquals(value.limb(i), a.limbs()[i]);
                b.setLength(Int1088Math.addLong(a.limbs(), 0, a.length(), -1, b.limbs(), 0));
                assertEquals(value.add(Int1088.fromLong(-1)), b.toValue());
                Int1088 copy = a.toValue();
                a.limbs()[0] ^= 123;
                assertEquals(value, copy);
            }
        }
    }

    @Test
    void limbViewsCanonicalizeSignExtendAndHandleOverlap() {
        try (Int1088Scratch scratch = Int1088Scratch.acquire()) {
            long[] src = {99, -7, -1, -1, 99};
            scratch.copyFrom(src, 1, 3);
            assertEquals(1, scratch.length());
            assertEquals(Int1088.fromLong(-7), scratch.toValue());
            for (int i = 1; i < Int1088Scratch.CAPACITY; ++i) assertEquals(-1, scratch.limbs()[i]);
            assertArrayEquals(new long[]{99, -7, -1, -1, 99}, src);
            scratch.limbs()[2] = 42;
            scratch.limbs()[3] = 0;
            scratch.copyFrom(scratch.limbs(), 2, 2);
            assertEquals(Int1088.fromLong(42), scratch.toValue());
            for (int i = 1; i < Int1088Scratch.CAPACITY; ++i) assertEquals(0, scratch.limbs()[i]);
            Arrays.fill(scratch.limbs(), -1L);
            scratch.setLength(Int1088Scratch.CAPACITY);
            assertEquals(1, scratch.length());
            assertEquals(Int1088.fromLong(-1), scratch.toValue());
        }
    }

    @Test
    void rejectsInvalidViewsAndOutOfDomainIntermediates() {
        try (Int1088Scratch scratch = Int1088Scratch.acquire()) {
            assertThrows(IllegalArgumentException.class, scratch::toValue);
            assertThrows(IllegalArgumentException.class, () -> scratch.setLength(0));
            assertThrows(IllegalArgumentException.class, () -> scratch.setLength(21));
            assertThrows(IndexOutOfBoundsException.class, () -> scratch.copyFrom(new long[2], 1, 2));
            assertThrows(IllegalArgumentException.class, () -> scratch.copyFrom(new long[21], 0, 21));
            scratch.limbs()[19] = 1;
            scratch.setLength(20);
            assertEquals(20, scratch.length());
            assertThrows(IllegalArgumentException.class, scratch::toValue);
            scratch.clear();
            assertEquals(0, scratch.length());
            assertArrayEquals(new long[20], scratch.limbs());
        }
    }

    @Test
    void closeIsIdempotentAndReacquireIsEmpty() {
        Int1088Scratch scratch = Int1088Scratch.acquire();
        scratch.copyFrom(Int1088.MIN);
        scratch.close();
        scratch.close();
        assertThrows(IllegalStateException.class, scratch::limbs);
        assertThrows(IllegalStateException.class, scratch::offset);
        assertThrows(IllegalStateException.class, scratch::length);
        assertThrows(IllegalStateException.class, () -> scratch.setLength(1));
        assertThrows(IllegalStateException.class, () -> scratch.copyFrom(Int1088.ONE));
        assertThrows(IllegalStateException.class, () -> scratch.copyFrom(new long[1], 0, 1));
        assertThrows(IllegalStateException.class, scratch::toValue);
        assertThrows(IllegalStateException.class, scratch::clear);
        try (Int1088Scratch again = Int1088Scratch.acquire();
             Int1088Scratch nested = Int1088Scratch.acquire()) {
            assertSame(scratch, again);
            assertNotSame(again, nested);
            assertEquals(0, again.length());
            assertArrayEquals(new long[20], again.limbs());
        }
    }

    @Test
    void deepNestingRetainsOnlyEightBuffers() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                Int1088Scratch[] first = new Int1088Scratch[12];
                Set<Int1088Scratch> identities = Collections.newSetFromMap(new IdentityHashMap<>());
                for (int i = 0; i < first.length; ++i) {
                    first[i] = Int1088Scratch.acquire();
                    assertTrue(identities.add(first[i]));
                    first[i].copyFrom(Int1088.fromLong(i));
                }
                for (int i = 0; i < first.length; ++i) {
                    assertEquals(Int1088.fromLong(i), first[i].toValue());
                    first[i].close();
                }
                Int1088Scratch[] second = new Int1088Scratch[12];
                int reused = 0;
                for (int i = 0; i < second.length; ++i) {
                    second[i] = Int1088Scratch.acquire();
                    if (identities.contains(second[i])) ++reused;
                }
                assertEquals(8, reused);
                for (Int1088Scratch scratch : second) scratch.close();
            }).get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void foreignThreadCannotAccessOrReturnBorrowedBuffer() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Int1088Scratch scratch = Int1088Scratch.acquire()) {
            scratch.copyFrom(Int1088.MAX);
            executor.submit(() -> {
                assertThrows(IllegalStateException.class, scratch::close);
                assertThrows(IllegalStateException.class, scratch::limbs);
                assertThrows(IllegalStateException.class, scratch::clear);
                try (Int1088Scratch local = Int1088Scratch.acquire()) {
                    assertNotSame(scratch, local);
                    assertEquals(0, local.length());
                }
            }).get(10, TimeUnit.SECONDS);
            assertEquals(Int1088.MAX, scratch.toValue());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentThreadsKeepNestedComputationsIsolated() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Future<?>[] results = new Future<?>[2];
            for (int thread = 0; thread < results.length; ++thread) {
                final int id = thread;
                results[thread] = executor.submit(() -> {
                    for (int i = 0; i < 100; ++i) {
                        try (Int1088Scratch outer = Int1088Scratch.acquire();
                             Int1088Scratch inner = Int1088Scratch.acquire()) {
                            Int1088 value = Int1088.ONE.shiftLeftSaturated(1024).addSaturated(Int1088.fromLong(id * 1000 + i));
                            outer.copyFrom(value);
                            inner.copyFrom(Int1088.fromLong(-id - i));
                            barrier.await(5, TimeUnit.SECONDS);
                            assertEquals(value, outer.toValue());
                            assertEquals(Int1088.fromLong(-id - i), inner.toValue());
                        }
                    }
                    return null;
                });
            }
            for (Future<?> result : results) result.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }
}
