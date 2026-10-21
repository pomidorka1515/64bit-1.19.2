package net.minecraft.world.level.levelgen.structure.pools;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.Int128;
import java.util.Objects;

public class JigsawJunction {
    private final Int128 sourceX;
    private final int sourceGroundY;
    private final Int128 sourceZ;
    private final int deltaY;
    private final StructureTemplatePool.Projection destProjection;

    public JigsawJunction(long pSourceX, int pSourceGroundY, long pSourceZ, int pDeltaY, StructureTemplatePool.Projection pDestProjection) {
        this(Int128.fromLong(pSourceX), pSourceGroundY, Int128.fromLong(pSourceZ), pDeltaY, pDestProjection);
    }

    public JigsawJunction(Int128 pSourceX, int pSourceGroundY, Int128 pSourceZ, int pDeltaY, StructureTemplatePool.Projection pDestProjection) {
        this.sourceX = pSourceX;
        this.sourceGroundY = pSourceGroundY;
        this.sourceZ = pSourceZ;
        this.deltaY = pDeltaY;
        this.destProjection = pDestProjection;
    }

    public Int128 getSourceX() {
        return this.sourceX;
    }

    public int getSourceGroundY() {
        return this.sourceGroundY;
    }

    public Int128 getSourceZ() {
        return this.sourceZ;
    }

    public int getDeltaY() {
        return this.deltaY;
    }

    public StructureTemplatePool.Projection getDestProjection() {
        return this.destProjection;
    }

    public <T> Dynamic<T> serialize(DynamicOps<T> pOps) {
        ImmutableMap.Builder<T, T> builder = ImmutableMap.builder();
        builder.put(pOps.createString("source_x"), pOps.createLong(this.sourceX.lo()))
                .put(pOps.createString("source_xHi"), pOps.createLong(this.sourceX.hi()))
                .put(pOps.createString("source_z"), pOps.createLong(this.sourceZ.lo()))
                .put(pOps.createString("source_zHi"), pOps.createLong(this.sourceZ.hi()))
                .put(pOps.createString("source_ground_y"), pOps.createInt(this.sourceGroundY))
                .put(pOps.createString("delta_y"), pOps.createInt(this.deltaY)).put(pOps.createString("dest_proj"), pOps.createString(this.destProjection.getName()));
        return new Dynamic<>(pOps, pOps.createMap(builder.build()));
    }

    public static <T> JigsawJunction deserialize(Dynamic<T> pDynamic) {
        return new JigsawJunction(readCoordinate(pDynamic, "source_x"), pDynamic.get("source_ground_y").asInt(0), readCoordinate(pDynamic, "source_z"), pDynamic.get("delta_y").asInt(0), StructureTemplatePool.Projection.byName(pDynamic.get("dest_proj").asString("")));
    }

    private static <T> Int128 readCoordinate(Dynamic<T> dynamic, String key) {
        // NBT must use exactly the same required LONG pair as NbtUtils.readInt128.
        if (dynamic.getValue() instanceof net.minecraft.nbt.CompoundTag tag) {
            return net.minecraft.nbt.NbtUtils.readInt128(tag, key);
        }
        long low = dynamic.get(key).asNumber().result().orElseThrow(() -> new IllegalArgumentException("Missing " + key)).longValue();
        long high = dynamic.get(key + "Hi").asNumber().result().orElseThrow(() -> new IllegalArgumentException("Missing " + key + "Hi")).longValue();
        return Int128.of(high, low);
    }

    public boolean equals(Object pOther) {
        if (this == pOther) {
            return true;
        } else if (pOther != null && this.getClass() == pOther.getClass()) {
            JigsawJunction jigsawjunction = (JigsawJunction) pOther;
            if (!this.sourceX.equals(jigsawjunction.sourceX)) {
                return false;
            } else if (!this.sourceZ.equals(jigsawjunction.sourceZ)) {
                return false;
            } else if (this.deltaY != jigsawjunction.deltaY) {
                return false;
            } else {
                return this.sourceGroundY == jigsawjunction.sourceGroundY && this.destProjection == jigsawjunction.destProjection;
            }
        } else {
            return false;
        }
    }

    public int hashCode() {
        return Objects.hash(this.sourceX, this.sourceGroundY, this.sourceZ, this.deltaY, this.destProjection);
    }

    public String toString() {
        return "JigsawJunction{sourceX=" + this.sourceX + ", sourceGroundY=" + this.sourceGroundY + ", sourceZ=" + this.sourceZ + ", deltaY=" + this.deltaY + ", destProjection=" + this.destProjection + "}";
    }
}
