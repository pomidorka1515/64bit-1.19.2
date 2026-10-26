package net.minecraft.client.multiplayer;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class EntityTrackingPacketTest {
    @Test
    void entityReplicationMustNotDependOnChunkCacheCenter() throws Exception {
        // Login and dimension changes can pair entities before the first cache
        // center packet. Dropping a spawn is permanent: the server already has
        // the recipient in seenBy. Dropping movement also loses the delta base.
        Set<String> handlers = Set.of("handleAddPlayer", "handleAddEntity",
                "handleAddExperienceOrb", "handleTeleportEntity", "handleMoveEntity");
        Set<String> visited = new HashSet<>();
        try (InputStream input = ClientPacketListener.class.getResourceAsStream("ClientPacketListener.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    if (!handlers.contains(name)) return null;
                    visited.add(name);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String method,
                                String descriptor, boolean isInterface) {
                            assertFalse(Set.of("isTrackedPosition", "isInViewRange", "getChunkSource")
                                    .contains(method), name + " must accept server tracking independently of chunk view");
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertEquals(handlers, visited);
    }
}
