package net.minecraft.world.level.chunk.storage;

import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.core.Int1088;

/** Shared cold-path encoding for the independent Anvil storage roots. */
final class RegionPathComponents {
    private static final int COMPONENTS = 9;
    private static final int HEX_WIDTH = 32;
    private static final String HEX = "0123456789abcdef";

    private RegionPathComponents() {
    }

    static Path file(Path storageRoot, String tree, String leaf, Int1088 x, Int1088 z) {
        Path path = axis(storageRoot.resolve(tree).resolve("x"), x).resolve("z");
        return axis(path, z).resolve(leaf);
    }

    private static Path axis(Path path, Int1088 coordinate) {
        for (int component = COMPONENTS - 1; component >= 0; --component) {
            char[] text = new char[HEX_WIDTH];
            int lowIndex = component * 2;
            long high = lowIndex + 1 == Int1088.LIMBS
                    ? coordinate.limb(Int1088.LIMBS - 1) >> 63 : coordinate.limb(lowIndex + 1);
            writeHex(text, 0, high);
            writeHex(text, 16, coordinate.limb(lowIndex));
            path = path.resolve(new String(text));
        }
        return path;
    }

    private static void writeHex(char[] text, int offset, long word) {
        for (int i = 15; i >= 0; --i) {
            text[offset + i] = HEX.charAt((int) (word & 15));
            word >>>= 4;
        }
    }

    /** Returns X/Z only after validating every component, including the extra sign limb. */
    static Optional<Int1088[]> parse(Path storageRoot, Path file, String tree, String leaf) {
        // Do not normalize a traversal into a seemingly valid canonical path.
        if (hasTraversal(file) || hasTraversal(storageRoot)) return Optional.empty();
        Path root = storageRoot.toAbsolutePath().normalize();
        Path absolute = file.toAbsolutePath().normalize();
        if (!absolute.startsWith(root)) return Optional.empty();
        Path relative = root.relativize(absolute);
        if (relative.getNameCount() != 22
                || !relative.getName(0).toString().equals(tree)
                || !relative.getName(1).toString().equals("x")
                || !relative.getName(11).toString().equals("z")
                || !relative.getName(21).toString().equals(leaf)) {
            return Optional.empty();
        }
        Int1088 x = parseAxis(relative, 2);
        Int1088 z = parseAxis(relative, 12);
        return x == null || z == null ? Optional.empty() : Optional.of(new Int1088[] {x, z});
    }

    private static boolean hasTraversal(Path path) {
        for (Path component : path) {
            String text = component.toString();
            if (text.equals(".") || text.equals("..")) return true;
        }
        return false;
    }

    private static Int1088 parseAxis(Path path, int start) {
        long[] limbs = new long[Int1088.LIMBS];
        for (int index = 0; index < COMPONENTS; ++index) {
            String text = path.getName(start + index).toString();
            if (text.length() != HEX_WIDTH) return null;
            for (int i = 0; i < HEX_WIDTH; ++i) {
                char digit = text.charAt(i);
                if (!(digit >= '0' && digit <= '9' || digit >= 'a' && digit <= 'f')) return null;
            }
            long high = Long.parseUnsignedLong(text.substring(0, 16), 16);
            long low = Long.parseUnsignedLong(text.substring(16), 16);
            int lowIndex = (COMPONENTS - 1 - index) * 2;
            if (lowIndex == Int1088.LIMBS - 1) {
                if (high != low >> 63) return null;
            } else {
                limbs[lowIndex + 1] = high;
            }
            limbs[lowIndex] = low;
        }
        return Int1088.fromFixedLimbs(limbs);
    }
}
