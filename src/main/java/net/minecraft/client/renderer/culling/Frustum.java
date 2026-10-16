package net.minecraft.client.renderer.culling;

import com.mojang.math.Matrix4f;
import com.mojang.math.Vector4f;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.CameraRelativePosition;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.SectorAABB;
import net.minecraft.world.phys.SectorVec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class Frustum {
    public static final int OFFSET_STEP = 4;
    private static volatile boolean enabled = true;
    private final Vector4f[] frustumData = new Vector4f[6];
    private Vector4f viewVector;
    private double camX;
    private double camY;
    private double camZ;
    private CameraRelativePosition cameraRelativePosition = CameraRelativePosition.of(SectorVec3.fromApproximate(0.0D, 0.0D, 0.0D));
    // Keep the integral camera block separate from the lossy Vec3 mirror.  X/Z
    // deltas must be formed as long arithmetic before they are narrowed to float.

    public Frustum(Matrix4f pProjection, Matrix4f pFrustum) {
        this.calculateFrustum(pProjection, pFrustum);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        Frustum.enabled = enabled;
    }

    public Frustum(Frustum pOther) {
        System.arraycopy(pOther.frustumData, 0, this.frustumData, 0, pOther.frustumData.length);
        this.camX = pOther.camX;
        this.camY = pOther.camY;
        this.camZ = pOther.camZ;
        this.cameraRelativePosition = pOther.cameraRelativePosition;
        this.viewVector = pOther.viewVector;
    }

    public Frustum offsetToFullyIncludeCameraCube(int p_194442_) {
        //      double d0 = Math.floor(this.camX / (double)p_194442_) * (double)p_194442_;
        //      double d1 = Math.floor(this.camY / (double)p_194442_) * (double)p_194442_;
        //      double d2 = Math.floor(this.camZ / (double)p_194442_) * (double)p_194442_;
        //      double d3 = Math.ceil (this.camX / (double)p_194442_) * (double)p_194442_;
        //      double d4 = Math.ceil (this.camY / (double)p_194442_) * (double)p_194442_;
        //      double d5 = Math.ceil (this.camZ / (double)p_194442_) * (double)p_194442_;
        //
        //      while(!this.cubeCompletelyInFrustum((float)(d0 - this.camX), (float)(d1 - this.camY), (float)(d2 - this.camZ), (float)(d3 - this.camX), (float)(d4 - this.camY), (float)(d5 - this.camZ))) {
        //         this.camX -= (double)(this.viewVector.x() * 4.0D);
        //         this.camY -= (double)(this.viewVector.y() * 4.0D);
        //         this.camZ -= (double)(this.viewVector.z() * 4.0D);
        //      }

        return this;
    }

    public void prepare(double pCamX, double pCamY, double pCamZ) {
        this.camX = pCamX;
        this.camY = pCamY;
        this.camZ = pCamZ;
        this.cameraRelativePosition = CameraRelativePosition.of(SectorVec3.fromApproximate(pCamX, pCamY, pCamZ));
    }

    /**
    * Prepares the frustum from the exact camera position.  The legacy double
    * overload remains for ordinary cameras, while sector cameras retain their
    * integral X/Z coordinates all the way through culling.
    */
    public void prepare(Camera camera) {
        if (camera == null)
            throw new NullPointerException("camera");
        if (camera.getExactPosition() != null) {
            this.cameraRelativePosition = CameraRelativePosition.of(camera.getExactPosition());
            this.camX = camera.getPosition().x;
            this.camY = camera.getPosition().y;
            this.camZ = camera.getPosition().z;
        } else {
            this.prepare(camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
        }
    }

    private void calculateFrustum(Matrix4f pProjection, Matrix4f pFrustumMatrix) {
        Matrix4f matrix4f = pFrustumMatrix.copy();
        matrix4f.multiply(pProjection);
        matrix4f.transpose();
        this.viewVector = new Vector4f(0.0F, 0.0F, 1.0F, 0.0F);
        this.viewVector.transform(matrix4f);
        this.getPlane(matrix4f, -1, 0, 0, 0);
        this.getPlane(matrix4f, 1, 0, 0, 1);
        this.getPlane(matrix4f, 0, -1, 0, 2);
        this.getPlane(matrix4f, 0, 1, 0, 3);
        this.getPlane(matrix4f, 0, 0, -1, 4);
        this.getPlane(matrix4f, 0, 0, 1, 5);
    }

    private void getPlane(Matrix4f pFrustrumMatrix, int pX, int pY, int pZ, int pId) {
        Vector4f vector4f = new Vector4f((float) pX, (float) pY, (float) pZ, 1.0F);
        vector4f.transform(pFrustrumMatrix);
        vector4f.normalize();
        this.frustumData[pId] = vector4f;
    }

    public boolean isVisible(AABB pAabb) {
        if (!enabled)
            return true;
        long minBlockX = Mth.lfloor(pAabb.minX);
        int minBlockY = Mth.floor(pAabb.minY);
        long minBlockZ = Mth.lfloor(pAabb.minZ);
        long maxBlockX = Mth.lfloor(pAabb.maxX);
        int maxBlockY = Mth.floor(pAabb.maxY);
        long maxBlockZ = Mth.lfloor(pAabb.maxZ);
        return this.cubeInFrustumLocal(
                this.cameraRelativePosition.relativeX(minBlockX) + pAabb.minX - (double) minBlockX,
                this.cameraRelativePosition.relativeY(minBlockY) + pAabb.minY - (double) minBlockY,
                this.cameraRelativePosition.relativeZ(minBlockZ) + pAabb.minZ - (double) minBlockZ,
                this.cameraRelativePosition.relativeX(maxBlockX) + pAabb.maxX - (double) maxBlockX,
                this.cameraRelativePosition.relativeY(maxBlockY) + pAabb.maxY - (double) maxBlockY,
                this.cameraRelativePosition.relativeZ(maxBlockZ) + pAabb.maxZ - (double) maxBlockZ);
    }

    /** Tests an exact X/Z box without reconstructing its endpoints as doubles. */
    public boolean isVisible(SectorAABB box) {
        if (!enabled)
            return true;
        if (box == null)
            throw new NullPointerException("box");
        return this.cubeInFrustumLocal(
                relativeX(box.minBlockX(), box.minSubX()), box.minY() - this.camY,
                relativeZ(box.minBlockZ(), box.minSubZ()), relativeX(box.maxBlockX(), box.maxSubX()),
                box.maxY() - this.camY, relativeZ(box.maxBlockZ(), box.maxSubZ()));
    }

    /** Tests a block-local shape at an exact block position. */
    public boolean isVisible(BlockPos blockPos, AABB localShape) {
        if (!enabled)
            return true;
        if (blockPos == null)
            throw new NullPointerException("blockPos");
        if (localShape == null)
            throw new NullPointerException("localShape");
        return this.cubeInFrustumLocal(
                relativeX(blockPos.getX(), localShape.minX), relativeY(blockPos.getY()) + localShape.minY,
                relativeZ(blockPos.getZ(), localShape.minZ), relativeX(blockPos.getX(), localShape.maxX),
                relativeY(blockPos.getY()) + localShape.maxY, relativeZ(blockPos.getZ(), localShape.maxZ));
    }

    public boolean isChunkVisible(long chunkX, int originBlockY, long chunkZ) {
        if (!enabled)
            return true;
        long blockMinX = net.minecraft.core.SectionPos.sectionToBlockCoord(chunkX);
        long blockMinZ = net.minecraft.core.SectionPos.sectionToBlockCoord(chunkZ);

        double minX = this.cameraRelativePosition.relativeX(blockMinX);
        double minY = this.cameraRelativePosition.relativeY(originBlockY);
        double minZ = this.cameraRelativePosition.relativeZ(blockMinZ);

        double maxX = minX + 16.0D;
        double maxY = minY + 16.0D;
        double maxZ = minZ + 16.0D;

        return this.cubeInFrustumLocal(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private double relativeX(long block, double fraction) {
        return this.cameraRelativePosition.relativeX(block) + fraction;
    }

    private double relativeZ(long block, double fraction) {
        return this.cameraRelativePosition.relativeZ(block) + fraction;
    }

    private double relativeY(int block) {
        return this.cameraRelativePosition.relativeY(block);
    }

    private boolean cubeInFrustum(double pMinX, double pMinY, double pMinZ, double pMaxX, double pMaxY, double pMaxZ) {
        return this.cubeInFrustumLocal(
                this.cameraRelativePosition.relativeX(Mth.lfloor(pMinX)) + pMinX - (double) Mth.lfloor(pMinX),
                this.cameraRelativePosition.relativeY(Mth.floor(pMinY)) + pMinY - (double) Mth.floor(pMinY),
                this.cameraRelativePosition.relativeZ(Mth.lfloor(pMinZ)) + pMinZ - (double) Mth.lfloor(pMinZ),
                this.cameraRelativePosition.relativeX(Mth.lfloor(pMaxX)) + pMaxX - (double) Mth.lfloor(pMaxX),
                this.cameraRelativePosition.relativeY(Mth.floor(pMaxY)) + pMaxY - (double) Mth.floor(pMaxY),
                this.cameraRelativePosition.relativeZ(Mth.lfloor(pMaxZ)) + pMaxZ - (double) Mth.lfloor(pMaxZ));
    }

    private boolean cubeInFrustumLocal(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return this.cubeInFrustum((float) minX, (float) minY, (float) minZ, (float) maxX, (float) maxY, (float) maxZ);
    }

    private boolean cubeInFrustum(float pMinX, float pMinY, float pMinZ, float pMaxX, float pMaxY, float pMaxZ) {
        for (int i = 0; i < 6; ++i) {
            Vector4f vector4f = this.frustumData[i];
            if (!(vector4f.dot(new Vector4f(pMinX, pMinY, pMinZ, 1.0F)) > 0.0F) && !(vector4f.dot(new Vector4f(pMaxX, pMinY, pMinZ, 1.0F)) > 0.0F) && !(vector4f.dot(new Vector4f(pMinX, pMaxY, pMinZ, 1.0F)) > 0.0F) && !(vector4f.dot(new Vector4f(pMaxX, pMaxY, pMinZ, 1.0F)) > 0.0F) && !(vector4f.dot(new Vector4f(pMinX, pMinY, pMaxZ, 1.0F)) > 0.0F) && !(vector4f.dot(new Vector4f(pMaxX, pMinY, pMaxZ, 1.0F)) > 0.0F) && !(vector4f.dot(new Vector4f(pMinX, pMaxY, pMaxZ, 1.0F)) > 0.0F) && !(vector4f.dot(new Vector4f(pMaxX, pMaxY, pMaxZ, 1.0F)) > 0.0F)) {
                return false;
            }
        }

        return true;
    }

    private boolean cubeCompletelyInFrustum(float p_194444_, float p_194445_, float p_194446_, float p_194447_, float p_194448_, float p_194449_) {
        for (int i = 0; i < 6; ++i) {
            Vector4f vector4f = this.frustumData[i];
            if (vector4f.dot(new Vector4f(p_194444_, p_194445_, p_194446_, 1.0F)) <= 0.0F) {
                return false;
            }

            if (vector4f.dot(new Vector4f(p_194447_, p_194445_, p_194446_, 1.0F)) <= 0.0F) {
                return false;
            }

            if (vector4f.dot(new Vector4f(p_194444_, p_194448_, p_194446_, 1.0F)) <= 0.0F) {
                return false;
            }

            if (vector4f.dot(new Vector4f(p_194447_, p_194448_, p_194446_, 1.0F)) <= 0.0F) {
                return false;
            }

            if (vector4f.dot(new Vector4f(p_194444_, p_194445_, p_194449_, 1.0F)) <= 0.0F) {
                return false;
            }

            if (vector4f.dot(new Vector4f(p_194447_, p_194445_, p_194449_, 1.0F)) <= 0.0F) {
                return false;
            }

            if (vector4f.dot(new Vector4f(p_194444_, p_194448_, p_194449_, 1.0F)) <= 0.0F) {
                return false;
            }

            if (vector4f.dot(new Vector4f(p_194447_, p_194448_, p_194449_, 1.0F)) <= 0.0F) {
                return false;
            }
        }

        return true;
    }
}
