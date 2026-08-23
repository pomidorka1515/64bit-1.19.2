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
   protected DynamicGraphMinFixedPoint(int p_75543_, final int p_75544_, final int p_75545_) {
      if (p_75543_ >= 254) {
         throw new IllegalArgumentException("Level count must be < 254.");
      } else {
         this.levelCount = p_75543_;
         this.queues = new ObjectLinkedOpenHashSet[p_75543_];

         for(int i = 0; i < p_75543_; ++i) {
            this.queues[i] = new ObjectLinkedOpenHashSet<>(p_75544_, 0.5F) {
				private static final long serialVersionUID = 8964L;

			protected void rehash(int p_75611_) {
                  if (p_75611_ > p_75544_) {
                     super.rehash(p_75611_);
                  }

               }
            };
         }

         this.computedLevels = new Object2ByteOpenHashMap<>(p_75545_, 0.5F) {
			private static final long serialVersionUID = 8964L;

			protected void rehash(int p_75620_) {
               if (p_75620_ > p_75545_) {
                  super.rehash(p_75620_);
               }

            }
         };
         this.computedLevels.defaultReturnValue((byte)-1);
         this.firstQueuedLevel = p_75543_;
      }
   }

   private int getKey(int p_75549_, int p_75550_) {
      int i = p_75549_;
      if (p_75549_ > p_75550_) {
         i = p_75550_;
      }

      if (i > this.levelCount - 1) {
         i = this.levelCount - 1;
      }

      return i;
   }

   private void checkFirstQueuedLevel(int p_75547_) {
      int i = this.firstQueuedLevel;
      this.firstQueuedLevel = p_75547_;

      for(int j = i + 1; j < p_75547_; ++j) {
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

   public void removeIf(Predicate<T> p_75582_) {
      ObjectList<T> longlist = new ObjectArrayList<T>();
      this.computedLevels.keySet().forEach((T p_75586_) -> {
         if (p_75582_.test(p_75586_)) {
            longlist.add(p_75586_);
         }

      });
      longlist.forEach((java.util.function.Consumer<T>)this::removeFromQueue);
   }

   private void dequeue(T p_75559_, int p_75560_, int p_75561_, boolean p_75562_) {
      if (p_75562_) {
         this.computedLevels.removeByte(p_75559_);
      }

      this.queues[p_75560_].remove(p_75559_);
      if (this.queues[p_75560_].isEmpty() && this.firstQueuedLevel == p_75560_) {
         this.checkFirstQueuedLevel(p_75561_);
      }

   }

   private void enqueue(T p_75555_, int p_75556_, int p_75557_) {
      this.computedLevels.put(p_75555_, (byte)p_75556_);
      this.queues[p_75557_].add(p_75555_);
      if (this.firstQueuedLevel > p_75557_) {
         this.firstQueuedLevel = p_75557_;
      }

   }

   protected void checkNode(T p_75602_) {
      this.checkEdge(p_75602_, p_75602_, this.levelCount - 1, false);
   }

   protected void checkEdge(T p_75577_, T p_75578_, int p_75579_, boolean p_75580_) {
      this.checkEdge(p_75577_, p_75578_, p_75579_, this.getLevel(p_75578_), this.computedLevels.getByte(p_75578_) & 255, p_75580_);
      this.hasWork = this.firstQueuedLevel < this.levelCount;
   }

   private void checkEdge(T p_75570_, T p_75571_, int p_75572_, int p_75573_, int p_75574_, boolean p_75575_) {
      if (!this.isSource(p_75571_)) {
         p_75572_ = Mth.clamp(p_75572_, 0, this.levelCount - 1);
         p_75573_ = Mth.clamp(p_75573_, 0, this.levelCount - 1);
         boolean flag;
         if (p_75574_ == 255) {
            flag = true;
            p_75574_ = p_75573_;
         } else {
            flag = false;
         }

         int i;
         if (p_75575_) {
            i = Math.min(p_75574_, p_75572_);
         } else {
            i = Mth.clamp(this.getComputedLevel(p_75571_, p_75570_, p_75572_), 0, this.levelCount - 1);
         }

         int j = this.getKey(p_75573_, p_75574_);
         if (p_75573_ != i) {
            int k = this.getKey(p_75573_, i);
            if (j != k && !flag) {
               this.dequeue(p_75571_, j, k, false);
            }

            this.enqueue(p_75571_, i, k);
         } else if (!flag) {
            this.dequeue(p_75571_, j, this.levelCount, true);
         }

      }
   }

   protected final void checkNeighbor(T p_75594_, T p_75595_, int p_75596_, boolean p_75597_) {
      int i = this.computedLevels.getByte(p_75595_) & 255;
      int j = Mth.clamp(this.computeLevelFromNeighbor(p_75594_, p_75595_, p_75596_), 0, this.levelCount - 1);
      if (p_75597_) {
         this.checkEdge(p_75594_, p_75595_, j, this.getLevel(p_75595_), i, true);
      } else {
         int k;
         boolean flag;
         if (i == 255) {
            flag = true;
            k = Mth.clamp(this.getLevel(p_75595_), 0, this.levelCount - 1);
         } else {
            k = i;
            flag = false;
         }

         if (j == k) {
            this.checkEdge(p_75594_, p_75595_, this.levelCount - 1, flag ? k : this.getLevel(p_75595_), i, false);
         }
      }

   }

   protected final boolean hasWork() {
      return this.hasWork;
   }

   protected final int runUpdates(int p_75589_) {
      if (this.firstQueuedLevel >= this.levelCount) {
         return p_75589_;
      } else {
         while(this.firstQueuedLevel < this.levelCount && p_75589_ > 0) {
            --p_75589_;
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
         return p_75589_;
      }
   }

   public int getQueueSize() {
      return this.computedLevels.size();
   }

   protected abstract boolean isSource(T p_75551_);

   protected abstract int getComputedLevel(T p_75566_, T p_75567_, int p_75568_);

   protected abstract void checkNeighborsAfterUpdate(T p_75563_, int p_75564_, boolean p_75565_);

   protected abstract int getLevel(T p_75599_);

   protected abstract void setLevel(T p_75552_, int p_75553_);

   protected abstract int computeLevelFromNeighbor(T p_75590_, T p_75591_, int p_75592_);
}
