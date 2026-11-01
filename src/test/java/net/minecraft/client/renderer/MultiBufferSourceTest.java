package net.minecraft.client.renderer;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

class MultiBufferSourceTest {
    private static volatile long checksum;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void mixedLifecycleAndBytesMatchFrozenReference() {
        try (Fixture reference = new Fixture(true, false); Fixture actual = new Fixture(false, false)) {
            Random random = new Random(244);
            for (int i = 0; i < 4000; i++) {
                int operation = random.nextInt(10);
                int type = random.nextInt(actual.types.length);
                assertEquals(reference.step(operation, type, i), actual.step(operation, type, i));
                compare(reference, actual);
            }
            reference.source.endBatch();
            actual.source.endBatch();
            compare(reference, actual);
            assertFalse(actual.events.isEmpty());
        }
    }

    @Test
    void consolidationSharedBuildersAndFlushOrderMatch() {
        try (Fixture reference = new Fixture(true, false); Fixture actual = new Fixture(false, false)) {
            // Default transitions, repeated strips, fixed/default interleaving, shared fixed builder.
            int[] sequence = {0, 0, 1, 1, 2, 2, 0, 3, 4, 5, 4, 1, 3, 0};
            for (int i = 0; i < sequence.length; i++) {
                assertEquals(reference.step(0, sequence[i], i), actual.step(0, sequence[i], i));
                compare(reference, actual);
            }
            reference.source.endLastBatch();
            actual.source.endLastBatch();
            compare(reference, actual);
            reference.source.endBatch();
            actual.source.endBatch();
            reference.source.endBatch();
            actual.source.endBatch();
            compare(reference, actual);
            assertTrue(actual.source.startedBuffers.isEmpty());
        }
    }

    @Test
    void equalButDistinctBuilderSubclassesKeepEqualitySemantics() {
        try (Fixture reference = new Fixture(true, true); Fixture actual = new Fixture(false, true)) {
            assertNotSame(actual.builders[1], actual.builders[2]);
            assertEquals(actual.builders[1], actual.builders[2]);
            assertEquals(reference.step(0, 3, 0), actual.step(0, 3, 0));
            assertEquals(reference.step(0, 4, 0), actual.step(0, 4, 0));
            assertEquals(1, actual.source.startedBuffers.size());
            assertFalse(actual.builders[2].building());
            compare(reference, actual);
            for (int type : new int[] {3, 4, 5, 0, 3, 4}) {
                assertEquals(reference.step(0, type, type), actual.step(0, type, type));
                compare(reference, actual);
            }
            assertEquals(reference.step(8, 4, 0), actual.step(8, 4, 0));
            compare(reference, actual);
        }
    }

    @Test
    void nullFixedBuilderRetainsFailureAndMembershipBehavior() {
        try (Fixture reference = new Fixture(true, false); Fixture actual = new Fixture(false, false)) {
            reference.fixed.put(reference.types[3], null);
            actual.fixed.put(actual.types[3], null);
            assertThrows(NullPointerException.class, () -> reference.source.getBuffer(reference.types[3]));
            assertThrows(NullPointerException.class, () -> actual.source.getBuffer(actual.types[3]));
            compare(reference, actual);
            assertTrue(actual.source.startedBuffers.contains(null));
            reference.fixed.put(reference.types[3], reference.builders[1]);
            actual.fixed.put(actual.types[3], actual.builders[1]);
            assertEquals(reference.step(0, 3, 1), actual.step(0, 3, 1));
            reference.source.endBatch();
            actual.source.endBatch();
            compare(reference, actual);
        }
    }

    @Test
    void beginAndEndFailuresPreservePartialState() {
        for (boolean begin : new boolean[] {true, false}) {
            try (Fixture reference = new Fixture(true, false); Fixture actual = new Fixture(false, false)) {
                if (begin) {
                    reference.builders[0].failBegin = actual.builders[0].failBegin = true;
                } else {
                    reference.step(0, 0, 1);
                    actual.step(0, 0, 1);
                    reference.types[0].failEnd = actual.types[0].failEnd = true;
                }
                assertEquals(reference.step(0, begin ? 0 : 1, 2), actual.step(0, begin ? 0 : 1, 2));
                compare(reference, actual);
                reference.builders[0].failBegin = actual.builders[0].failBegin = false;
                reference.types[0].failEnd = actual.types[0].failEnd = false;
                for (int i = 0; i < 10; i++) {
                    assertEquals(reference.step(i, i % 6, i), actual.step(i, i % 6, i));
                    compare(reference, actual);
                }
            }
        }
    }

