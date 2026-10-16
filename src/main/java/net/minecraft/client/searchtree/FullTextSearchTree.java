package net.minecraft.client.searchtree;

import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FullTextSearchTree<T> extends IdSearchTree<T> {
    private final List<T> contents;
    private final Function<T, Stream<String>> filler;
    private PlainTextSearchTree<T> plainTextSearchTree = PlainTextSearchTree.empty();

    public FullTextSearchTree(Function<T, Stream<String>> p_235155_, Function<T, Stream<ResourceLocation>> p_235156_, List<T> p_235157_) {
        super(p_235156_, p_235157_);
        this.contents = p_235157_;
        this.filler = p_235155_;
    }

    public void refresh() {
        super.refresh();
        this.plainTextSearchTree = PlainTextSearchTree.create(this.contents, this.filler);
    }

    protected List<T> searchPlainText(String pQuery) {
        return this.plainTextSearchTree.search(pQuery);
    }

    protected List<T> searchResourceLocation(String pNamespace, String pPath) {
        List<T> list = this.resourceLocationSearchTree.searchNamespace(pNamespace);
        List<T> list1 = this.resourceLocationSearchTree.searchPath(pPath);
        List<T> list2 = this.plainTextSearchTree.search(pPath);
        Iterator<T> iterator = new MergingUniqueIterator<>(list1.iterator(), list2.iterator(), this.additionOrder);
        return ImmutableList.copyOf(new IntersectionIterator<>(list.iterator(), iterator, this.additionOrder));
    }
}
