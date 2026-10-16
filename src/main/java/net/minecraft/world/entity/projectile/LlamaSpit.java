package net.minecraft.world.entity.projectile;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class LlamaSpit extends Projectile {
    public LlamaSpit(EntityType<? extends LlamaSpit> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    public LlamaSpit(Level pLevel, Llama pSpitter) {
        this(EntityType.LLAMA_SPIT, pLevel);
        this.setOwner(pSpitter);
        double offset = (double) (pSpitter.getBbWidth() + 1.0F) * 0.5D;
        this.applyExactPosition(pSpitter.sectorPosition().add(-offset * (double) Mth.sin(pSpitter.yBodyRot * ((float) Math.PI / 180F)),
                pSpitter.getEyeY() - (double) 0.1F - pSpitter.getY(),
                offset * (double) Mth.cos(pSpitter.yBodyRot * ((float) Math.PI / 180F))));
    }

    public void tick() {
        super.tick();
        Vec3 vec3 = this.getDeltaMovement();
        HitResult hitresult = ProjectileUtil.getHitResult(this, this::canHitEntity);
        this.onHit(hitresult);
        SectorVec3 nextPosition = this.sectorPosition().add(vec3.x, vec3.y, vec3.z);
        this.updateRotation();
        float f = 0.99F;
        float f1 = 0.06F;
        if (this.level.getBlockStates(this.getBoundingBox()).noneMatch(BlockBehaviour.BlockStateBase::isAir)) {
            this.discard();
        } else if (this.isInWaterOrBubble()) {
            this.discard();
        } else {
            this.setDeltaMovement(vec3.scale((double) 0.99F));
            if (!this.isNoGravity()) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, (double) -0.06F, 0.0D));
            }

            this.applyExactPosition(nextPosition);
        }
    }

    protected void onHitEntity(EntityHitResult pResult) {
        super.onHitEntity(pResult);
        Entity entity = this.getOwner();
        if (entity instanceof LivingEntity) {
            pResult.getEntity().hurt(DamageSource.indirectMobAttack(this, (LivingEntity) entity).setProjectile(), 1.0F);
        }
    }

    protected void onHitBlock(BlockHitResult pResult) {
        super.onHitBlock(pResult);
        if (!this.level.isClientSide) {
            this.discard();
        }
    }

    protected void defineSynchedData() {
    }

    public void recreateFromPacket(ClientboundAddEntityPacket pPacket) {
        super.recreateFromPacket(pPacket);
        double d0 = pPacket.getXa();
        double d1 = pPacket.getYa();
        double d2 = pPacket.getZa();

        for (int i = 0; i < 7; ++i) {
            double d3 = 0.4D + 0.1D * (double) i;
            this.level.addParticle(ParticleTypes.SPIT, this.particlePosition(0.0D, 0.0D, 0.0D),
                    d0 * d3, d1, d2 * d3);
        }

        this.setDeltaMovement(d0, d1, d2);
    }
}