    @Test
    void changingFixedMapAndGrowingStartedSetMatch() {
        try (Fixture reference = new Fixture(true, false); Fixture actual = new Fixture(false, false)) {
            for (int i = 0; i < 80; i++) {
                reference.addFixed(i);
                actual.addFixed(i);
                assertEquals(reference.step(0, reference.types.length - 1, i),
                    actual.step(0, actual.types.length - 1, i));
                compare(reference, actual);
            }
            reference.source.endBatch();
            actual.source.endBatch();
            compare(reference, actual);
            reference.fixed.remove(reference.types[3]);
            actual.fixed.remove(actual.types[3]);
            for (int i = 0; i < 30; i++) {
                assertEquals(reference.step(i % 10, i % 6, i), actual.step(i % 10, i % 6, i));
                compare(reference, actual);
            }
        }
    }

    private static void compare(Fixture reference, Fixture actual) {
        assertEquals(reference.events, actual.events);
        assertEquals(reference.source.lastState.map(Object::toString), actual.source.lastState.map(Object::toString));
        assertEquals(reference.started().size(), actual.started().size());
        for (int i = 0; i < reference.builders.length; i++) {
            assertEquals(reference.started().contains(reference.builders[i]),
                actual.started().contains(actual.builders[i]));
            assertEquals(reference.builders[i].building(), actual.builders[i].building());
        }
    }

    private static class Fixture implements AutoCloseable {
        final List<String> events = new ArrayList<>();
        final Map<RenderType, BufferBuilder> fixed = new LinkedHashMap<>();
        RecordingBuilder[] builders;
        Type[] types;
        final MultiBufferSource.BufferSource source;

        Set<BufferBuilder> started() {
            return source instanceof FrozenSource frozen ? frozen.startedBuffers : source.startedBuffers;
        }

        Fixture(boolean reference, boolean equalBuilders) {
            builders = new RecordingBuilder[] {new RecordingBuilder(0, events, false),
                new RecordingBuilder(1, events, equalBuilders), new RecordingBuilder(2, events, equalBuilders)};
            types = new Type[6];
            for (int i = 0; i < types.length; i++) {
                types[i] = new Type("type" + i, i == 2 ? VertexFormat.Mode.TRIANGLE_STRIP : VertexFormat.Mode.QUADS, events);
            }
            fixed.put(types[3], builders[1]);
            fixed.put(types[4], builders[2]);
            fixed.put(types[5], builders[1]);
            source = reference ? new FrozenSource(builders[0], fixed) : MultiBufferSource.immediateWithBuffers(fixed, builders[0]);
        }

        void addFixed(int number) {
            RecordingBuilder builder = new RecordingBuilder(builders.length, events, false);
            builders = java.util.Arrays.copyOf(builders, builders.length + 1);
            builders[builders.length - 1] = builder;
            Type type = new Type("extra" + number, VertexFormat.Mode.QUADS, events);
            types = java.util.Arrays.copyOf(types, types.length + 1);
            types[types.length - 1] = type;
            fixed.put(type, builder);
        }

        String step(int operation, int type, int value) {
            try {
                if (operation < 7) {
                    VertexConsumer consumer = source.getBuffer(types[type]);
                    int selected = -1;
                    for (int i = 0; i < builders.length; i++) if (consumer == builders[i]) selected = i;
                    assertTrue(selected >= 0);
                    for (int i = 0; i < 4; i++) consumer.vertex(value + i * 0.25, -value, i).endVertex();
                    return "builder" + selected;
                } else if (operation == 7) source.endLastBatch();
                else if (operation == 8) source.endBatch(types[type]);
                else source.endBatch();
                return "ok";
            } catch (IllegalStateException exception) {
                return exception.getClass().getName() + ":" + exception.getMessage();
            }
        }

