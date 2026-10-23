package net.minecraft.world.entity;

import net.minecraft.server.level.ServerLevel;

/** Minimal server-side flag for NBT-only tests; no world/chunk operations are supported. */
public final class EntityLoadingTestLevel {
    private EntityLoadingTestLevel() {}

    public static ServerLevel create() {
        try {
            java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (ServerLevel) ((sun.misc.Unsafe) field.get(null)).allocateInstance(ServerLevel.class);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
