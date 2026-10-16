package net.minecraft.world.level.pathfinder;

import net.minecraft.network.FriendlyByteBuf;

public class Target extends Node {
    private float bestHeuristic = Float.MAX_VALUE;
    private Node bestNode;
    private boolean reached;

    public Target(Node pNode) {
        super(pNode.x, pNode.y, pNode.z);
    }

    public Target(long pX, int pY, long pZ) {
        super(pX, pY, pZ);
    }

    public void updateBest(float pHeuristic, Node pNode) {
        if (pHeuristic < this.bestHeuristic) {
            this.bestHeuristic = pHeuristic;
            this.bestNode = pNode;
        }
    }

    public Node getBestNode() {
        return this.bestNode;
    }

    public void setReached() {
        this.reached = true;
    }

    public boolean isReached() {
        return this.reached;
    }

    public static Target createFromStream(FriendlyByteBuf pBuffer) {
        Target target = new Target(pBuffer.readLong(), pBuffer.readInt(), pBuffer.readLong());
        target.walkedDistance = pBuffer.readFloat();
        target.costMalus = pBuffer.readFloat();
        target.closed = pBuffer.readBoolean();
        target.type = BlockPathTypes.values()[pBuffer.readInt()];
        target.f = pBuffer.readFloat();
        return target;
    }
}
