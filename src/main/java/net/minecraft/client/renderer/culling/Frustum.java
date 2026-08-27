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
   private final Vector4f[] frustumData = new Vector4f[6];
   private Vector4f viewVector;
   private double camX;
   private double camY;
   private double camZ;
   private CameraRelativePosition cameraRelativePosition = CameraRelativePosition.of(SectorVec3.fromApproximate(0.0D, 0.0D, 0.0D));
   // Keep the integral camera block separate from the lossy Vec3 mirror.  X/Z
   // deltas must be formed as long arithmetic before they are narrowed to float.

   public Frustum(Matrix4f p_113000_, Matrix4f p_113001_) {
      this.calculateFrustum(p_113000_, p_113001_);
   }

   public Frustum(Frustum p_194440_) {
      System.arraycopy(p_194440_.frustumData, 0, this.frustumData, 0, p_194440_.frustumData.length);
      this.camX = p_194440_.camX;
      this.camY = p_194440_.camY;
      this.camZ = p_194440_.camZ;
      this.cameraRelativePosition = p_194440_.cameraRelativePosition;
      this.viewVector = p_194440_.viewVector;
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

   public void prepare(double p_113003_, double p_113004_, double p_113005_) {
      this.camX = p_113003_;
      this.camY = p_113004_;
      this.camZ = p_113005_;
      this.cameraRelativePosition = CameraRelativePosition.of(SectorVec3.fromApproximate(p_113003_, p_113004_, p_113005_));
   }

   /**
    * Prepares the frustum from the exact camera position.  The legacy double
    * overload remains for ordinary cameras, while sector cameras retain their
    * integral X/Z coordinates all the way through culling.
    */
   public void prepare(Camera camera) {
      if (camera == null) throw new NullPointerException("camera");
      if (camera.getExactPosition() != null) {
         this.cameraRelativePosition = CameraRelativePosition.of(camera.getExactPosition());
         this.camX = camera.getPosition().x;
         this.camY = camera.getPosition().y;
         this.camZ = camera.getPosition().z;
      } else {
         this.prepare(camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
      }
   }

   private void calculateFrustum(Matrix4f p_113027_, Matrix4f p_113028_) {
      Matrix4f matrix4f = p_113028_.copy();
      matrix4f.multiply(p_113027_);
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

   private void getPlane(Matrix4f p_113021_, int p_113022_, int p_113023_, int p_113024_, int p_113025_) {
      Vector4f vector4f = new Vector4f((float)p_113022_, (float)p_113023_, (float)p_113024_, 1.0F);
      vector4f.transform(p_113021_);
      vector4f.normalize();
      this.frustumData[p_113025_] = vector4f;
   }

   public boolean isVisible(AABB p_113030_) {
      long minBlockX = Mth.lfloor(p_113030_.minX);
      int minBlockY = Mth.floor(p_113030_.minY);
      long minBlockZ = Mth.lfloor(p_113030_.minZ);
      long maxBlockX = Mth.lfloor(p_113030_.maxX);
      int maxBlockY = Mth.floor(p_113030_.maxY);
      long maxBlockZ = Mth.lfloor(p_113030_.maxZ);
      return this.cubeInFrustumLocal(
            this.cameraRelativePosition.relativeX(minBlockX) + p_113030_.minX - (double)minBlockX,
            this.cameraRelativePosition.relativeY(minBlockY) + p_113030_.minY - (double)minBlockY,
            this.cameraRelativePosition.relativeZ(minBlockZ) + p_113030_.minZ - (double)minBlockZ,
            this.cameraRelativePosition.relativeX(maxBlockX) + p_113030_.maxX - (double)maxBlockX,
            this.cameraRelativePosition.relativeY(maxBlockY) + p_113030_.maxY - (double)maxBlockY,
            this.cameraRelativePosition.relativeZ(maxBlockZ) + p_113030_.maxZ - (double)maxBlockZ);
   }

   /** Tests an exact X/Z box without reconstructing its endpoints as doubles. */
   public boolean isVisible(SectorAABB box) {
      if (box == null) throw new NullPointerException("box");
      return this.cubeInFrustumLocal(
            relativeX(box.minBlockX(), box.minSubX()), box.minY() - this.camY,
            relativeZ(box.minBlockZ(), box.minSubZ()), relativeX(box.maxBlockX(), box.maxSubX()),
            box.maxY() - this.camY, relativeZ(box.maxBlockZ(), box.maxSubZ()));
   }

   /** Tests a block-local shape at an exact block position. */
   public boolean isVisible(BlockPos blockPos, AABB localShape) {
      if (blockPos == null) throw new NullPointerException("blockPos");
      if (localShape == null) throw new NullPointerException("localShape");
      return this.cubeInFrustumLocal(
            relativeX(blockPos.getX(), localShape.minX), relativeY(blockPos.getY()) + localShape.minY,
            relativeZ(blockPos.getZ(), localShape.minZ), relativeX(blockPos.getX(), localShape.maxX),
            relativeY(blockPos.getY()) + localShape.maxY, relativeZ(blockPos.getZ(), localShape.maxZ));
   }

   public boolean isChunkVisible(long chunkX, int originBlockY, long chunkZ) {
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

   private boolean cubeInFrustum(double p_113007_, double p_113008_, double p_113009_, double p_113010_, double p_113011_, double p_113012_) {
      return this.cubeInFrustumLocal(
            this.cameraRelativePosition.relativeX(Mth.lfloor(p_113007_)) + p_113007_ - (double)Mth.lfloor(p_113007_),
            this.cameraRelativePosition.relativeY(Mth.floor(p_113008_)) + p_113008_ - (double)Mth.floor(p_113008_),
            this.cameraRelativePosition.relativeZ(Mth.lfloor(p_113009_)) + p_113009_ - (double)Mth.lfloor(p_113009_),
            this.cameraRelativePosition.relativeX(Mth.lfloor(p_113010_)) + p_113010_ - (double)Mth.lfloor(p_113010_),
            this.cameraRelativePosition.relativeY(Mth.floor(p_113011_)) + p_113011_ - (double)Mth.floor(p_113011_),
            this.cameraRelativePosition.relativeZ(Mth.lfloor(p_113012_)) + p_113012_ - (double)Mth.lfloor(p_113012_));
   }

   private boolean cubeInFrustumLocal(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      return this.cubeInFrustum((float)minX, (float)minY, (float)minZ, (float)maxX, (float)maxY, (float)maxZ);
   }

   private boolean cubeInFrustum(float p_113014_, float p_113015_, float p_113016_, float p_113017_, float p_113018_, float p_113019_) {
      for(int i = 0; i < 6; ++i) {
         Vector4f vector4f = this.frustumData[i];
         if (!(vector4f.dot(new Vector4f(p_113014_, p_113015_, p_113016_, 1.0F)) > 0.0F) && !(vector4f.dot(new Vector4f(p_113017_, p_113015_, p_113016_, 1.0F)) > 0.0F) && !(vector4f.dot(new Vector4f(p_113014_, p_113018_, p_113016_, 1.0F)) > 0.0F) && !(vector4f.dot(new Vector4f(p_113017_, p_113018_, p_113016_, 1.0F)) > 0.0F) && !(vector4f.dot(new Vector4f(p_113014_, p_113015_, p_113019_, 1.0F)) > 0.0F) && !(vector4f.dot(new Vector4f(p_113017_, p_113015_, p_113019_, 1.0F)) > 0.0F) && !(vector4f.dot(new Vector4f(p_113014_, p_113018_, p_113019_, 1.0F)) > 0.0F) && !(vector4f.dot(new Vector4f(p_113017_, p_113018_, p_113019_, 1.0F)) > 0.0F)) {
            return false;
         }
      }

      return true;
   }

   private boolean cubeCompletelyInFrustum(float p_194444_, float p_194445_, float p_194446_, float p_194447_, float p_194448_, float p_194449_) {
      for(int i = 0; i < 6; ++i) {
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
