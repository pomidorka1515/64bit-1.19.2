package net.minecraft.world.level.chunk;

import com.mojang.serialization.Codec;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.IdMapper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PalettedContainerTest {
    @Test
    void sourceResizePreservesZeroBitSnapshot() {
        for (PalettedContainer.Strategy strategy : strategies()) {
            IdMapper<Object> registry = registry();
            PalettedContainer<Object> source = new PalettedContainer<>(registry, registry.byId(0), strategy);
            PalettedContainer<Object> snapshot = source.copy();
            source.set(0, 0, 0, registry.byId(1));
            assertSame(registry.byId(1), source.get(0, 0, 0));
            for (int i = 0; i < strategy.size(); ++i) {
                assertSame(registry.byId(0), snapshot.get(i), "snapshot index " + i);
            }
        }
    }

    @Test
    void copyCanResizeFromZeroBitsWithoutChangingSource() {
        assertAll(strategies().stream().map(strategy -> () -> {
            IdMapper<Object> registry = registry();
            PalettedContainer<Object> source = new PalettedContainer<>(registry, registry.byId(0), strategy);
            PalettedContainer<Object> copy = source.copy();
            copy.set(0, 0, 0, registry.byId(1));
            assertSame(registry.byId(1), copy.get(0, 0, 0));
            for (int i = 0; i < strategy.size(); ++i) {
                assertSame(registry.byId(0), source.get(i), "source index " + i);
                assertSame(registry.byId(i == 0 ? 1 : 0), copy.get(i), "copy index " + i);
            }
        }));
    }

    @Test
    void copyCanResizeFullLocalPaletteWithoutChangingEitherArrayOracle() {
        assertAll(strategies().stream().map(strategy -> () -> {
            IdMapper<Object> registry = registry();
            PalettedContainer<Object> source = new PalettedContainer<>(registry, registry.byId(0), strategy);
            int entries = strategy == PalettedContainer.Strategy.SECTION_STATES ? 16 : 2;
            int side = strategy == PalettedContainer.Strategy.SECTION_STATES ? 16 : 4;
            Object[] expected = new Object[strategy.size()];
            Arrays.fill(expected, registry.byId(0));
            for (int i = 1; i < entries; ++i) {
                source.set(i % side, 0, i / side, registry.byId(i));
                expected[i] = registry.byId(i);
            }
            PalettedContainer<Object> copy = source.copy();
            copy.set(0, 0, 0, registry.byId(entries));
            for (int i = 0; i < strategy.size(); ++i) {
                assertSame(expected[i], source.get(i), "source index " + i);
                assertSame(i == 0 ? registry.byId(entries) : expected[i], copy.get(i), "copy index " + i);
            }
        }));
    }

    @Test
    void repeatedNetworkReadsMatchIndependentWireLayoutAndPreserveCopies() {
        for (PalettedContainer.Strategy strategy : strategies()) {
            Fixture fixture = new Fixture(strategy);
            PalettedContainer<Object> target = fixture.empty();
            // Matching configurations with different palette entries, then growth and shrinkage.
            for (int entries : new int[] {1, 1, 2, 2, 8, 8, 17, 33, 257, 1, 2}) {
                if (entries > strategy.size()) {
                    continue;
                }
                for (int offset : new int[] {0, 91}) {
                    Object[] oracle = fixture.values(entries, offset);
                    PalettedContainer<Object> source = fixture.filled(oracle);
                    byte[] expected = fixture.wire(oracle, entries);
                    assertArrayEquals(expected, write(source));
                    PalettedContainer<Object> snapshot = target.copy();
                    Object[] before = fixture.contents(target);
                    FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(expected));
                    try {
                        target.read(buffer);
                        assertEquals(0, buffer.readableBytes());
                    } finally {
                        buffer.release();
                    }
                    fixture.assertContents(oracle, target);
                    fixture.assertContents(before, snapshot);
                    assertArrayEquals(expected, write(target));
                    // Reading into a copy must not mutate the original, even at zero bits.
                    PalettedContainer<Object> copy = target.copy();
                    FriendlyByteBuf replacement = new FriendlyByteBuf(Unpooled.wrappedBuffer(
                            fixture.wire(fixture.values(entries, offset + 1), entries)));
                    try {
                        copy.read(replacement);
                        assertEquals(0, replacement.readableBytes());
                    } finally {
                        replacement.release();
                    }
                    fixture.assertContents(oracle, target);
                    fixture.assertContents(fixture.values(entries, offset + 1), copy);
                }
            }
        }
    }

    @Test
    void packedSaveLayoutAndCodecRoundTripsMatchIndependentOracle() {
        for (PalettedContainer.Strategy strategy : strategies()) {
            Fixture fixture = new Fixture(strategy);
            Codec<PalettedContainer<Object>> codec = PalettedContainer.codecRW(fixture.registry,
                    Codec.INT.xmap(fixture.registry::byId, fixture.registry::getId), strategy, fixture.registry.byId(0));
            for (int entries : new int[] {1, 2, 8, 9, 16, 17, 32, 33, 256, 257, 511}) {
                if (entries > strategy.size()) {
                    continue;
                }
                Object[] oracle = fixture.values(entries, 91);
                PalettedContainer<Object> source = fixture.filled(oracle);
                List<Object> palette = palette(oracle);
                int bits = entries == 1 ? 0 : strategy == PalettedContainer.Strategy.SECTION_STATES
                        ? Math.max(4, bits(entries)) : bits(entries);
                long[] raw = pack(oracle, palette, fixture.registry, bits, false);
                PalettedContainerRO.PackedData<Object> packed = source.pack(fixture.registry, strategy);
                assertEquals(palette, packed.paletteEntries());
                assertEquals(bits != 0, packed.storage().isPresent());
                assertArrayEquals(raw, packed.storage().map(stream -> stream.toArray()).orElseGet(() -> new long[0]));

                CompoundTag expected = new CompoundTag();
                ListTag savedPalette = new ListTag();
                palette.forEach(value -> savedPalette.add(IntTag.valueOf(fixture.registry.getId(value))));
                expected.put("palette", savedPalette);
                if (bits != 0) {
                    expected.put("data", new LongArrayTag(raw));
                }
                assertEquals(expected, codec.encodeStart(NbtOps.INSTANCE, source).result().orElseThrow());
                PalettedContainer<Object> restored = codec.parse(NbtOps.INSTANCE, expected).result().orElseThrow();
                fixture.assertContents(oracle, restored);
                assertEquals(expected, codec.encodeStart(NbtOps.INSTANCE, restored).result().orElseThrow());
                // Save packing drops unused runtime palette entries and may return to zero bits.
                Object value = oracle[0];
                Object[] uniform = new Object[strategy.size()];
                Arrays.fill(uniform, value);
                fixture.fill(source, uniform);
                PalettedContainerRO.PackedData<Object> compact = source.pack(fixture.registry, strategy);
                assertEquals(List.of(value), compact.paletteEntries());
                assertTrue(compact.storage().isEmpty());
            }
        }
    }

    static List<PalettedContainer.Strategy> strategies() {
        return List.of(PalettedContainer.Strategy.SECTION_STATES, PalettedContainer.Strategy.SECTION_BIOMES);
    }

    private static IdMapper<Object> registry() {
        IdMapper<Object> registry = new IdMapper<>(512);
        for (int i = 0; i < 512; ++i) {
            registry.add(new Object());
        }
        return registry;
    }

    static byte[] write(PalettedContainer<Object> container) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            container.write(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static int bits(int entries) {
        return 32 - Integer.numberOfLeadingZeros(entries - 1);
    }

    private static List<Object> palette(Object[] oracle) {
        List<Object> palette = new ArrayList<>();
        for (Object value : oracle) {
            if (!palette.contains(value)) {
                palette.add(value);
            }
        }
        return palette;
    }

    private static long[] pack(Object[] oracle, List<Object> palette, IdMapper<Object> registry, int bits, boolean global) {
        if (bits == 0) {
            return new long[0];
        }
        int perWord = 64 / bits;
        long[] raw = new long[(oracle.length + perWord - 1) / perWord];
        for (int i = 0; i < oracle.length; ++i) {
            int value = global ? registry.getId(oracle[i]) : palette.indexOf(oracle[i]);
            raw[i / perWord] |= (long) value << (i % perWord * bits);
        }
        return raw;
    }

    private static void varInt(ByteArrayOutputStream output, int value) {
        while ((value & ~127) != 0) {
            output.write(value & 127 | 128);
            value >>>= 7;
        }
        output.write(value);
    }

    static final class Fixture {
        final IdMapper<Object> registry = registry();
        final PalettedContainer.Strategy strategy;
        final int side;

        Fixture(PalettedContainer.Strategy strategy) {
            this.strategy = strategy;
            this.side = strategy == PalettedContainer.Strategy.SECTION_STATES ? 16 : 4;
        }

        PalettedContainer<Object> empty() {
            return new PalettedContainer<>(this.registry, this.registry.byId(0), this.strategy);
        }

        Object[] values(int entries, int offset) {
            Object[] values = new Object[this.strategy.size()];
            for (int i = 0; i < values.length; ++i) {
                // Local IDs and registry IDs deliberately differ, including in global save data.
                values[i] = this.registry.byId(((i % entries) * 73 + offset) % 512);
            }
            return values;
        }

        PalettedContainer<Object> filled(Object[] oracle) {
            PalettedContainer<Object> container = new PalettedContainer<>(this.registry, oracle[0], this.strategy);
            this.fill(container, oracle);
            return container;
        }

        void fill(PalettedContainer<Object> container, Object[] oracle) {
            for (int i = 0; i < oracle.length; ++i) {
                this.set(container, i, oracle[i]);
            }
        }

        void set(PalettedContainer<Object> container, int index, Object value) {
            container.set(index % this.side, index / (this.side * this.side), index / this.side % this.side, value);
        }

        Object[] contents(PalettedContainer<Object> container) {
            Object[] values = new Object[this.strategy.size()];
            for (int i = 0; i < values.length; ++i) {
                values[i] = container.get(i % this.side, i / (this.side * this.side), i / this.side % this.side);
            }
            return values;
        }

        void assertContents(Object[] oracle, PalettedContainer<Object> container) {
            assertArrayEquals(oracle, this.contents(container));
        }

        byte[] wire(Object[] oracle, int entries) {
            boolean states = this.strategy == PalettedContainer.Strategy.SECTION_STATES;
            boolean global = entries > (states ? 256 : 8);
            int bits = entries == 1 ? 0 : global ? 9 : states ? Math.max(4, bits(entries)) : bits(entries);
            List<Object> palette = palette(oracle);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(bits);
            if (!global) {
                if (bits != 0) {
                    varInt(output, palette.size());
                }
                palette.forEach(value -> varInt(output, this.registry.getId(value)));
            }
            long[] raw = pack(oracle, palette, this.registry, bits, global);
            varInt(output, raw.length);
            for (long word : raw) {
                for (int shift = 56; shift >= 0; shift -= 8) {
                    output.write((int) (word >>> shift) & 255);
                }
            }
            return output.toByteArray();
        }
    }
}
