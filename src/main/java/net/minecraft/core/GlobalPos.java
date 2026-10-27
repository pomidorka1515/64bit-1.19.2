package net.minecraft.core;

import com.mojang.serialization.Codec;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class GlobalPos {
    /** NBT persistence uses per-axis arrays; other formats keep the shared position codec. */
    private static final Codec<BlockPos> POSITION_CODEC = new Codec<>() {
        public <T> DataResult<Pair<BlockPos, T>> decode(DynamicOps<T> ops, T input) {
            if (input instanceof Tag) {
                if (!(input instanceof CompoundTag tag) || !tag.contains("Y", Tag.TAG_INT)) {
                    return DataResult.error("Expected GlobalPos position compound with X/Z LongArray[17] and Y int");
                }
                try {
                    return DataResult.success(Pair.of(NbtUtils.readBlockPos(tag), ops.empty()));
                } catch (IllegalArgumentException error) {
                    return DataResult.error(error.getMessage());
                }
            }
            return BlockPos.CODEC.decode(ops, input);
        }

        public <T> DataResult<T> encode(BlockPos value, DynamicOps<T> ops, T prefix) {
            if (ops.empty() instanceof Tag) {
                T encoded = NbtOps.INSTANCE.convertTo(ops, NbtUtils.writeBlockPos(value));
                return ops.getMap(encoded).flatMap(map -> ops.mergeToMap(prefix, map));
            }
            return BlockPos.CODEC.encode(value, ops, prefix);
        }
    };
    public static final Codec<GlobalPos> CODEC = RecordCodecBuilder.create((p_122642_) -> {
        return p_122642_.group(Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(GlobalPos::dimension), POSITION_CODEC.fieldOf("pos").forGetter(GlobalPos::pos)).apply(p_122642_, GlobalPos::of);
    });
    private final ResourceKey<Level> dimension;
    private final BlockPos pos;

    private GlobalPos(ResourceKey<Level> pDimension, BlockPos pPos) {
        this.dimension = pDimension;
        this.pos = pPos;
    }

    public static GlobalPos of(ResourceKey<Level> p_122644_, BlockPos p_122645_) {
        return new GlobalPos(p_122644_, p_122645_);
    }

    public ResourceKey<Level> dimension() {
        return this.dimension;
    }

    public BlockPos pos() {
        return this.pos;
    }

    public boolean equals(Object pOther) {
        if (this == pOther) {
            return true;
        } else if (pOther != null && this.getClass() == pOther.getClass()) {
            GlobalPos globalpos = (GlobalPos) pOther;
            return Objects.equals(this.dimension, globalpos.dimension) && Objects.equals(this.pos, globalpos.pos);
        } else {
            return false;
        }
    }

    public int hashCode() {
        return Objects.hash(this.dimension, this.pos);
    }

    public String toString() {
        return this.dimension + " " + this.pos;
    }
}
