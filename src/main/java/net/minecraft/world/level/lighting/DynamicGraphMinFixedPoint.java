package net.minecraft.world.level.lighting;

import java.util.function.Predicate;

import it.unimi.dsi.fastutil.objects.Object2ByteMap;
import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.util.Mth;

public abstract class DynamicGraphMinFixedPoint<T> {
    private static final int NO_COMPUTED_LEVEL = 255;
    private final int levelCount;
    private final ObjectLinkedOpenHashSet<T>[] queues;
    private final Object2ByteMap<T> computedLevels;
    private int firstQueuedLevel;
    private volatile boolean hasWork;

    @SuppressWarnings("unchecked")
    protected DynamicGraphMinFixedPoint(int pFirstQueuedLevel, final int pWidth, final int pHeight) {
        if (pFirstQueuedLevel >= 254) {
            throw new IllegalArgumentException("Level count must be < 254.");
        } else {
            this.levelCount = pFirstQueuedLevel;
            this.queues = new ObjectLinkedOpenHashSet[pFirstQueuedLevel];

            for (int i = 0; i < pFirstQueuedLevel; ++i) {
                this.queues[i] = new ObjectLinkedOpenHashSet<>(pWidth, 0.5F) {
                    private static final long serialVersionUID = 8964L;

                    protected void rehash(int p_75611_) {
                        if (p_75611_ > pWidth) {
                            super.rehash(p_75611_);
                        }
                    }
                };
            }

            this.computedLevels = new Object2ByteOpenHashMap<>(pHeight, 0.5F) {
                private static final long serialVersionUID = 8964L;

                protected void rehash(int p_75620_) {
                    if (p_75620_ > pHeight) {
                        super.rehash(p_75620_);
                    }
                }
            };
            this.computedLevels.defaultReturnValue((byte) -1);
            this.firstQueuedLevel = pFirstQueuedLevel;
        }
    }

    private int getKey(int pLevel1, int pLevel2) {
        int i = pLevel1;
        if (pLevel1 > pLevel2) {
            i = pLevel2;
        }

        if (i > this.levelCount - 1) {
            i = this.levelCount - 1;
        }

        return i;
    }

    private void checkFirstQueuedLevel(int pMaxLevel) {
        int i = this.firstQueuedLevel;
        this.firstQueuedLevel = pMaxLevel;

        for (int j = i + 1; j < pMaxLevel; ++j) {
            if (!this.queues[j].isEmpty()) {
                this.firstQueuedLevel = j;
                break;
            }
        }
    }

    protected void removeFromQueue(T p_75601_) {
        int i = this.computedLevels.getByte(p_75601_) & 255;
        if (i != 255) {
            int j = this.getLevel(p_75601_);
            int k = this.getKey(j, i);
            this.dequeue(p_75601_, k, this.levelCount, true);
            this.hasWork = this.firstQueuedLevel < this.levelCount;
        }
    }

    public void removeIf(Predicate<T> pPredicate) {
        ObjectList<T> longlist = new ObjectArrayList<T>();
        this.computedLevels.keySet().forEach((T p_75586_) -> {
            if (pPredicate.test(p_75586_)) {
                longlist.add(p_75586_);
            }
        });
        longlist.forEach((java.util.function.Consumer<T>) this::removeFromQueue);
    }

    private void dequeue(T pPos, int pLevel, int pMaxLevel, boolean pRemoveAll) {
        if (pRemoveAll) {
            this.computedLevels.removeByte(pPos);
        }

        this.queues[pLevel].remove(pPos);
        if (this.queues[pLevel].isEmpty() && this.firstQueuedLevel == pLevel) {
            this.checkFirstQueuedLevel(pMaxLevel);
        }
    }

    private void enqueue(T pPos, int pLevelToSet, int pUpdateLevel) {
        this.computedLevels.put(pPos, (byte) pLevelToSet);
        this.queues[pUpdateLevel].add(pPos);
        if (this.firstQueuedLevel > pUpdateLevel) {
            this.firstQueuedLevel = pUpdateLevel;
        }
    }

    protected void checkNode(T pLevelPos) {
        this.checkEdge(pLevelPos, pLevelPos, this.levelCount - 1, false);
    }

