package net.minecraft.world.level;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class Explosion {
   private static final ExplosionDamageCalculator EXPLOSION_DAMAGE_CALCULATOR = new ExplosionDamageCalculator();
   private static final int MAX_DROPS_PER_COMBINED_STACK = 16;
   private final boolean fire;
   private final Explosion.BlockInteraction blockInteraction;
   private final RandomSource random = RandomSource.create();
   private final Level level;
   /** Exact explosion origin; legacy x/y/z access is derived only at compatibility boundaries. */
   private final SectorVec3 position;
   @Nullable
   private final Entity source;
   private final float radius;
   private final DamageSource damageSource;
   private final ExplosionDamageCalculator damageCalculator;
   private final ObjectArrayList<BlockPos> toBlow = new ObjectArrayList<>();
   private final Map<Player, Vec3> hitPlayers = Maps.newHashMap();

   /** Legacy absolute-double construction boundary. Prefer the SectorVec3 constructors. */
   public Explosion(Level p_151471_, @Nullable Entity p_151472_, double p_151473_, double p_151474_, double p_151475_, float p_151476_) {
      this(p_151471_, p_151472_, SectorVec3.fromApproximate(p_151473_, p_151474_, p_151475_), p_151476_, false, Explosion.BlockInteraction.DESTROY);
   }

   public Explosion(Level level, @Nullable Entity source, SectorVec3 position, float radius) {
      this(level, source, position, radius, false, Explosion.BlockInteraction.DESTROY);
   }

   /** Legacy absolute-double construction boundary. Prefer the SectorVec3 constructors. */
   public Explosion(Level p_46024_, @Nullable Entity p_46025_, double p_46026_, double p_46027_, double p_46028_, float p_46029_, List<BlockPos> p_46030_) {
      this(p_46024_, p_46025_, SectorVec3.fromApproximate(p_46026_, p_46027_, p_46028_), p_46029_, p_46030_);
   }

   public Explosion(Level level, @Nullable Entity source, SectorVec3 position, float radius, List<BlockPos> toBlow) {
      this(level, source, position, radius, false, Explosion.BlockInteraction.DESTROY, toBlow);
   }

   /** Legacy absolute-double construction boundary. Prefer the SectorVec3 constructors. */
   public Explosion(Level p_46041_, @Nullable Entity p_46042_, double p_46043_, double p_46044_, double p_46045_, float p_46046_, boolean p_46047_, Explosion.BlockInteraction p_46048_, List<BlockPos> p_46049_) {
      this(p_46041_, p_46042_, SectorVec3.fromApproximate(p_46043_, p_46044_, p_46045_), p_46046_, p_46047_, p_46048_, p_46049_);
   }

   public Explosion(Level level, @Nullable Entity source, SectorVec3 position, float radius, boolean fire,
                    Explosion.BlockInteraction blockInteraction, List<BlockPos> toBlow) {
      this(level, source, position, radius, fire, blockInteraction);
      this.toBlow.addAll(toBlow);
   }

   /** Legacy absolute-double construction boundary. Prefer the SectorVec3 constructors. */
   public Explosion(Level p_46032_, @Nullable Entity p_46033_, double p_46034_, double p_46035_, double p_46036_, float p_46037_, boolean p_46038_, Explosion.BlockInteraction p_46039_) {
      this(p_46032_, p_46033_, SectorVec3.fromApproximate(p_46034_, p_46035_, p_46036_), p_46037_, p_46038_, p_46039_);
   }

   public Explosion(Level level, @Nullable Entity source, SectorVec3 position, float radius, boolean fire,
                    Explosion.BlockInteraction blockInteraction) {
      this(level, source, (DamageSource)null, (ExplosionDamageCalculator)null, position, radius, fire, blockInteraction);
   }

   /** Legacy absolute-double construction boundary. Prefer the SectorVec3 constructors. */
   public Explosion(Level p_46051_, @Nullable Entity p_46052_, @Nullable DamageSource p_46053_, @Nullable ExplosionDamageCalculator p_46054_, double p_46055_, double p_46056_, double p_46057_, float p_46058_, boolean p_46059_, Explosion.BlockInteraction p_46060_) {
      this(p_46051_, p_46052_, p_46053_, p_46054_, SectorVec3.fromApproximate(p_46055_, p_46056_, p_46057_), p_46058_, p_46059_, p_46060_);
   }

   public Explosion(Level level, @Nullable Entity source, @Nullable DamageSource damageSource,
                    @Nullable ExplosionDamageCalculator damageCalculator, SectorVec3 position, float radius,
                    boolean fire, Explosion.BlockInteraction blockInteraction) {
      if (position == null) throw new NullPointerException("position");
      this.level = level;
      this.source = source;
      this.position = position;
      this.radius = radius;
      this.fire = fire;
      this.blockInteraction = blockInteraction;
      this.damageSource = damageSource == null ? DamageSource.explosion(this) : damageSource;
      this.damageCalculator = damageCalculator == null ? this.makeDamageCalculator(source) : damageCalculator;
   }

   private ExplosionDamageCalculator makeDamageCalculator(@Nullable Entity p_46063_) {
      return (ExplosionDamageCalculator)(p_46063_ == null ? EXPLOSION_DAMAGE_CALCULATOR : new EntityBasedExplosionDamageCalculator(p_46063_));
   }

   public void explode() {
      this.level.gameEvent(this.source, GameEvent.EXPLODE, this.position.blockPosition());
      Set<BlockPos> set = Sets.newHashSet();
      int i = 16;

      for(int j = 0; j < 16; ++j) {
         for(int k = 0; k < 16; ++k) {
            for(int l = 0; l < 16; ++l) {
               if (j == 0 || j == 15 || k == 0 || k == 15 || l == 0 || l == 15) {
                  double d0 = (double)((float)j / 15.0F * 2.0F - 1.0F);
                  double d1 = (double)((float)k / 15.0F * 2.0F - 1.0F);
                  double d2 = (double)((float)l / 15.0F * 2.0F - 1.0F);
                  double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                  d0 /= d3;
                  d1 /= d3;
                  d2 /= d3;
                  float f = this.radius * (0.7F + this.level.random.nextFloat() * 0.6F);
                  SectorVec3 rayPosition = this.position;

                  for(float f1 = 0.3F; f > 0.0F; f -= 0.22500001F) {
                     BlockPos blockpos = rayPosition.blockPosition();
                     BlockState blockstate = this.level.getBlockState(blockpos);
                     FluidState fluidstate = this.level.getFluidState(blockpos);
                     if (!this.level.isInWorldBounds(blockpos)) {
                        break;
                     }

                     Optional<Float> optional = this.damageCalculator.getBlockExplosionResistance(this, this.level, blockpos, blockstate, fluidstate);
                     if (optional.isPresent()) {
                        f -= (optional.get() + 0.3F) * 0.3F;
                     }

                     if (f > 0.0F && this.damageCalculator.shouldBlockExplode(this, this.level, blockpos, blockstate, f)) {
                        set.add(blockpos);
                     }

                     rayPosition = rayPosition.add(d0 * (double)0.3F, d1 * (double)0.3F,
                           d2 * (double)0.3F);
                  }
               }
            }
         }
      }

      this.toBlow.addAll(set);
      float f2 = this.radius * 2.0F;
      long minX = this.position.add(-(double)f2 - 1.0D, 0.0D, 0.0D).blockX();
      long maxX = this.position.add((double)f2 + 1.0D, 0.0D, 0.0D).blockX();
      int minY = Mth.floor(this.position.y() - (double)f2 - 1.0D);
      int maxY = Mth.floor(this.position.y() + (double)f2 + 1.0D);
      long minZ = this.position.add(0.0D, 0.0D, -(double)f2 - 1.0D).blockZ();
      long maxZ = this.position.add(0.0D, 0.0D, (double)f2 + 1.0D).blockZ();

      for(Entity entity : this.level.getEntities().getAll()) {
         this.hurtEntity(entity, f2, minX, maxX, minY, maxY, minZ, maxZ);
         if (entity instanceof EnderDragon dragon) {
            for(EnderDragonPart part : dragon.getSubEntities()) {
               if (part != this.source) {
                  this.hurtEntity(part, f2, minX, maxX, minY, maxY, minZ, maxZ);
               }
            }
         }
      }

   }

   private void hurtEntity(Entity entity, float blastDiameter, long minX, long maxX, int minY, int maxY,
                           long minZ, long maxZ) {
      if (entity == this.source || entity.ignoreExplosion()) return;
      SectorVec3 entityPosition = entity.exactPosition();
      if (entityPosition == null) {
         entityPosition = SectorVec3.fromApproximate(entity.getX(), entity instanceof PrimedTnt ? entity.getY() : entity.getEyeY(), entity.getZ());
      } else if (!(entity instanceof PrimedTnt)) {
         entityPosition = entityPosition.withY(entity.getEyeY());
      }

      if (entityPosition.blockX() < minX || entityPosition.blockX() > maxX
            || entityPosition.y() < (double)minY || entityPosition.y() > (double)maxY
            || entityPosition.blockZ() < minZ || entityPosition.blockZ() > maxZ) return;

      Vec3 relativePosition = entityPosition.relativeTo(this.position);
      double normalizedDistance = Math.sqrt(relativePosition.lengthSqr()) / (double)blastDiameter;
      if (normalizedDistance > 1.0D) return;
      double length = Math.sqrt(relativePosition.lengthSqr());
      if (length == 0.0D) return;

      double xDirection = relativePosition.x / length;
      double yDirection = relativePosition.y / length;
      double zDirection = relativePosition.z / length;
      double exposure = (double)this.getSeenPercent(entity, entityPosition);
      double damageFactor = (1.0D - normalizedDistance) * exposure;
      entity.hurt(this.getDamageSource(), (float)((int)((damageFactor * damageFactor + damageFactor) / 2.0D
            * 7.0D * (double)blastDiameter + 1.0D)));
      double knockback = damageFactor;
      if (entity instanceof LivingEntity) {
         knockback = ProtectionEnchantment.getExplosionKnockbackAfterDampener((LivingEntity)entity, damageFactor);
      }

      entity.setDeltaMovement(entity.getDeltaMovement().add(xDirection * knockback, yDirection * knockback,
            zDirection * knockback));
      if (entity instanceof Player) {
         Player player = (Player)entity;
         if (!player.isSpectator() && (!player.isCreative() || !player.getAbilities().flying)) {
            this.hitPlayers.put(player, new Vec3(xDirection * damageFactor, yDirection * damageFactor,
                  zDirection * damageFactor));
         }
      }
   }

   /** Exact split-coordinate exposure sampling for explosion damage. */
   private float getSeenPercent(Entity entity, SectorVec3 entityPosition) {
      double width = (double)entity.getBbWidth();
      double height = (double)entity.getBbHeight();
      double stepX = 1.0D / (width * 2.0D + 1.0D);
      double stepY = 1.0D / (height * 2.0D + 1.0D);
      double stepZ = 1.0D / (width * 2.0D + 1.0D);
      double offsetX = (1.0D - Math.floor(1.0D / stepX) * stepX) / 2.0D;
      double offsetZ = (1.0D - Math.floor(1.0D / stepZ) * stepZ) / 2.0D;
      if (stepX < 0.0D || stepY < 0.0D || stepZ < 0.0D) return 0.0F;

      int visible = 0;
      int samples = 0;
      SectorVec3 feet = entity.exactPosition();
      double eyeOffset = entity instanceof PrimedTnt ? 0.0D : (double)entity.getEyeHeight();
      for(double sampleX = 0.0D; sampleX <= 1.0D; sampleX += stepX) {
         for(double sampleY = 0.0D; sampleY <= 1.0D; sampleY += stepY) {
            for(double sampleZ = 0.0D; sampleZ <= 1.0D; sampleZ += stepZ) {
               SectorVec3 sample;
               if (feet != null) {
                  sample = feet.add(Mth.lerp(sampleX, -(double)entity.getBbWidth() * 0.5D,
                        (double)entity.getBbWidth() * 0.5D) + offsetX,
                        Mth.lerp(sampleY, 0.0D, (double)entity.getBbHeight()),
                        Mth.lerp(sampleZ, -(double)entity.getBbWidth() * 0.5D,
                              (double)entity.getBbWidth() * 0.5D) + offsetZ);
               } else {
                  sample = entityPosition.add(Mth.lerp(sampleX, -width * 0.5D, width * 0.5D) + offsetX,
                        Mth.lerp(sampleY, -eyeOffset, height - eyeOffset),
                        Mth.lerp(sampleZ, -width * 0.5D, width * 0.5D) + offsetZ);
               }

               if (SectorClipper.clip(this.level, sample, this.position, entity,
                     ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE).getType() == HitResult.Type.MISS) {
                  ++visible;
               }
               ++samples;
            }
         }
      }
      return (float)visible / (float)samples;
   }

   public SectorVec3 getPosition() {
      return this.position;
   }

   public void finalizeExplosion(boolean p_46076_) {
      if (this.level.isClientSide) {
         // Sound has not yet received an exact origin overload; preserve its legacy boundary.
         Vec3 approximatePosition = this.position.toApproximateVec3();
         this.level.playLocalSound(approximatePosition.x, approximatePosition.y, approximatePosition.z,
               SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 4.0F,
               (1.0F + (this.level.random.nextFloat() - this.level.random.nextFloat()) * 0.2F) * 0.7F, false);
      }

      boolean flag = this.blockInteraction != Explosion.BlockInteraction.NONE;
      if (p_46076_) {
         if (!(this.radius < 2.0F) && flag) {
            this.level.addParticle(ParticleTypes.EXPLOSION_EMITTER, this.position, 1.0D, 0.0D, 0.0D);
         } else {
            this.level.addParticle(ParticleTypes.EXPLOSION, this.position, 1.0D, 0.0D, 0.0D);
         }
      }

      if (flag) {
         ObjectArrayList<Pair<ItemStack, BlockPos>> objectarraylist = new ObjectArrayList<>();
         boolean flag1 = this.getSourceMob() instanceof Player;
         Util.shuffle(this.toBlow, this.level.random);

         for(BlockPos blockpos : this.toBlow) {
            BlockState blockstate = this.level.getBlockState(blockpos);
            Block block = blockstate.getBlock();
            if (!blockstate.isAir()) {
               BlockPos blockpos1 = blockpos.immutable();
               this.level.getProfiler().push("explosion_blocks");
               if (block.dropFromExplosion(this)) {
                  Level $$9 = this.level;
                  if ($$9 instanceof ServerLevel) {
                     ServerLevel serverlevel = (ServerLevel)$$9;
                     BlockEntity blockentity = blockstate.hasBlockEntity() ? this.level.getBlockEntity(blockpos) : null;
                     LootContext.Builder lootcontext$builder = (new LootContext.Builder(serverlevel)).withRandom(this.level.random).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(blockpos)).withParameter(LootContextParams.TOOL, ItemStack.EMPTY).withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockentity).withOptionalParameter(LootContextParams.THIS_ENTITY, this.source);
                     if (this.blockInteraction == Explosion.BlockInteraction.DESTROY) {
                        lootcontext$builder.withParameter(LootContextParams.EXPLOSION_RADIUS, this.radius);
                     }

                     blockstate.spawnAfterBreak(serverlevel, blockpos, ItemStack.EMPTY, flag1);
                     blockstate.getDrops(lootcontext$builder).forEach((p_46074_) -> {
                        addBlockDrops(objectarraylist, p_46074_, blockpos1);
                     });
                  }
               }

               this.level.setBlock(blockpos, Blocks.AIR.defaultBlockState(), 3);
               block.wasExploded(this.level, blockpos, this);
               this.level.getProfiler().pop();
            }
         }

         for(Pair<ItemStack, BlockPos> pair : objectarraylist) {
            Block.popResource(this.level, pair.getSecond(), pair.getFirst());
         }
      }

      if (this.fire) {
         for(BlockPos blockpos2 : this.toBlow) {
            if (this.random.nextInt(3) == 0 && this.level.getBlockState(blockpos2).isAir() && this.level.getBlockState(blockpos2.below()).isSolidRender(this.level, blockpos2.below())) {
               this.level.setBlockAndUpdate(blockpos2, BaseFireBlock.getState(this.level, blockpos2));
            }
         }
      }

   }

   private static void addBlockDrops(ObjectArrayList<Pair<ItemStack, BlockPos>> p_46068_, ItemStack p_46069_, BlockPos p_46070_) {
      int i = p_46068_.size();

      for(int j = 0; j < i; ++j) {
         Pair<ItemStack, BlockPos> pair = p_46068_.get(j);
         ItemStack itemstack = pair.getFirst();
         if (ItemEntity.areMergable(itemstack, p_46069_)) {
            ItemStack itemstack1 = ItemEntity.merge(itemstack, p_46069_, 16);
            p_46068_.set(j, Pair.of(itemstack1, pair.getSecond()));
            if (p_46069_.isEmpty()) {
               return;
            }
         }
      }

      p_46068_.add(Pair.of(p_46069_, p_46070_));
   }

   public DamageSource getDamageSource() {
      return this.damageSource;
   }

   public Map<Player, Vec3> getHitPlayers() {
      return this.hitPlayers;
   }

   @Nullable
   public LivingEntity getSourceMob() {
      if (this.source == null) {
         return null;
      } else if (this.source instanceof PrimedTnt) {
         return ((PrimedTnt)this.source).getOwner();
      } else if (this.source instanceof LivingEntity) {
         return (LivingEntity)this.source;
      } else {
         if (this.source instanceof Projectile) {
            Entity entity = ((Projectile)this.source).getOwner();
            if (entity instanceof LivingEntity) {
               return (LivingEntity)entity;
            }
         }

         return null;
      }
   }

   public void clearToBlow() {
      this.toBlow.clear();
   }

   public List<BlockPos> getToBlow() {
      return this.toBlow;
   }

   public static enum BlockInteraction {
      NONE,
      BREAK,
      DESTROY;
   }
}