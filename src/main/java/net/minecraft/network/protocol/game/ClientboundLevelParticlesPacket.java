package net.minecraft.network.protocol.game;

import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.phys.SectorVec3;

/**
 * Particle packet whose horizontal origin remains split-coordinate exact.
 *
 * <p>The spread and velocity are local deltas and therefore remain ordinary
 * floats.  The source position is never reconstructed as an absolute X/Z
 * double on either side of the connection.</p>
 */
public class ClientboundLevelParticlesPacket implements Packet<ClientGamePacketListener> {
   private final SectorVec3 position;
   private final float xDist;
   private final float yDist;
   private final float zDist;
   private final float maxSpeed;
   private final int count;
   private final boolean overrideLimiter;
   private final ParticleOptions particle;

   /** Compatibility constructor for legacy callers with already-lossy doubles. */
   public <T extends ParticleOptions> ClientboundLevelParticlesPacket(T particle, boolean overrideLimiter,
                                                                       double x, double y, double z,
                                                                       float xDist, float yDist, float zDist,
                                                                       float maxSpeed, int count) {
      this(particle, overrideLimiter, SectorVec3.fromApproximate(x, y, z), xDist, yDist, zDist, maxSpeed, count);
   }

   public <T extends ParticleOptions> ClientboundLevelParticlesPacket(T particle, boolean overrideLimiter,
                                                                       SectorVec3 position,
                                                                       float xDist, float yDist, float zDist,
                                                                       float maxSpeed, int count) {
      this.particle = particle;
      this.overrideLimiter = overrideLimiter;
      this.position = position;
      this.xDist = xDist;
      this.yDist = yDist;
      this.zDist = zDist;
      this.maxSpeed = maxSpeed;
      this.count = count;
   }

   public ClientboundLevelParticlesPacket(FriendlyByteBuf buf) {
      ParticleType<?> particleType = buf.readById(Registry.PARTICLE_TYPE);
      this.overrideLimiter = buf.readBoolean();
      this.position = SectorVec3.fromBlockAndFraction(buf.readLong(), buf.readDouble(), buf.readDouble(),
            buf.readLong(), buf.readDouble());
      this.xDist = buf.readFloat();
      this.yDist = buf.readFloat();
      this.zDist = buf.readFloat();
      this.maxSpeed = buf.readFloat();
      this.count = buf.readInt();
      this.particle = this.readParticle(buf, particleType);
   }

   private <T extends ParticleOptions> T readParticle(FriendlyByteBuf buf, ParticleType<T> particleType) {
      return particleType.getDeserializer().fromNetwork(particleType, buf);
   }

   public void write(FriendlyByteBuf buf) {
      buf.writeId(Registry.PARTICLE_TYPE, this.particle.getType());
      buf.writeBoolean(this.overrideLimiter);
      buf.writeLong(this.position.blockX());
      buf.writeDouble(this.position.subX());
      buf.writeDouble(this.position.y());
      buf.writeLong(this.position.blockZ());
      buf.writeDouble(this.position.subZ());
      buf.writeFloat(this.xDist);
      buf.writeFloat(this.yDist);
      buf.writeFloat(this.zDist);
      buf.writeFloat(this.maxSpeed);
      buf.writeInt(this.count);
      this.particle.writeToNetwork(buf);
   }

   public boolean isOverrideLimiter() {
      return this.overrideLimiter;
   }

   /** Exact X/Z world position of the particle source. */
   public SectorVec3 getExactPosition() {
      return this.position;
   }

   /** Legacy compatibility accessor; precision-sensitive callers must use getExactPosition. */
   public double getX() {
      return this.position.toApproximateVec3().x;
   }

   public double getY() {
      return this.position.y();
   }

   /** Legacy compatibility accessor; precision-sensitive callers must use getExactPosition. */
   public double getZ() {
      return this.position.toApproximateVec3().z;
   }

   public float getXDist() {
      return this.xDist;
   }

   public float getYDist() {
      return this.yDist;
   }

   public float getZDist() {
      return this.zDist;
   }

   public float getMaxSpeed() {
      return this.maxSpeed;
   }

   public int getCount() {
      return this.count;
   }

   public ParticleOptions getParticle() {
      return this.particle;
   }

   public void handle(ClientGamePacketListener listener) {
      listener.handleParticleEvent(this);
   }
}
