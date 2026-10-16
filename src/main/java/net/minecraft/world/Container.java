package net.minecraft.world;

import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public interface Container extends Clearable {
    int LARGE_MAX_STACK_SIZE = 64;

    int getContainerSize();

    boolean isEmpty();

    ItemStack getItem(int pSlot);

    ItemStack removeItem(int pSlot, int pAmount);

    ItemStack removeItemNoUpdate(int pSlot);

    void setItem(int pSlot, ItemStack pStack);

    default int getMaxStackSize() {
        return 64;
    }

    void setChanged();

    boolean stillValid(Player pPlayer);

    default void startOpen(Player pPlayer) {
    }

    default void stopOpen(Player pPlayer) {
    }

    default boolean canPlaceItem(int pIndex, ItemStack pStack) {
        return true;
    }

    default int countItem(Item pItem) {
        int i = 0;

        for (int j = 0; j < this.getContainerSize(); ++j) {
            ItemStack itemstack = this.getItem(j);
            if (itemstack.getItem().equals(pItem)) {
                i += itemstack.getCount();
            }
        }

        return i;
    }

    default boolean hasAnyOf(Set<Item> pSet) {
        return this.hasAnyMatching((p_216873_) -> {
            return !p_216873_.isEmpty() && pSet.contains(p_216873_.getItem());
        });
    }

    default boolean hasAnyMatching(Predicate<ItemStack> p_216875_) {
        for (int i = 0; i < this.getContainerSize(); ++i) {
            ItemStack itemstack = this.getItem(i);
            if (p_216875_.test(itemstack)) {
                return true;
            }
        }

        return false;
    }
}
