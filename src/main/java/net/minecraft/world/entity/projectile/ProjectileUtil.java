package net.minecraft.world.entity.projectile;

import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.SectorAABB;
import net.minecraft.world.phys.SectorPhysicsOrigin;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public final class ProjectileUtil {
    public static HitResult getHitResult(Entity projectile, Predicate<Entity> predicate) {
        Vec3 movement = projectile.getDeltaMovement();
        SectorVec3 exactStart = projectile.sectorPosition();
        SectorVec3 exactEnd = exactStart.add(movement.x, movement.y, movement.z);
        HitResult blockHit = net.minecraft.world.level.SectorClipper.clip(projectile.level, exactStart, exactEnd, projectile,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE);
        SectorVec3 exactBlockHit = blockHit.getExactLocation();
        SectorVec3 rayEnd = blockHit.getType() == HitResult.Type.MISS || exactBlockHit == null ? exactEnd : exactBlockHit;
        EntityHitResult entityHit = getSectorEntityHitResult(projectile, exactStart, rayEnd, predicate, 1.0D);
        if (entityHit == null)
            return blockHit;
        SectorVec3 exactEntityHit = entityHit.getExactLocation();
        double entityDistance = exactEntityHit == null ? Double.MAX_VALUE : exactEntityHit.relativeTo(exactStart).lengthSqr();
        double blockDistance = exactBlockHit == null ? Double.MAX_VALUE : exactBlockHit.relativeTo(exactStart).lengthSqr();
        return entityDistance < blockDistance ? entityHit : blockHit;
    }

    /**
    * Exact X/Z entity raycast for projectiles. Candidate boxes are translated
    * into the projectile's local frame before clipping the short motion ray.
    */
    @Nullable
    public static EntityHitResult getSectorEntityHitResult(Entity projectile, SectorVec3 start, SectorVec3 end,
            Predicate<Entity> predicate, double inflation) {
        return getSectorEntityHitResult(projectile, start, end, predicate, inflation, Double.MAX_VALUE);
    }

    @Nullable
    public static EntityHitResult getSectorEntityHitResult(Entity projectile, SectorVec3 start, SectorVec3 end,
            Predicate<Entity> predicate, double inflation,
            double maximumDistanceSqr) {
        SectorPhysicsOrigin origin = SectorPhysicsOrigin.from(start);
        Vec3 localStart = origin.toLocal(start);
        Vec3 localEnd = origin.toLocal(end);
        SectorAABB query = SectorAABB.around(start, 0.0D, 0.0D).expandTowards(localEnd.x - localStart.x, localEnd.y - localStart.y, localEnd.z - localStart.z).inflate(inflation, inflation, inflation);
        AABB localQuery = query.toLocalAABB(origin);
        double closest = maximumDistanceSqr;
        Entity closestEntity = null;
        Vec3 closestHit = null;

        for (Entity candidate : projectile.level.getSectorEntities(projectile, query, localQuery, origin, predicate)) {
            if (candidate == projectile)
                continue;
            SectorAABB candidateBox = candidate.getSectorBoundingBoxForCulling();
            if (candidateBox == null)
                continue;
            AABB localCandidateBox = candidateBox.toLocalAABB(origin).inflate(candidate.getPickRadius());
            if (!localCandidateBox.intersects(localQuery))
                continue;
            Optional<Vec3> hit = localCandidateBox.clip(localStart, localEnd);
            if (localCandidateBox.contains(localStart)) {
                if (closest >= 0.0D) {
                    closestEntity = candidate;
                    closestHit = localStart;
                    closest = 0.0D;
                }
            } else if (hit.isPresent()) {
                Vec3 hitPosition = hit.get();
                double distance = localStart.distanceToSqr(hitPosition);
                if (distance < closest || closest == 0.0D) {
                    if (candidate.getRootVehicle() == projectile.getRootVehicle()) {
                        if (closest == 0.0D) {
                            closestEntity = candidate;
                            closestHit = hitPosition;
                        }
                    } else {
                        closestEntity = candidate;
                        closestHit = hitPosition;
                        closest = distance;
                    }
                }
            }
        }

        return closestEntity == null ? null : new EntityHitResult(closestEntity, SectorVec3.fromBlockAndFraction(origin.originBlockX(), closestHit.x, (double) origin.originBlockY() + closestHit.y, origin.originBlockZ(), closestHit.z));
    }

    @Nullable
    public static EntityHitResult getEntityHitResult(Entity pShooter, Vec3 pStartVec, Vec3 pEndVec, AABB pBoundingBox, Predicate<Entity> pFilter, double pDistance) {
        if (pShooter.hasSectorPosition()) {
            SectorVec3 exactStart = pShooter.exactEyePosition();
            Vec3 localDelta = pEndVec.subtract(pStartVec);
            SectorVec3 exactEnd = exactStart.add(localDelta.x, localDelta.y, localDelta.z);
            EntityHitResult result = getSectorEntityHitResult(pShooter, exactStart, exactEnd, pFilter, 1.0D,
                    pDistance);
            return result;
        }
        Level level = pShooter.level;
        double d0 = pDistance;
        Entity entity = null;
        Vec3 vec3 = null;

        for (Entity entity1 : level.getEntities(pShooter, pBoundingBox, pFilter)) {
            AABB aabb = entity1.exactBoundingBox().inflate((double) entity1.getPickRadius());
            Optional<Vec3> optional = aabb.clip(pStartVec, pEndVec);
            if (aabb.contains(pStartVec)) {
                if (d0 >= 0.0D) {
                    entity = entity1;
                    vec3 = optional.orElse(pStartVec);
                    d0 = 0.0D;
                }
            } else if (optional.isPresent()) {
                Vec3 vec31 = optional.get();
                double d1 = pStartVec.distanceToSqr(vec31);
                if (d1 < d0 || d0 == 0.0D) {
                    if (entity1.getRootVehicle() == pShooter.getRootVehicle()) {
                        if (d0 == 0.0D) {
                            entity = entity1;
                            vec3 = vec31;
                        }
                    } else {
                        entity = entity1;
                        vec3 = vec31;
                        d0 = d1;
                    }
                }
            }
        }

        return entity == null ? null : new EntityHitResult(entity, vec3);
    }

    @Nullable
    public static EntityHitResult getEntityHitResult(Level pLevel, Entity pProjectile, Vec3 pStartVec, Vec3 pEndVec, AABB pBoundingBox, Predicate<Entity> pFilter) {
        return getEntityHitResult(pLevel, pProjectile, pStartVec, pEndVec, pBoundingBox, pFilter, 0.3F);
    }

    @Nullable
    public static EntityHitResult getEntityHitResult(Level pLevel, Entity pProjectile, Vec3 pStartVec, Vec3 pEndVec, AABB pBoundingBox, Predicate<Entity> pFilter, float pInflationAmount) {
        double d0 = Double.MAX_VALUE;
        Entity entity = null;

        for (Entity entity1 : pLevel.getEntities(pProjectile, pBoundingBox, pFilter)) {
            AABB aabb = entity1.exactBoundingBox().inflate((double) pInflationAmount);
            Optional<Vec3> optional = aabb.clip(pStartVec, pEndVec);
            if (optional.isPresent()) {
                double d1 = pStartVec.distanceToSqr(optional.get());
                if (d1 < d0) {
                    entity = entity1;
                    d0 = d1;
                }
            }
        }

        return entity == null ? null : new EntityHitResult(entity);
    }

    public static void rotateTowardsMovement(Entity pProjectile, float pRotationSpeed) {
        Vec3 vec3 = pProjectile.getDeltaMovement();
        if (vec3.lengthSqr() != 0.0D) {
            double d0 = vec3.horizontalDistance();
            pProjectile.setYRot((float) (Mth.atan2(vec3.z, vec3.x) * (double) (180F / (float) Math.PI)) + 90.0F);
            pProjectile.setXRot((float) (Mth.atan2(d0, vec3.y) * (double) (180F / (float) Math.PI)) - 90.0F);

            while (pProjectile.getXRot() - pProjectile.xRotO < -180.0F) {
                pProjectile.xRotO -= 360.0F;
            }

            while (pProjectile.getXRot() - pProjectile.xRotO >= 180.0F) {
                pProjectile.xRotO += 360.0F;
            }

            while (pProjectile.getYRot() - pProjectile.yRotO < -180.0F) {
                pProjectile.yRotO -= 360.0F;
            }

            while (pProjectile.getYRot() - pProjectile.yRotO >= 180.0F) {
                pProjectile.yRotO += 360.0F;
            }

            pProjectile.setXRot(Mth.lerp(pRotationSpeed, pProjectile.xRotO, pProjectile.getXRot()));
            pProjectile.setYRot(Mth.lerp(pRotationSpeed, pProjectile.yRotO, pProjectile.getYRot()));
        }
    }

    public static InteractionHand getWeaponHoldingHand(LivingEntity pShooter, Item pWeapon) {
        return pShooter.getMainHandItem().is(pWeapon) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    public static AbstractArrow getMobArrow(LivingEntity pShooter, ItemStack pArrowStack, float pVelocity) {
        ArrowItem arrowitem = (ArrowItem) (pArrowStack.getItem() instanceof ArrowItem ? pArrowStack.getItem() : Items.ARROW);
        AbstractArrow abstractarrow = arrowitem.createArrow(pShooter.level, pArrowStack, pShooter);
        abstractarrow.setEnchantmentEffectsFromEntity(pShooter, pVelocity);
        if (pArrowStack.is(Items.TIPPED_ARROW) && abstractarrow instanceof Arrow) {
            ((Arrow) abstractarrow).setEffectsFromItem(pArrowStack);
        }

        return abstractarrow;
    }
}
