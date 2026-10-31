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
import net.minecraft.world.level.SectorClipper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.ExactVec3;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class Camera {
    private boolean initialized;
    private BlockGetter level;
    private Entity entity;
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
        SectorVec3 interpolated = pEntity.interpolatedExactPosition(pPartialTick);
        // Preserve the tick-smoothed pose transition without projecting exact X/Z to doubles.
        this.setPosition(interpolated.withY(interpolated.y()
                + (double) Mth.lerp(pPartialTick, this.eyeHeightOld, this.eyeHeight)));
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
            double distance = SectorClipper.clipCameraDistance(this.level, this.exactPosition,
                    new Vec3(f, f1, f2),
                    new Vec3(-this.forwards.x() * pStartingDistance + f + f2,
                            -this.forwards.y() * pStartingDistance + f1,
                            -this.forwards.z() * pStartingDistance + f2), this.entity);
            pStartingDistance = Math.min(pStartingDistance, distance);
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
            this.setPosition(this.playerThirdPerson
                    ? this.exactPosition.offsetCamera(d0, d1, d2)
                    : this.exactPosition.add(d0, d1, d2));
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
        this.setPosition(SectorVec3.fromApproximate(pX, pY, pZ));
    }

    protected void setPosition(Vec3 pPos) {
        this.setPosition(ExactVec3.position(pPos));
    }

    protected void setPosition(SectorVec3 pPos) {
        this.exactPosition = pPos;
        this.blockPosition.set(pPos.blockPosition());
    }

    /** Local X/Z offsets from the exact horizontal block base; Y remains vertical world height. */
    public Vec3 getPosition() {
        return this.exactPosition == null ? Vec3.ZERO
                : new Vec3(this.exactPosition.subX(), this.exactPosition.y(), this.exactPosition.subZ());
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
            BlockPos cameraBlock = this.blockPosition;
            boolean inside = this.exactPosition.isInsideWorld();
            FluidState fluidstate = inside ? this.level.getFluidState(cameraBlock) : net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
            if (fluidstate.is(FluidTags.WATER) && this.exactPosition.y() < (double) cameraBlock.getY() + fluidstate.getHeight(this.level, cameraBlock)) {
                return FogType.WATER;
            } else {
                Camera.NearPlane camera$nearplane = this.getNearPlane();

                for (Vec3 vec3 : Arrays.asList(camera$nearplane.forward, camera$nearplane.getTopLeft(), camera$nearplane.getTopRight(), camera$nearplane.getBottomLeft(), camera$nearplane.getBottomRight())) {
                    SectorVec3 exactSample = this.exactPosition.offsetCamera(vec3.x, vec3.y, vec3.z);
                    if (!exactSample.isInsideWorld()) {
                        continue;
                    }
                    BlockPos blockpos = exactSample.blockPosition();
                    FluidState fluidstate1 = this.level.getFluidState(blockpos);
                    if (fluidstate1.is(FluidTags.LAVA)) {
                        if (exactSample.y() <= (double) blockpos.getY() + fluidstate1.getHeight(this.level, blockpos)) {
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