        @Override
        public void close() {
            try {
                Field buffer = BufferBuilder.class.getDeclaredField("buffer");
                buffer.setAccessible(true);
                for (RecordingBuilder builder : builders) MemoryUtil.memFree((ByteBuffer) buffer.get(builder));
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        }
    }

    private static class RecordingBuilder extends BufferBuilder {
        final int id;
        final List<String> events;
        final boolean equal;
        boolean failBegin;

        RecordingBuilder(int id, List<String> events, boolean equal) {
            super(256);
            this.id = id;
            this.events = events;
            this.equal = equal;
        }

        @Override
        public void begin(VertexFormat.Mode mode, VertexFormat format) {
            events.add("begin:" + id + ":" + mode);
            if (failBegin) throw new IllegalStateException("begin failure");
            super.begin(mode, format);
        }

        @Override
        public boolean equals(Object other) {
            return this == other || equal && other instanceof RecordingBuilder builder && builder.equal;
        }

        @Override
        public int hashCode() {
            // Collisions exercise deletion/probing; equal subclasses deliberately share a hash.
            return 7;
        }
    }

    private static class Type extends RenderType {
        final List<String> events;
        boolean failEnd;

        Type(String name, VertexFormat.Mode mode, List<String> events) {
            super(name, DefaultVertexFormat.POSITION, mode, 256, false, false, () -> {}, () -> {});
            this.events = events;
        }

        @Override
        public void end(BufferBuilder builder, int x, int y, int z) {
            RecordingBuilder recording = (RecordingBuilder) builder;
            events.add("end:" + this + ":" + recording.id + ":" + x + ":" + y + ":" + z);
            if (failEnd) throw new IllegalStateException("end failure");
            if (builder.building()) {
                BufferBuilder.RenderedBuffer rendered = builder.end();
                events.add(rendered.drawState().mode() + ":" + rendered.drawState().vertexCount()
                    + ":" + rendered.drawState().indexCount() + ":" + rendered.drawState().indexType()
                    + ":" + bytes(rendered.vertexBuffer()) + ":" + bytes(rendered.indexBuffer()));
                rendered.release();
            }
        }
    }

    private static String bytes(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }

    public static void main(String[] args) throws Exception {
        bootstrap();
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        String[] workloads = {"default-switch", "fixed-cycle", "fixed-wave", "no-transition"};
        try (Probe reference = new Probe(true); Probe actual = new Probe(false)) {
            // Warm every branch before measuring any workload, rather than reprofiling run per case.
            for (int i = 0; i < 10; i++) {
                for (String workload : workloads) {
                    reference.run(workload, 200000);
                    actual.run(workload, 200000);
                }
            }
            for (String workload : workloads) {
                for (int trial = 0; trial < 7; trial++) {
                    long[][] result = new long[2][];
                    for (int order = 0; order < 2; order++) {
                        int path = (trial + order) % 2;
                        Probe probe = path == 0 ? reference : actual;
                        long beforeBytes = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
                        long beforeTime = System.nanoTime();
                        long sum = probe.run(workload, 500000);
                        long elapsed = System.nanoTime() - beforeTime;
                        long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().getId()) - beforeBytes;
                        checksum = sum;
                        result[path] = new long[] {elapsed, allocated, sum};
                    }
                    if (result[0][2] != result[1][2]) throw new AssertionError("probe checksum mismatch");
                    System.out.printf(java.util.Locale.ROOT,
                        "SOURCE_PROBE %s %d ref_ns=%.3f actual_ns=%.3f ref_bytes=%.3f actual_bytes=%.3f checksum=%d%n",
                        workload, trial, result[0][0] / 500000.0, result[1][0] / 500000.0,
                        result[0][1] / 500000.0, result[1][1] / 500000.0, result[0][2]);
                }
            }
        }
    }

    // Nonallocating begin/end boundaries isolate real BufferSource dispatch and set operations.
    // No vertices, GL calls, upload work or wrapper allocations are measured here.
    private static class Probe implements AutoCloseable {
        final BufferBuilder[] builders = new BufferBuilder[17];
        final RenderType[] types = new RenderType[18];
        final MultiBufferSource.BufferSource source;
        long begins;
        long ends;

