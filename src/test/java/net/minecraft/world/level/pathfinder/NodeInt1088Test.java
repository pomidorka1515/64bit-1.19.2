package net.minecraft.world.level.pathfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.netty.buffer.Unpooled;
import java.util.HashSet;
import net.minecraft.core.Int1088;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class NodeInt1088Test {
    @Test
    void identityAndWireEncodingRetainHighLimbs() {
        Int1088 wide = Int1088.ONE.shiftLeftSaturated(1024).addSaturated(Int1088.fromLong(7));
        Node node = new Node(wide, 64, Int1088.MIN);
        Node other = new Node(Int1088.fromLong(7), 64, Int1088.MIN);
        assertNotEquals(node, other);
        assertEquals(2, new HashSet<>(java.util.List.of(node, other)).size());

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            node.writeToStream(buffer);
            assertEquals(node, Node.createFromStream(buffer));
            buffer.clear();
            node.writeToStream(buffer);
            assertEquals(node, Target.createFromStream(buffer));
        } finally {
            buffer.release();
        }
    }
}
