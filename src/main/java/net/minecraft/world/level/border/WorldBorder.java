package net.minecraft.world.level.border;

import com.google.common.collect.Lists;
import com.mojang.serialization.DynamicLike;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WorldBorder {
    public static final double MAX_SIZE = 5.9999968E7D;
    public static final double MAX_CENTER_COORDINATE = 2.9999984E7D;
    private final List<BorderChangeListener> listeners = Lists.newArrayList();
    private double damagePerBlock = 0.2D;
    private double damageSafeZone = 5.0D;
    private int warningTime = 15;
    private int warningBlocks = 5;
    private SectorVec3 exactCenter = SectorVec3.fromApproximate(0.0D, 0.0D, 0.0D);
    int absoluteMaxSize = 29999984;
    private WorldBorder.BorderExtent extent = new WorldBorder.StaticBorderExtent(5.9999968E7D);
    public static final WorldBorder.Settings DEFAULT_SETTINGS = new WorldBorder.Settings(0.0D, 0.0D, 0.2D, 5.0D, 5, 15, 5.9999968E7D, 0L, 0.0D);

    public boolean isWithinBounds(BlockPos pPos) {
        return true;
        //      return (double)(p_61938_.getX() + 1) > this.getMinX() && (double)p_61938_.getX() < this.getMaxX() && (double)(p_61938_.getZ() + 1) > this.getMinZ() && (double)p_61938_.getZ() < this.getMaxZ();
    }

    public boolean isWithinBounds(ChunkPos pChunkPos) {
        return true;
        //      return (double)p_61928_.getMaxBlockX() > this.getMinX() && (double)p_61928_.getMinBlockX() < this.getMaxX() && (double)p_61928_.getMaxBlockZ() > this.getMinZ() && (double)p_61928_.getMinBlockZ() < this.getMaxZ();
    }

    public boolean isWithinBounds(double pX, double pZ) {
        return true;
        //      return p_156094_ > this.getMinX() && p_156094_ < this.getMaxX() && p_156095_ > this.getMinZ() && p_156095_ < this.getMaxZ();
    }

    public boolean isWithinBounds(double pX, double pZ, double pOffset) {
        return true;
        //      return p_187563_ > this.getMinX() - p_187565_ && p_187563_ < this.getMaxX() + p_187565_ && p_187564_ > this.getMinZ() - p_187565_ && p_187564_ < this.getMaxZ() + p_187565_;
    }

    public boolean isWithinBounds(AABB pBox) {
        return true;
        //      return p_61936_.maxX > this.getMinX() && p_61936_.minX < this.getMaxX() && p_61936_.maxZ > this.getMinZ() && p_61936_.minZ < this.getMaxZ();
    }

    public BlockPos clampToBounds(double pX, double pY, double pZ) {
        return new BlockPos(pX, pY, pZ);
        //      return new BlockPos(Mth.clamp(p_187570_, this.getMinX(), this.getMaxX()), p_187571_, Mth.clamp(p_187572_, this.getMinZ(), this.getMaxZ()));
    }

    public double getDistanceToBorder(Entity pEntity) {
        return this.getDistanceToBorder(pEntity.sectorPosition());
    }

    public VoxelShape getCollisionShape() {
        // The border is disabled, including collision and damage at arbitrary coordinates.
        return Shapes.empty();
    }

    public double getDistanceToBorder(double pX, double pZ) {
        return Double.POSITIVE_INFINITY;
    }

    public double getDistanceToBorder(net.minecraft.world.phys.SectorVec3 position) {
        return Double.POSITIVE_INFINITY;
    }

    public boolean isInsideCloseToBorder(Entity pEntity, AABB pBounds) {
        return false;
    }

    public BorderStatus getStatus() {
        return this.extent.getStatus();
    }

    public SectorVec3 getExactCenter() {
        return this.exactCenter;
    }

    public void setCenter(SectorVec3 center) {
        this.exactCenter = SectorVec3.fromBlockAndFraction(center.blockX(), center.subX(), 0.0D,
                center.blockZ(), center.subZ());
        this.extent.onCenterChange();

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onBorderCenterSet(this, this.getExactCenter());
        }
    }

    public double getSize() {
        return this.extent.getSize();
    }

    public long getLerpRemainingTime() {
        return this.extent.getLerpRemainingTime();
    }

    public double getLerpTarget() {
        return this.extent.getLerpTarget();
    }

    public void setSize(double pSize) {
        this.extent = new WorldBorder.StaticBorderExtent(pSize);

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onBorderSizeSet(this, pSize);
        }
    }

    public void lerpSizeBetween(double pOldSize, double pNewSize, long pTime) {
        this.extent = (WorldBorder.BorderExtent) (pOldSize == pNewSize ? new WorldBorder.StaticBorderExtent(pNewSize) : new WorldBorder.MovingBorderExtent(pOldSize, pNewSize, pTime));

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onBorderSizeLerping(this, pOldSize, pNewSize, pTime);
        }
    }

    protected List<BorderChangeListener> getListeners() {
        return Lists.newArrayList(this.listeners);
    }

    public void addListener(BorderChangeListener pListener) {
        this.listeners.add(pListener);
    }

    public void removeListener(BorderChangeListener pListener) {
        this.listeners.remove(pListener);
    }

    public void setAbsoluteMaxSize(int pSize) {
        this.absoluteMaxSize = pSize;
        this.extent.onAbsoluteMaxSizeChange();
    }

    public int getAbsoluteMaxSize() {
        return this.absoluteMaxSize;
    }

    public double getDamageSafeZone() {
        return this.damageSafeZone;
    }

    public void setDamageSafeZone(double pDamageSafeZone) {
        this.damageSafeZone = pDamageSafeZone;

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onBorderSetDamageSafeZOne(this, pDamageSafeZone);
        }
    }

    public double getDamagePerBlock() {
        return this.damagePerBlock;
    }

    public void setDamagePerBlock(double pDamagePerBlock) {
        this.damagePerBlock = pDamagePerBlock;

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onBorderSetDamagePerBlock(this, pDamagePerBlock);
        }
    }

    public double getLerpSpeed() {
        return this.extent.getLerpSpeed();
    }

    public int getWarningTime() {
        return this.warningTime;
    }

    public void setWarningTime(int pWarningTime) {
        this.warningTime = pWarningTime;

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onBorderSetWarningTime(this, pWarningTime);
        }
    }

    public int getWarningBlocks() {
        return this.warningBlocks;
    }

    public void setWarningBlocks(int pWarningDistance) {
        this.warningBlocks = pWarningDistance;

        for (BorderChangeListener borderchangelistener : this.getListeners()) {
            borderchangelistener.onBorderSetWarningBlocks(this, pWarningDistance);
        }
    }

    public void tick() {
        this.extent = this.extent.update();
    }

    public WorldBorder.Settings createSettings() {
        return new WorldBorder.Settings(this);
    }

    public void applySettings(WorldBorder.Settings pSerializer) {
        this.setCenter(pSerializer.getExactCenter());
        this.setDamagePerBlock(pSerializer.getDamagePerBlock());
        this.setDamageSafeZone(pSerializer.getSafeZone());
        this.setWarningBlocks(pSerializer.getWarningBlocks());
        this.setWarningTime(pSerializer.getWarningTime());
        if (pSerializer.getSizeLerpTime() > 0L) {
            this.lerpSizeBetween(pSerializer.getSize(), pSerializer.getSizeLerpTarget(), pSerializer.getSizeLerpTime());
        } else {
            this.setSize(pSerializer.getSize());
        }
    }

    interface BorderExtent {
        double getSize();

        double getLerpSpeed();

        long getLerpRemainingTime();

        double getLerpTarget();

        BorderStatus getStatus();

        void onAbsoluteMaxSizeChange();

        void onCenterChange();

        WorldBorder.BorderExtent update();

        VoxelShape getCollisionShape();
    }

    class MovingBorderExtent implements WorldBorder.BorderExtent {
        private final double from;
        private final double to;
        private final long lerpEnd;
        private final long lerpBegin;
        private final double lerpDuration;

        MovingBorderExtent(double p_61979_, double p_61980_, long p_61981_) {
            this.from = p_61979_;
            this.to = p_61980_;
            this.lerpDuration = (double) p_61981_;
            this.lerpBegin = Util.getMillis();
            this.lerpEnd = this.lerpBegin + p_61981_;
        }

        public double getSize() {
            double d0 = (double) (Util.getMillis() - this.lerpBegin) / this.lerpDuration;
            return d0 < 1.0D ? Mth.lerp(d0, this.from, this.to) : this.to;
        }

        public double getLerpSpeed() {
            return Math.abs(this.from - this.to) / (double) (this.lerpEnd - this.lerpBegin);
        }

        public long getLerpRemainingTime() {
            return this.lerpEnd - Util.getMillis();
        }

        public double getLerpTarget() {
            return this.to;
        }

        public BorderStatus getStatus() {
            return this.to < this.from ? BorderStatus.SHRINKING : BorderStatus.GROWING;
        }

        public void onCenterChange() {
        }

        public void onAbsoluteMaxSizeChange() {
        }

        public WorldBorder.BorderExtent update() {
            return (WorldBorder.BorderExtent) (this.getLerpRemainingTime() <= 0L ? WorldBorder.this.new StaticBorderExtent(this.to) : this);
        }

        public VoxelShape getCollisionShape() {
            return Shapes.empty();
        }
    }

    public static class Settings {
        private final SectorVec3 exactCenter;
        private final double damagePerBlock;
        private final double safeZone;
        private final int warningBlocks;
        private final int warningTime;
        private final double size;
        private final long sizeLerpTime;
        private final double sizeLerpTarget;

        Settings(double pCenterX, double pCenterZ, double pDamagePerBlock, double pSafeZone, int pWarningBlocks, int pWarningTime, double pSize, long pSizeLerpTime, double pSizeLerpTarget) {
            this(SectorVec3.fromApproximate(pCenterX, 0.0D, pCenterZ), pDamagePerBlock, pSafeZone, pWarningBlocks, pWarningTime, pSize, pSizeLerpTime, pSizeLerpTarget);
        }

        Settings(SectorVec3 center, double pDamagePerBlock, double pSafeZone, int pWarningBlocks, int pWarningTime, double pSize, long pSizeLerpTime, double pSizeLerpTarget) {
            this.exactCenter = SectorVec3.fromBlockAndFraction(center.blockX(), center.subX(), 0.0D, center.blockZ(), center.subZ());
            this.damagePerBlock = pDamagePerBlock;
            this.safeZone = pSafeZone;
            this.warningBlocks = pWarningBlocks;
            this.warningTime = pWarningTime;
            this.size = pSize;
            this.sizeLerpTime = pSizeLerpTime;
            this.sizeLerpTarget = pSizeLerpTarget;
        }

        Settings(WorldBorder pBorder) {
            this.exactCenter = pBorder.getExactCenter();
            this.damagePerBlock = pBorder.getDamagePerBlock();
            this.safeZone = pBorder.getDamageSafeZone();
            this.warningBlocks = pBorder.getWarningBlocks();
            this.warningTime = pBorder.getWarningTime();
            this.size = pBorder.getSize();
            this.sizeLerpTime = pBorder.getLerpRemainingTime();
            this.sizeLerpTarget = pBorder.getLerpTarget();
        }

        public SectorVec3 getExactCenter() {
            return this.exactCenter;
        }

        public double getDamagePerBlock() {
            return this.damagePerBlock;
        }

        public double getSafeZone() {
            return this.safeZone;
        }

        public int getWarningBlocks() {
            return this.warningBlocks;
        }

        public int getWarningTime() {
            return this.warningTime;
        }

        public double getSize() {
            return this.size;
        }

        public long getSizeLerpTime() {
            return this.sizeLerpTime;
        }

        public double getSizeLerpTarget() {
            return this.sizeLerpTarget;
        }

        public static WorldBorder.Settings read(DynamicLike<?> pDynamic, WorldBorder.Settings pDefaultValue) {
            CompoundTag centerTag = new CompoundTag();
            for (String key : new String[]{"BorderCenterX", "BorderCenterZ", "BorderCenterXFraction", "BorderCenterZFraction"}) {
                centerTag.put(key, pDynamic.get(key).get().result().orElseThrow(() -> new IllegalArgumentException("Missing required border tag: " + key)).convert(NbtOps.INSTANCE).getValue());
            }
            double fractionX = centerTag.getDouble("BorderCenterXFraction");
            double fractionZ = centerTag.getDouble("BorderCenterZFraction");
            if (!centerTag.contains("BorderCenterXFraction", 6) || !centerTag.contains("BorderCenterZFraction", 6)
                    || !Double.isFinite(fractionX) || !Double.isFinite(fractionZ)
                    || fractionX < 0.0D || fractionX >= 1.0D || fractionZ < 0.0D || fractionZ >= 1.0D) {
                throw new IllegalArgumentException("Invalid border center fractions");
            }
            SectorVec3 center = SectorVec3.fromBlockAndFraction(NbtUtils.readInt1088(centerTag, "BorderCenterX"), fractionX,
                    0.0D, NbtUtils.readInt1088(centerTag, "BorderCenterZ"), fractionZ);
            double d2 = pDynamic.get("BorderSize").asDouble(pDefaultValue.size);
            long i = pDynamic.get("BorderSizeLerpTime").asLong(pDefaultValue.sizeLerpTime);
            double d3 = pDynamic.get("BorderSizeLerpTarget").asDouble(pDefaultValue.sizeLerpTarget);
            double d4 = pDynamic.get("BorderSafeZone").asDouble(pDefaultValue.safeZone);
            double d5 = pDynamic.get("BorderDamagePerBlock").asDouble(pDefaultValue.damagePerBlock);
            int j = pDynamic.get("BorderWarningBlocks").asInt(pDefaultValue.warningBlocks);
            int k = pDynamic.get("BorderWarningTime").asInt(pDefaultValue.warningTime);
            return new WorldBorder.Settings(center, d5, d4, j, k, d2, i, d3);
        }

        public void write(CompoundTag pNbt) {
            NbtUtils.writeInt1088(pNbt, "BorderCenterX", this.exactCenter.blockX());
            NbtUtils.writeInt1088(pNbt, "BorderCenterZ", this.exactCenter.blockZ());
            pNbt.putDouble("BorderCenterXFraction", this.exactCenter.subX());
            pNbt.putDouble("BorderCenterZFraction", this.exactCenter.subZ());
            pNbt.putDouble("BorderSize", this.size);
            pNbt.putLong("BorderSizeLerpTime", this.sizeLerpTime);
            pNbt.putDouble("BorderSafeZone", this.safeZone);
            pNbt.putDouble("BorderDamagePerBlock", this.damagePerBlock);
            pNbt.putDouble("BorderSizeLerpTarget", this.sizeLerpTarget);
            pNbt.putDouble("BorderWarningBlocks", (double) this.warningBlocks);
            pNbt.putDouble("BorderWarningTime", (double) this.warningTime);
        }
    }

    class StaticBorderExtent implements WorldBorder.BorderExtent {
        private final double size;

        public StaticBorderExtent(double p_62059_) {
            this.size = p_62059_;
        }

        public double getSize() {
            return this.size;
        }

        public BorderStatus getStatus() {
            return BorderStatus.STATIONARY;
        }

        public double getLerpSpeed() {
            return 0.0D;
        }

        public long getLerpRemainingTime() {
            return 0L;
        }

        public double getLerpTarget() {
            return this.size;
        }

        public void onAbsoluteMaxSizeChange() {
        }

        public void onCenterChange() {
        }

        public WorldBorder.BorderExtent update() {
            return this;
        }

        public VoxelShape getCollisionShape() {
            return Shapes.empty();
        }
    }
}
