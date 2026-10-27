package net.minecraft.world.level.storage.loot.predicates;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.minecraft.advancements.critereon.LocationPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public class LocationCheck implements LootItemCondition {
    final LocationPredicate predicate;
    final BlockPos offset;

    LocationCheck(LocationPredicate pLocationPredicate, BlockPos pOffset) {
        this.predicate = pLocationPredicate;
        this.offset = pOffset;
    }

    public LootItemConditionType getType() {
        return LootItemConditions.LOCATION_CHECK;
    }

    public boolean test(LootContext p_81731_) {
        Vec3 vec3 = p_81731_.getParamOrNull(LootContextParams.ORIGIN);
        if (vec3 == null) {
            return false;
        }
        var origin = net.minecraft.world.phys.ExactVec3.position(vec3);
        var position = net.minecraft.world.phys.SectorVec3.fromBlockAndFraction(
                origin.blockX().addSaturated(this.offset.getX()), origin.subX(),
                origin.y() + this.offset.getY(),
                origin.blockZ().addSaturated(this.offset.getZ()), origin.subZ());
        return this.predicate.matches(p_81731_.getLevel(), position);
    }

    public static LootItemCondition.Builder checkLocation(LocationPredicate.Builder pLocationPredicateBuilder) {
        return () -> {
            return new LocationCheck(pLocationPredicateBuilder.build(), BlockPos.ZERO);
        };
    }

    public static LootItemCondition.Builder checkLocation(LocationPredicate.Builder pLocationPredicateBuilder, BlockPos pOffset) {
        return () -> {
            return new LocationCheck(pLocationPredicateBuilder.build(), pOffset);
        };
    }

    public static class Serializer implements net.minecraft.world.level.storage.loot.Serializer<LocationCheck> {
        public void serialize(JsonObject p_81749_, LocationCheck p_81750_, JsonSerializationContext p_81751_) {
            p_81749_.add("predicate", p_81750_.predicate.serializeToJson());
            if (!p_81750_.offset.getX().equals(net.minecraft.core.Int1088.ZERO)) {
                p_81749_.addProperty("offsetX", localOffset(p_81750_.offset.getX()));
            }

            if (p_81750_.offset.getY() != 0) {
                p_81749_.addProperty("offsetY", p_81750_.offset.getY());
            }

            if (!p_81750_.offset.getZ().equals(net.minecraft.core.Int1088.ZERO)) {
                p_81749_.addProperty("offsetZ", localOffset(p_81750_.offset.getZ()));
            }
        }

        private static int localOffset(net.minecraft.core.Int1088 value) {
            if (!value.fitsLong()) throw new ArithmeticException("Offset does not fit int");
            int local = Math.toIntExact(value.lowLimb());
            if (!value.equals(net.minecraft.core.Int1088.fromLong(local))) {
                throw new IllegalArgumentException("Loot location offset exceeds signed int range");
            }
            return local;
        }

        public LocationCheck deserialize(JsonObject p_81757_, JsonDeserializationContext p_81758_) {
            LocationPredicate locationpredicate = LocationPredicate.fromJson(p_81757_.get("predicate"));
            int i = GsonHelper.getAsInt(p_81757_, "offsetX", 0);
            int j = GsonHelper.getAsInt(p_81757_, "offsetY", 0);
            int k = GsonHelper.getAsInt(p_81757_, "offsetZ", 0);
            return new LocationCheck(locationpredicate, new BlockPos(i, j, k));
        }
    }
}