        Probe(boolean reference) throws Exception {
            Map<RenderType, BufferBuilder> fixed = new LinkedHashMap<>();
            for (int i = 0; i < builders.length; i++) {
                final int id = i;
                builders[i] = new BufferBuilder(1) {
                    @Override
                    public void begin(VertexFormat.Mode mode, VertexFormat format) {
                        begins += id + 1;
                    }
                };
            }
            for (int i = 0; i < types.length; i++) {
                final int id = i;
                types[i] = new RenderType("probe" + i, DefaultVertexFormat.POSITION, VertexFormat.Mode.QUADS,
                    256, false, false, () -> {}, () -> {}) {
                    @Override
                    public void end(BufferBuilder builder, int x, int y, int z) {
                        ends += id + 1;
                    }
                };
                if (i >= 2) fixed.put(types[i], builders[i - 1]);
            }
            source = MultiBufferSource.immediateWithBuffers(fixed, builders[0]);
            if (reference) {
                // Both paths execute the identical compiled production methods: only storage differs.
                // The separate FrozenSource remains the behavioral oracle, not the timing oracle.
                Field started = MultiBufferSource.BufferSource.class.getDeclaredField("startedBuffers");
                started.setAccessible(true);
                started.set(source, new HashSet<BufferBuilder>());
            }
        }

        long run(String workload, int count) {
            begins = ends = 0;
            switch (workload) {
                case "default-switch" -> {
                    for (int i = 0; i < count; i++) source.getBuffer(types[i & 1]);
                }
                case "fixed-cycle" -> {
                    for (int i = 0; i < count; i++) {
                        RenderType type = types[2 + (i & 15)];
                        source.getBuffer(type);
                        source.endBatch(type);
                    }
                }
                case "fixed-wave" -> {
                    for (int i = 0; i < count; i++) {
                        source.getBuffer(types[2 + (i & 15)]);
                        if ((i & 15) == 15) source.endBatch();
                    }
                }
                case "no-transition" -> {
                    for (int i = 0; i < count; i++) source.getBuffer(types[0]);
                }
                default -> throw new IllegalArgumentException(workload);
            }
            source.endBatch();
            return begins * 31 + ends;
        }

        @Override
        public void close() throws Exception {
            Field buffer = BufferBuilder.class.getDeclaredField("buffer");
            buffer.setAccessible(true);
            for (BufferBuilder builder : builders) MemoryUtil.memFree((ByteBuffer) buffer.get(builder));
        }
    }

    // Frozen pre-P2.4 control flow and HashSet storage, including virtual getBuffer/endBatch calls.
    private static class FrozenSource extends MultiBufferSource.BufferSource {
        private final Set<BufferBuilder> startedBuffers = new HashSet<>();

        FrozenSource(BufferBuilder builder, Map<RenderType, BufferBuilder> fixed) {
            super(builder, fixed);
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            Optional<RenderType> optional = type.asOptional();
            BufferBuilder selected = fixedBuffers.getOrDefault(type, builder);
            if (!Objects.equals(lastState, optional) || !type.canConsolidateConsecutiveGeometry()) {
                if (lastState.isPresent()) {
                    RenderType previous = lastState.get();
                    if (!fixedBuffers.containsKey(previous)) endBatch(previous);
                }
                if (startedBuffers.add(selected)) selected.begin(type.mode(), type.format());
                lastState = optional;
            }
            return selected;
        }

        @Override
        public void endLastBatch() {
            if (lastState.isPresent()) {
                RenderType previous = lastState.get();
                if (!fixedBuffers.containsKey(previous)) endBatch(previous);
                lastState = Optional.empty();
            }
        }

        @Override
        public void endBatch() {
            lastState.ifPresent(type -> {
                VertexConsumer consumer = getBuffer(type);
                if (consumer == builder) endBatch(type);
            });
            for (RenderType type : fixedBuffers.keySet()) endBatch(type);
        }

        @Override
        public void endBatch(RenderType type) {
            BufferBuilder selected = fixedBuffers.getOrDefault(type, builder);
            boolean last = Objects.equals(lastState, type.asOptional());
            if (last || selected != builder) {
                if (startedBuffers.remove(selected)) {
                    type.end(selected, 0, 0, 0);
                    if (last) lastState = Optional.empty();
                }
            }
        }
    }
}
