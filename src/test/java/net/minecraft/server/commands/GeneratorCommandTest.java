package net.minecraft.server.commands;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.brigadier.CommandDispatcher;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

class GeneratorCommandTest {
    @Test void exposesOnlyIndependentApprovedArguments() {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        GeneratorCommand.register(dispatcher);
        assertNull(dispatcher.getRoot().getChild("farlands"));
        var root = dispatcher.getRoot().getChild("generator");
        assertEquals(Set.of("mode", "precision", "confirm"), names(root));
        assertEquals(Set.of("32bit", "32bit-stable-shifts", "64bit", "64bit-stable-shifts", "64bit-patched", "help"), names(root.getChild("mode")));
        assertEquals(Set.of("precise", "lossy", "help"), names(root.getChild("precision")));
        assertNull(root.getCommand());
        assertNull(root.getChild("mode").getCommand());
        assertNull(root.getChild("precision").getCommand());
    }

    private static Set<String> names(com.mojang.brigadier.tree.CommandNode<CommandSourceStack> node) {
        return node.getChildren().stream().map(com.mojang.brigadier.tree.CommandNode::getName).collect(Collectors.toSet());
    }
}
