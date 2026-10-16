package net.minecraft.resources;

import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Registry;

public class ResourceKey<T> {
    private static final Map<String, ResourceKey<?>> VALUES = Collections.synchronizedMap(Maps.newIdentityHashMap());
    private final ResourceLocation registryName;
    private final ResourceLocation location;

    public static <T> Codec<ResourceKey<T>> codec(ResourceKey<? extends Registry<T>> pRegistryKey) {
        return ResourceLocation.CODEC.xmap((p_195979_) -> {
            return create(pRegistryKey, p_195979_);
        }, ResourceKey::location);
    }

    public static <T> ResourceKey<T> create(ResourceKey<? extends Registry<T>> pRegistryKey, ResourceLocation pLocation) {
        return create(pRegistryKey.location, pLocation);
    }

    public static <T> ResourceKey<Registry<T>> createRegistryKey(ResourceLocation pLocation) {
        return create(Registry.ROOT_REGISTRY_NAME, pLocation);
    }

    private static <T> ResourceKey<T> create(ResourceLocation pRegistryName, ResourceLocation pLocation) {
        String s = (pRegistryName + ":" + pLocation).intern();
        return (ResourceKey<T>) VALUES.computeIfAbsent(s, (p_195971_) -> {
            return new ResourceKey(pRegistryName, pLocation);
        });
    }

    private ResourceKey(ResourceLocation pRegistryName, ResourceLocation pLocation) {
        this.registryName = pRegistryName;
        this.location = pLocation;
    }

    public String toString() {
        return "ResourceKey[" + this.registryName + " / " + this.location + "]";
    }

    public boolean isFor(ResourceKey<? extends Registry<?>> pRegistryKey) {
        return this.registryName.equals(pRegistryKey.location());
    }

    public <E> Optional<ResourceKey<E>> cast(ResourceKey<? extends Registry<E>> pRegistryKey) {
        return this.isFor(pRegistryKey) ? Optional.of((ResourceKey<E>) this) : Optional.empty();
    }

    public ResourceLocation location() {
        return this.location;
    }

    public ResourceLocation registry() {
        return this.registryName;
    }
}
