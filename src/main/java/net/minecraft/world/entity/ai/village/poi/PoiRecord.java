package net.minecraft.world.entity.ai.village.poi;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.util.VisibleForDebug;

public class PoiRecord {
    // POI saves use per-axis NBT arrays, without changing the shared or JSON codec.
    private static final Codec<BlockPos> POSITION_CODEC = new Codec<>() {
        public <T> DataResult<Pair<BlockPos, T>> decode(DynamicOps<T> ops, T input) {
            if (input instanceof Tag) {
                if (!(input instanceof CompoundTag tag) || !tag.contains("Y", Tag.TAG_INT)) {
                    return DataResult.error("Expected POI position compound with X/Z LongArray[17] and Y int");
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
    private final BlockPos pos;
    private final Holder<PoiType> poiType;
    private int freeTickets;
    private final Runnable setDirty;

    public static Codec<PoiRecord> codec(Runnable pExecutable) {
        return RecordCodecBuilder.create((p_27246_) -> {
            return p_27246_.group(POSITION_CODEC.fieldOf("pos").forGetter((p_148673_) -> {
                               return p_148673_.pos;
                           }),
                                   RegistryFixedCodec.create(Registry.POINT_OF_INTEREST_TYPE_REGISTRY).fieldOf("type").forGetter((p_218017_) -> {
                                       return p_218017_.poiType;
                                   }),
                                   Codec.INT.fieldOf("free_tickets").orElse(0).forGetter((p_148669_) -> {
                                       return p_148669_.freeTickets;
                                   }),
                                   RecordCodecBuilder.point(pExecutable))
                    .apply(p_27246_, PoiRecord::new);
        });
    }

    private PoiRecord(BlockPos p_218008_, Holder<PoiType> p_218009_, int p_218010_, Runnable p_218011_) {
        this.pos = p_218008_.immutable();
        this.poiType = p_218009_;
        this.freeTickets = p_218010_;
        this.setDirty = p_218011_;
    }

    public PoiRecord(BlockPos pPod, Holder<PoiType> pPoiType, Runnable pSetDirty) {
        this(pPod, pPoiType, pPoiType.value().maxTickets(), pSetDirty);
    }

    /** @deprecated */
    @Deprecated
    @VisibleForDebug
    public int getFreeTickets() {
        return this.freeTickets;
    }

    protected boolean acquireTicket() {
        if (this.freeTickets <= 0) {
            return false;
        } else {
            --this.freeTickets;
            this.setDirty.run();
            return true;
        }
    }

    protected boolean releaseTicket() {
        if (this.freeTickets >= this.poiType.value().maxTickets()) {
            return false;
        } else {
            ++this.freeTickets;
            this.setDirty.run();
            return true;
        }
    }

    public boolean hasSpace() {
        return this.freeTickets > 0;
    }

    public boolean isOccupied() {
        return this.freeTickets != this.poiType.value().maxTickets();
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public Holder<PoiType> getPoiType() {
        return this.poiType;
    }

    public boolean equals(Object pOther) {
        if (this == pOther) {
            return true;
        } else {
            return pOther != null && this.getClass() == pOther.getClass() ? Objects.equals(this.pos, ((PoiRecord) pOther).pos) : false;
        }
    }

    public int hashCode() {
        return this.pos.hashCode();
    }
}
