package net.minecraft.client;

import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.SectorClipper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class Camera {
    private boolean initialized;
    private BlockGetter level;
    private Entity entity;
    private Vec3 position = Vec3.ZERO;
    @Nullable
    private SectorVec3 exactPosition;
    private final BlockPos.MutableBlockPos blockPosition = new BlockPos.MutableBlockPos();
    private final Vector3f forwards = new Vector3f(0.0F, 0.0F, 1.0F);
    private final Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
    private final Vector3f left = new Vector3f(1.0F, 0.0F, 0.0F);
    private float xRot;
    private float yRot;
    private final Quaternion rotation = new Quaternion(0.0F, 0.0F, 0.0F, 1.0F);
    private boolean detached;
    private boolean playerThirdPerson;
    private float eyeHeight;
    private float eyeHeightOld;
    public static final float FOG_DISTANCE_SCALE = 0.083333336F;

    public void setup(BlockGetter pLevel, Entity pEntity, boolean pDetached, boolean pThirdPersonReverse, float pPartialTick) {
        this.initialized = true;
        this.level = pLevel;
        this.entity = pEntity;
        this.detached = pDetached;
        this.playerThirdPerson = pDetached && pEntity == Minecraft.getInstance().player;
        this.setRotation(pEntity.getViewYRot(pPartialTick), pEntity.getViewXRot(pPartialTick));
        this.exactPosition = pEntity.interpolatedExactPosition(pPartialTick);
        if (this.exactPosition != null) {
            // exactPosition must describe the actual camera, not the player's feet.
            // The legacy Vec3 path already added eyeHeight to the camera position;
            // keeping that addition only in the compatibility mirror left sector
            // rendering and third-person camera motion one eye-height below it.
            this.exactPosition = this.exactPosition.withY(this.exactPosition.y()
                    + (double) pEntity.getEyeHeight());
            this.setPosition(this.exactPosition.toApproximateVec3());
        } else {
            this.setPosition(Mth.lerp((double) pPartialTick, pEntity.xo, pEntity.getX()), Mth.lerp((double) pPartialTick, pEntity.yo, pEntity.getY()) + (double) Mth.lerp(pPartialTick, this.eyeHeightOld, this.eyeHeight), Mth.lerp((double) pPartialTick, pEntity.zo, pEntity.getZ()));
        }
        if (pDetached) {
            if (pThirdPersonReverse) {
                this.setRotation(this.yRot + 180.0F, -this.xRot);
            }

            this.move(-this.getMaxZoom(4.0D), 0.0D, 0.0D);
        } else if (pEntity instanceof LivingEntity && ((LivingEntity) pEntity).isSleeping()) {
            Direction direction = ((LivingEntity) pEntity).getBedOrientation();
            this.setRotation(direction != null ? direction.toYRot() - 180.0F : 0.0F, 0.0F);
            this.move(0.0D, 0.3D, 0.0D);
        }
    }

    public void tick() {
        if (this.entity != null) {
            this.eyeHeightOld = this.eyeHeight;
            this.eyeHeight += (this.entity.getEyeHeight() - this.eyeHeight) * 0.5F;
        }
    }

    private double getMaxZoom(double pStartingDistance) {
        for (int i = 0; i < 8; ++i) {
            float f = (float) ((i & 1) * 2 - 1);
            float f1 = (float) ((i >> 1 & 1) * 2 - 1);
            float f2 = (float) ((i >> 2 & 1) * 2 - 1);
            f *= 0.1F;
            f1 *= 0.1F;
            f2 *= 0.1F;
            HitResult hitresult;
            if (this.exactPosition != null) {
                if (this.playerThirdPerson) {
                    double distance = SectorClipper.clipCameraDistance(this.level, this.exactPosition,
                            new Vec3(f, f1, f2),
                            new Vec3(-this.forwards.x() * pStartingDistance + f + f2,
                                    -this.forwards.y() * pStartingDistance + f1,
                                    -this.forwards.z() * pStartingDistance + f2), this.entity);
                    pStartingDistance = Math.min(pStartingDistance, distance);
                    continue;
                }
                SectorVec3 from = this.exactPosition.add(f, f1, f2);
                // Both endpoints are relative to the camera. Do not add the
                // corner offset twice by deriving the far endpoint from `from`.
                SectorVec3 to = this.exactPosition.add(-this.forwards.x() * pStartingDistance + f + f2,
                        -this.forwards.y() * pStartingDistance + f1,
                        -this.forwards.z() * pStartingDistance + f2);
                hitresult = SectorClipper.clip(this.level, from, to, this.entity,
                        ClipContext.Block.VISUAL, ClipContext.Fluid.NONE);
            } else {
                Vec3 vec3 = this.position.add((double) f, (double) f1, (double) f2);
                Vec3 vec31 = new Vec3(this.position.x - (double) this.forwards.x() * pStartingDistance + (double) f + (double) f2, this.position.y - (double) this.forwards.y() * pStartingDistance + (double) f1, this.position.z - (double) this.forwards.z() * pStartingDistance + (double) f2);
                hitresult = this.level.clip(new ClipContext(vec3, vec31, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, this.entity));
            }
            if (hitresult.getType() != HitResult.Type.MISS) {
                double d0;
                if (this.exactPosition != null && hitresult instanceof BlockHitResult blockHit
                        && blockHit.getExactLocation() != null) {
                    // The hit point is also split-coordinate data. Comparing its
                    // lossy Vec3 mirror with the camera position can turn a wall
                    // hit into a zero-distance hit at large coordinates.
                    d0 = blockHit.getExactLocation().relativeTo(this.exactPosition).length();
                } else {
                    d0 = hitresult.getLocation().distanceTo(this.position);
                }
                if (d0 < pStartingDistance) {
                    pStartingDistance = d0;
                }
            }
        }

        return pStartingDistance;
    }

    protected void move(double pDistanceOffset, double pVerticalOffset, double pHorizontalOffset) {
        double d0 = (double) this.forwards.x() * pDistanceOffset + (double) this.up.x() * pVerticalOffset + (double) this.left.x() * pHorizontalOffset;
        double d1 = (double) this.forwards.y() * pDistanceOffset + (double) this.up.y() * pVerticalOffset + (double) this.left.y() * pHorizontalOffset;
        double d2 = (double) this.forwards.z() * pDistanceOffset + (double) this.up.z() * pVerticalOffset + (double) this.left.z() * pHorizontalOffset;
        if (this.exactPosition != null) {
            // Third-person camera motion is not entity motion. Keep the camera's
            // split-coordinate position in sync, otherwise renderers still use the
            // first-person eye position while the actual camera has moved behind
            // the player (making the player appear enormous/in the camera).
            this.exactPosition = this.playerThirdPerson
                    ? this.exactPosition.offsetCamera(d0, d1, d2)
                    : this.exactPosition.add(d0, d1, d2);
            this.setPosition(this.exactPosition.toApproximateVec3());
        } else {
            this.setPosition(new Vec3(this.position.x + d0, this.position.y + d1, this.position.z + d2));
        }
    }

    protected void setRotation(float pYRot, float pXRot) {
        this.xRot = pXRot;
        this.yRot = pYRot;
        this.rotation.set(0.0F, 0.0F, 0.0F, 1.0F);
        this.rotation.mul(Vector3f.YP.rotationDegrees(-pYRot));
        this.rotation.mul(Vector3f.XP.rotationDegrees(pXRot));
        this.forwards.set(0.0F, 0.0F, 1.0F);
        this.forwards.transform(this.rotation);
        this.up.set(0.0F, 1.0F, 0.0F);
        this.up.transform(this.rotation);
        this.left.set(1.0F, 0.0F, 0.0F);
        this.left.transform(this.rotation);
    }

    protected void setPosition(double pX, double pY, double pZ) {
        this.setPosition(new Vec3(pX, pY, pZ));
    }

    protected void setPosition(Vec3 pPos) {
        this.position = pPos;
        this.blockPosition.set(pPos.x, pPos.y, pPos.z);
    }

    public Vec3 getPosition() {
        return this.position;
    }

    public BlockPos getBlockPosition() {
        return this.blockPosition;
    }

    @Nullable
    public SectorVec3 getExactPosition() {
        return this.exactPosition;
    }

    /** Returns the split-coordinate camera position used by precision-sensitive render paths. */
    @Nullable
    public SectorVec3 exactPosition() {
        return this.exactPosition;
    }

    public float getXRot() {
        return this.xRot;
    }

    public float getYRot() {
        return this.yRot;
    }

    public Quaternion rotation() {
        return this.rotation;
    }

    public Entity getEntity() {
        return this.entity;
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    public boolean isDetached() {
        return this.detached;
    }

    public Camera.NearPlane getNearPlane() {
        Minecraft minecraft = Minecraft.getInstance();
        double d0 = (double) minecraft.getWindow().getWidth() / (double) minecraft.getWindow().getHeight();
        double d1 = Math.tan((double) ((float) minecraft.options.fov().get().intValue() * ((float) Math.PI / 180F)) / 2.0D) * (double) 0.05F;
        double d2 = d1 * d0;
        Vec3 vec3 = (new Vec3(this.forwards)).scale((double) 0.05F);
        Vec3 vec31 = (new Vec3(this.left)).scale(d2);
        Vec3 vec32 = (new Vec3(this.up)).scale(d1);
        return new Camera.NearPlane(vec3, vec31, vec32);
    }

    public FogType getFluidInCamera() {
        if (!this.initialized) {
            return FogType.NONE;
        } else {
            BlockPos cameraBlock = this.exactPosition != null ? this.exactPosition.blockPosition() : this.blockPosition;
            boolean inside = this.exactPosition == null || this.exactPosition.isInsideWorld();
            FluidState fluidstate = inside ? this.level.getFluidState(cameraBlock) : net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
            if (fluidstate.is(FluidTags.WATER) && this.position.y < (double) ((float) cameraBlock.getY() + fluidstate.getHeight(this.level, cameraBlock))) {
                return FogType.WATER;
            } else {
                Camera.NearPlane camera$nearplane = this.getNearPlane();

                for (Vec3 vec3 : Arrays.asList(camera$nearplane.forward, camera$nearplane.getTopLeft(), camera$nearplane.getTopRight(), camera$nearplane.getBottomLeft(), camera$nearplane.getBottomRight())) {
                    Vec3 vec31 = this.position.add(vec3);
                    SectorVec3 exactSample = this.exactPosition != null
                            ? this.exactPosition.offsetCamera(vec3.x, vec3.y, vec3.z) : null;
                    if (exactSample != null && !exactSample.isInsideWorld()) {
                        continue;
                    }
                    BlockPos blockpos = exactSample != null ? exactSample.blockPosition() : new BlockPos(vec31);
                    FluidState fluidstate1 = this.level.getFluidState(blockpos);
                    if (fluidstate1.is(FluidTags.LAVA)) {
                        if (vec31.y <= (double) (fluidstate1.getHeight(this.level, blockpos) + (float) blockpos.getY())) {
                            return FogType.LAVA;
                        }
                    } else {
                        BlockState blockstate = this.level.getBlockState(blockpos);
                        if (blockstate.is(Blocks.POWDER_SNOW)) {
                            return FogType.POWDER_SNOW;
                        }
                    }
                }

                return FogType.NONE;
            }
        }
    }

    public final Vector3f getLookVector() {
        return this.forwards;
    }

    public final Vector3f getUpVector() {
        return this.up;
    }

    public final Vector3f getLeftVector() {
        return this.left;
    }

    public void reset() {
        this.level = null;
        this.entity = null;
        this.exactPosition = null;
        this.initialized = false;
    }

    @OnlyIn(Dist.CLIENT)
    public static class NearPlane {
        final Vec3 forward;
        private final Vec3 left;
        private final Vec3 up;

        NearPlane(Vec3 pForward, Vec3 pLeft, Vec3 pUp) {
            this.forward = pForward;
            this.left = pLeft;
            this.up = pUp;
        }

        public Vec3 getTopLeft() {
            return this.forward.add(this.up).add(this.left);
        }

        public Vec3 getTopRight() {
            return this.forward.add(this.up).subtract(this.left);
        }

        public Vec3 getBottomLeft() {
            return this.forward.subtract(this.up).add(this.left);
        }

        public Vec3 getBottomRight() {
            return this.forward.subtract(this.up).subtract(this.left);
        }

        public Vec3 getPointOnPlane(float pLeftScale, float pUpScale) {
            return this.forward.add(this.up.scale((double) pUpScale)).subtract(this.left.scale((double) pLeftScale));
        }
    }
}
