package net.minecraft.world.level.levelgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

/** Strict persistence boundaries; only sampling, never saved world identity, projects to low 64 bits. */
public final class WorldgenPlacementCodecs {
    private WorldgenPlacementCodecs() {
    }

    /** Unlike optionalFieldOf, a present but invalid placement must not become an absent one. */
    public static <A> MapCodec<Optional<A>> optionalField(Codec<A> codec, String name) {
        return new MapCodec<Optional<A>>() {
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.of(ops.createString(name));
            }

            public <T> DataResult<Optional<A>> decode(DynamicOps<T> ops, MapLike<T> input) {
                T value = input.get(name);
                if (value == null) {
                    // JsonOps.get treats explicit null as missing; the persisted key still makes it invalid.
                    boolean present = input.entries().anyMatch(entry -> ops.getStringValue(entry.getFirst()).result().filter(name::equals).isPresent());
                    return present ? DataResult.error("Invalid null placement field: " + name) : DataResult.success(Optional.empty());
                }
                return codec.parse(ops, value).map(Optional::of);
            }

            public <T> RecordBuilder<T> encode(Optional<A> value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return value.isPresent() ? prefix.add(name, codec.encodeStart(ops, value.get())) : prefix;
            }
        };
    }

    public static <A> MapCodec<A> optionalField(Codec<A> codec, String name, A defaultValue) {
        return optionalField(codec, name).xmap(value -> value.orElse(defaultValue),
                value -> value.equals(defaultValue) ? Optional.empty() : Optional.of(value));
    }

    /** DynamicOps numeric readers otherwise silently truncate fractions and overflowing foreign numbers. */
    public static long exactLong(Number value) {
        try {
            return new BigDecimal(value.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Placement coordinate half must be an exact signed long", exception);
        }
    }
}
