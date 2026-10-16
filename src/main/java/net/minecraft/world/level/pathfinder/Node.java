package net.minecraft.world.level.pathfinder;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class Node {
    public final long x;
    public final int y;
    public final long z;
    private final int hash;
    public int heapIdx = -1;
    public float g;
    public float h;
    public float f;
    @Nullable
    public Node cameFrom;
    public boolean closed;
    public float walkedDistance;
    public float costMalus;
    public BlockPathTypes type = BlockPathTypes.BLOCKED;

    public Node(long pX, int pY, long pZ) {
        this.x = pX;
        this.y = pY;
        this.z = pZ;
        this.hash = createHash(pX, pY, pZ);
    }

    public Node cloneAndMove(long pX, int pY, long pZ) {
        Node node = new Node(pX, pY, pZ);
        node.heapIdx = this.heapIdx;
        node.g = this.g;
        node.h = this.h;
        node.f = this.f;
        node.cameFrom = this.cameFrom;
        node.closed = this.closed;
        node.walkedDistance = this.walkedDistance;
        node.costMalus = this.costMalus;
        node.type = this.type;
        return node;
    }

    public static int createHash(long pX, int pY, long pZ) {
        int i = Long.hashCode(pX);
        i = 31 * i + pY;
        return 31 * i + Long.hashCode(pZ);
    }

    public float distanceTo(Node pPathpoint) {
        float f = (float) (pPathpoint.x - this.x);
        float f1 = (float) (pPathpoint.y - this.y);
        float f2 = (float) (pPathpoint.z - this.z);
        return Mth.sqrt(f * f + f1 * f1 + f2 * f2);
    }

    public float distanceToXZ(Node p_230614_) {
        float f = (float) (p_230614_.x - this.x);
        float f1 = (float) (p_230614_.z - this.z);
        return Mth.sqrt(f * f + f1 * f1);
    }

    public float distanceTo(BlockPos pPos) {
        float f = (float) (pPos.getX() - this.x);
        float f1 = (float) (pPos.getY() - this.y);
        float f2 = (float) (pPos.getZ() - this.z);
        return Mth.sqrt(f * f + f1 * f1 + f2 * f2);
    }

    public float distanceToSqr(Node pPathpoint) {
        float f = (float) (pPathpoint.x - this.x);
        float f1 = (float) (pPathpoint.y - this.y);
        float f2 = (float) (pPathpoint.z - this.z);
        return f * f + f1 * f1 + f2 * f2;
    }

    public float distanceToSqr(BlockPos pPos) {
        float f = (float) (pPos.getX() - this.x);
        float f1 = (float) (pPos.getY() - this.y);
        float f2 = (float) (pPos.getZ() - this.z);
        return f * f + f1 * f1 + f2 * f2;
    }

    public float distanceManhattan(Node pPathpoint) {
        float f = (float) Math.abs(pPathpoint.x - this.x);
        float f1 = (float) Math.abs(pPathpoint.y - this.y);
        float f2 = (float) Math.abs(pPathpoint.z - this.z);
        return f + f1 + f2;
    }

    public float distanceManhattan(BlockPos pPos) {
        float f = (float) Math.abs(pPos.getX() - this.x);
        float f1 = (float) Math.abs(pPos.getY() - this.y);
        float f2 = (float) Math.abs(pPos.getZ() - this.z);
        return f + f1 + f2;
    }

    public BlockPos asBlockPos() {
        return new BlockPos(this.x, this.y, this.z);
    }

    public Vec3 asVec3() {
        return new Vec3((double) this.x, (double) this.y, (double) this.z);
    }

    public boolean equals(Object pOther) {
        if (!(pOther instanceof Node node)) {
            return false;
        } else {
            return this.hash == node.hash && this.x == node.x && this.y == node.y && this.z == node.z;
        }
    }

    public int hashCode() {
        return this.hash;
    }

    public boolean inOpenSet() {
        return this.heapIdx >= 0;
    }

    public String toString() {
        return "Node{x=" + this.x + ", y=" + this.y + ", z=" + this.z + "}";
    }

    public void writeToStream(FriendlyByteBuf pBuffer) {
        pBuffer.writeLong(this.x);
        pBuffer.writeInt(this.y);
        pBuffer.writeLong(this.z);
        pBuffer.writeFloat(this.walkedDistance);
        pBuffer.writeFloat(this.costMalus);
        pBuffer.writeBoolean(this.closed);
        pBuffer.writeInt(this.type.ordinal());
        pBuffer.writeFloat(this.f);
    }

    public static Node createFromStream(FriendlyByteBuf pBuf) {
        Node node = new Node(pBuf.readLong(), pBuf.readInt(), pBuf.readLong());
        node.walkedDistance = pBuf.readFloat();
        node.costMalus = pBuf.readFloat();
        node.closed = pBuf.readBoolean();
        node.type = BlockPathTypes.values()[pBuf.readInt()];
        node.f = pBuf.readFloat();
        return node;
    }

    /**
    * Complete node identity for evaluator cache lookups. The former packed
    * 32-bit key discarded high X/Z bits and merged distinct Far Lands nodes.
    */
    static record NodeKey(long x, int y, long z) {
    }
}