    protected void checkEdge(T pFromPos, T pToPos, int pNewLevel, boolean pIsDecreasing) {
        this.checkEdge(pFromPos, pToPos, pNewLevel, this.getLevel(pToPos), this.computedLevels.getByte(pToPos) & 255, pIsDecreasing);
        this.hasWork = this.firstQueuedLevel < this.levelCount;
    }

    private void checkEdge(T pFromPos, T pToPos, int pNewLevel, int pPreviousLevel, int pPropagationLevel, boolean pIsDecreasing) {
        if (!this.isSource(pToPos)) {
            pNewLevel = Mth.clamp(pNewLevel, 0, this.levelCount - 1);
            pPreviousLevel = Mth.clamp(pPreviousLevel, 0, this.levelCount - 1);
            boolean flag;
            if (pPropagationLevel == 255) {
                flag = true;
                pPropagationLevel = pPreviousLevel;
            } else {
                flag = false;
            }

            int i;
            if (pIsDecreasing) {
                i = Math.min(pPropagationLevel, pNewLevel);
            } else {
                i = Mth.clamp(this.getComputedLevel(pToPos, pFromPos, pNewLevel), 0, this.levelCount - 1);
            }

            int j = this.getKey(pPreviousLevel, pPropagationLevel);
            if (pPreviousLevel != i) {
                int k = this.getKey(pPreviousLevel, i);
                if (j != k && !flag) {
                    this.dequeue(pToPos, j, k, false);
                }

                this.enqueue(pToPos, i, k);
            } else if (!flag) {
                this.dequeue(pToPos, j, this.levelCount, true);
            }
        }
    }

    protected final void checkNeighbor(T pFromPos, T pToPos, int pSourceLevel, boolean pIsDecreasing) {
        int i = this.computedLevels.getByte(pToPos) & 255;
        int j = Mth.clamp(this.computeLevelFromNeighbor(pFromPos, pToPos, pSourceLevel), 0, this.levelCount - 1);
        if (pIsDecreasing) {
            this.checkEdge(pFromPos, pToPos, j, this.getLevel(pToPos), i, true);
        } else {
            int k;
            boolean flag;
            if (i == 255) {
                flag = true;
                k = Mth.clamp(this.getLevel(pToPos), 0, this.levelCount - 1);
            } else {
                k = i;
                flag = false;
            }

            if (j == k) {
                this.checkEdge(pFromPos, pToPos, this.levelCount - 1, flag ? k : this.getLevel(pToPos), i, false);
            }
        }
    }

    protected final boolean hasWork() {
        return this.hasWork;
    }

    protected final int runUpdates(int pToUpdateCount) {
        if (this.firstQueuedLevel >= this.levelCount) {
            return pToUpdateCount;
        } else {
            while (this.firstQueuedLevel < this.levelCount && pToUpdateCount > 0) {
                --pToUpdateCount;
                ObjectLinkedOpenHashSet<T> longlinkedopenhashset = this.queues[this.firstQueuedLevel];
                T i = longlinkedopenhashset.removeFirst();
                int j = Mth.clamp(this.getLevel(i), 0, this.levelCount - 1);
                if (longlinkedopenhashset.isEmpty()) {
                    this.checkFirstQueuedLevel(this.levelCount);
                }

                int k = this.computedLevels.removeByte(i) & 255;
                if (k < j) {
                    this.setLevel(i, k);
                    this.checkNeighborsAfterUpdate(i, k, true);
                } else if (k > j) {
                    this.enqueue(i, k, this.getKey(this.levelCount - 1, k));
                    this.setLevel(i, this.levelCount - 1);
                    this.checkNeighborsAfterUpdate(i, j, false);
                }
            }

            this.hasWork = this.firstQueuedLevel < this.levelCount;
            return pToUpdateCount;
        }
    }

    public int getQueueSize() {
        return this.computedLevels.size();
    }

    protected abstract boolean isSource(T pPos);

    protected abstract int getComputedLevel(T pPos, T pExcludedSourcePos, int pLevel);

    protected abstract void checkNeighborsAfterUpdate(T pPos, int pLevel, boolean pIsDecreasing);

    protected abstract int getLevel(T pChunkPos);

    protected abstract void setLevel(T pChunkPos, int pLevel);

    protected abstract int computeLevelFromNeighbor(T pStartPos, T pEndPos, int pStartLevel);
}
