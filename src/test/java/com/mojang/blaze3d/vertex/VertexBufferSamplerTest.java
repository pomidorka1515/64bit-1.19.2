package com.mojang.blaze3d.vertex;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

public class VertexBufferSamplerTest {
    private static final String OWNER = "com/mojang/blaze3d/vertex/VertexBuffer";
    private static final String TEST = Type.getInternalName(VertexBufferSamplerTest.class);
    private static final String RECORDER = Type.getInternalName(Recorder.class);
    public static final String[] CACHED_NAMES = names();
    private static final MethodHandle LOOP = samplerLoop();
    private static Recorder current;
    private static volatile long sink;

    @Test
    void fixedNamesAreReusedWithoutPerDrawConstruction() throws Exception {
        assertNotNull(CACHED_NAMES);
        assertEquals(12, CACHED_NAMES.length);
        var field = VertexBuffer.class.getDeclaredField("SAMPLER_NAMES");
        assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isStatic(field.getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isFinal(field.getModifiers()));
        for (int i = 0; i < 12; i++) {
            assertEquals("Sampler" + i, CACHED_NAMES[i]);
            assertSame(CACHED_NAMES[i], names()[i]);
        }
        int[] lookups = {0};
        try (InputStream input = VertexBuffer.class.getResourceAsStream("VertexBuffer.class")) {
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (!name.equals("_drawWithShader")) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitInvokeDynamicInsn(String name, String desc, Handle bootstrap, Object... args) {
                            fail("draw loop must not construct names dynamically");
                        }

                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                            if (owner.equals(OWNER) && name.equals("SAMPLER_NAMES")) {
                                assertEquals(Opcodes.GETSTATIC, opcode);
                                lookups[0]++;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertEquals(1, lookups[0]);
    }

    @Test
    void compiledLoopMatchesFrozenReferenceAcrossChangingTextures() throws Throwable {
        for (int pass = 0; pass < 100; pass++) {
            Recorder reference = new Recorder(true);
            for (int i = 0; i < 12; i++) {
                reference.textures[i] = pass * 1009 + i * 31;
            }
            Recorder candidate = reference.copy();
            current = reference;
            reference(reference);
            current = candidate;
            run(candidate);
            assertEquals(reference.events, candidate.events);
            assertEquals(reference.checksum, candidate.checksum);
            assertEquals(24, candidate.events.size());
            for (int i = 0; i < 12; i++) {
                assertEquals("read:" + i, candidate.events.get(i * 2));
                assertEquals("set:Sampler" + i + ":" + candidate.textures[i], candidate.events.get(i * 2 + 1));
            }
        }
    }

    @Test
    void textureAndSamplerFailuresKeepTheSameCallBoundary() throws Throwable {
        for (int failure = 0; failure < 24; failure++) {
            Recorder reference = new Recorder(true);
            reference.failAt = failure;
            Recorder candidate = reference.copy();
            current = reference;
            assertThrows(IllegalStateException.class, () -> reference(reference));
            current = candidate;
            assertThrows(IllegalStateException.class, () -> run(candidate));
            assertEquals(reference.events, candidate.events);
            assertEquals(reference.checksum, candidate.checksum);
        }
    }

    // Frozen pre-change loop. The harness substitutes only the two external calls.
    private static void reference(Recorder shader) {
        for (int i = 0; i < 12; ++i) {
            int texture = texture(i);
            shader.setSampler("Sampler" + i, texture);
        }
    }

    public static int texture(int index) {
        if (current.events != null) {
            current.event("read:" + index);
        }
        return current.textures[index];
    }

    public static String[] names() {
        try {
            var field = VertexBuffer.class.getDeclaredField("SAMPLER_NAMES");
            field.setAccessible(true);
            return (String[]) field.get(null);
        } catch (NoSuchFieldException e) {
            return null; // Allows the frozen-reference tests to run before the production edit.
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void run(Recorder recorder) throws Throwable {
        LOOP.invokeExact((Object) null, (Object) null, (Object) null, recorder);
    }

    /** Execute the actual compiled loop without constructing a VertexBuffer or GL context. */
    private static MethodHandle samplerLoop() {
        try (InputStream input = VertexBuffer.class.getResourceAsStream("VertexBuffer.class")) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            String generated = "com/mojang/blaze3d/vertex/SamplerLoopHarness";
            writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, generated, null, "java/lang/Object", null);
            String descriptor = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;L" + RECORDER + ";)V";
            boolean[] found = {false};
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    if (!name.equals("_drawWithShader")) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9,
                            writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", descriptor, null, null)) {
                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                            if (owner.equals("net/minecraft/client/renderer/ShaderInstance") && name.equals("MODEL_VIEW_MATRIX")) {
                                // The first uniform access follows the sampler loop; discard its receiver.
                                super.visitInsn(Opcodes.POP);
                                super.visitInsn(Opcodes.RETURN);
                                super.visitMaxs(0, 0);
                                super.visitEnd();
                                found[0] = true;
                                mv = null;
                            } else if (owner.equals(OWNER) && name.equals("SAMPLER_NAMES")) {
                                super.visitFieldInsn(Opcodes.GETSTATIC, TEST, "CACHED_NAMES", "[Ljava/lang/String;");
                            } else {
                                super.visitFieldInsn(opcode, owner, name, desc);
                            }
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
                            if (name.equals("getShaderTexture") && owner.equals("com/mojang/blaze3d/systems/RenderSystem")) {
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, TEST, "texture", desc, false);
                            } else if (name.equals("setSampler") && owner.equals("net/minecraft/client/renderer/ShaderInstance")) {
                                super.visitMethodInsn(opcode, RECORDER, name, desc, false);
                            } else {
                                super.visitMethodInsn(opcode, owner, name, desc, isInterface);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            assertTrue(found[0], "sampler loop terminator must be found");
            writer.visitEnd();
            class Loader extends ClassLoader {
                Loader() { super(VertexBufferSamplerTest.class.getClassLoader()); }
                Class<?> define(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
            }
            Class<?> harness = new Loader().define(writer.toByteArray());
            return MethodHandles.publicLookup().findStatic(harness, "run",
                    MethodType.methodType(void.class, Object.class, Object.class, Object.class, Recorder.class));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static class Recorder {
        final int[] textures = new int[12];
        final List<String> events;
        int failAt = -1;
        long checksum;

        Recorder(boolean record) {
            events = record ? new ArrayList<>() : null;
            for (int i = 0; i < 12; i++) {
                textures[i] = i - 6; // Cached Integer range: isolate name allocation.
            }
        }

        Recorder copy() {
            Recorder copy = new Recorder(events != null);
            System.arraycopy(textures, 0, copy.textures, 0, 12);
            copy.failAt = failAt;
            return copy;
        }

        void event(String event) {
            if (events != null) {
                events.add(event);
                if (events.size() - 1 == failAt) {
                    throw new IllegalStateException("boundary");
                }
            }
        }

        public void setSampler(String name, Object value) {
            if (events != null) {
                event("set:" + name + ":" + value);
            }
            checksum += name.hashCode() ^ (Integer) value;
        }
    }

    /** Local diagnostic only: not a GPU benchmark or a JUnit timing assertion. */
    public static void main(String[] args) throws Throwable {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        Recorder recorder = new Recorder(false);
        current = recorder;
        for (int i = 0; i < 200_000; i++) {
            reference(recorder);
            run(recorder);
        }
        double[][] nanos = new double[2][7];
        double[][] bytes = new double[2][7];
        for (int trial = 0; trial < 7; trial++) {
            long[] checksums = new long[2];
            for (int order = 0; order < 2; order++) {
                int path = (trial + order) % 2;
                recorder.checksum = 0;
                long beforeBytes = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
                long start = System.nanoTime();
                for (int i = 0; i < 100_000; i++) {
                    if (path == 0) {
                        reference(recorder);
                    } else {
                        run(recorder);
                    }
                }
                long elapsed = System.nanoTime() - start;
                long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().getId()) - beforeBytes;
                checksums[path] = recorder.checksum;
                sink = recorder.checksum;
                nanos[path][trial] = elapsed / 100_000.0;
                bytes[path][trial] = allocated / 100_000.0;
                System.out.printf("trial=%d path=%s ns/loop=%.3f bytes/loop=%.3f checksum=%d%n",
                        trial, path == 0 ? "reference" : "cached", nanos[path][trial], bytes[path][trial], sink);
            }
            assertEquals(checksums[0], checksums[1]);
        }
        for (int path = 0; path < 2; path++) {
            Arrays.sort(nanos[path]);
            Arrays.sort(bytes[path]);
        }
        System.out.printf("median reference/cached ns=%.3f/%.3f bytes=%.3f/%.3f elapsed-change=%.2f%%%n",
                nanos[0][3], nanos[1][3], bytes[0][3], bytes[1][3], (nanos[1][3] / nanos[0][3] - 1) * 100);
        assertTrue(bytes[1][3] < bytes[0][3]);
        assertTrue(nanos[1][3] < nanos[0][3]);
    }
}
