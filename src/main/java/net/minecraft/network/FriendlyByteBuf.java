package net.minecraft.network;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import io.netty.util.ByteProcessor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.time.Instant;
import java.util.BitSet;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.IdMap;
import net.minecraft.core.Int1088;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Crypt;
import net.minecraft.util.CryptException;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.SectorVec3;
import net.minecraft.world.phys.Vec3;

public class FriendlyByteBuf extends ByteBuf {
    private static final int MAX_VARINT_SIZE = 5;
    private static final int MAX_VARLONG_SIZE = 10;
    private static final int DEFAULT_NBT_QUOTA = 2097152;
    private final ByteBuf source;
    public static final short MAX_STRING_LENGTH = Short.MAX_VALUE;
    public static final int MAX_COMPONENT_STRING_LENGTH = 262144;
    private static final int PUBLIC_KEY_SIZE = 256;
    private static final int MAX_PUBLIC_KEY_HEADER_SIZE = 256;
    private static final int MAX_PUBLIC_KEY_LENGTH = 512;

    public FriendlyByteBuf(ByteBuf pSource) {
        this.source = pSource;
    }

    public static int getVarIntSize(int pInput) {
        for (int i = 1; i < 5; ++i) {
            if ((pInput & -1 << i * 7) == 0) {
                return i;
            }
        }

        return 5;
    }

    public static int getVarLongSize(long pInput) {
        for (int i = 1; i < 10; ++i) {
            if ((pInput & -1L << i * 7) == 0L) {
                return i;
            }
        }

        return 10;
    }

    /** @deprecated */
    public <T> T readWithCodec(Codec<T> pCodec) {
        CompoundTag compoundtag = this.readAnySizeNbt();
        DataResult<T> dataresult = pCodec.parse(NbtOps.INSTANCE, compoundtag);
        dataresult.error().ifPresent((p_178380_) -> {
            throw new EncoderException("Failed to decode: " + p_178380_.message() + " " + compoundtag);
        });
        return dataresult.result().get();
    }

    /** @deprecated */
    public <T> void writeWithCodec(Codec<T> pCodec, T pData) {
        DataResult<Tag> dataresult = pCodec.encodeStart(NbtOps.INSTANCE, pData);
        dataresult.error().ifPresent((p_178349_) -> {
            throw new EncoderException("Failed to encode: " + p_178349_.message() + " " + pData);
        });
        this.writeNbt((CompoundTag) dataresult.result().get());
    }

    public <T> void writeId(IdMap<T> p_236819_, T p_236820_) {
        int i = p_236819_.getId(p_236820_);
        if (i == -1) {
            throw new IllegalArgumentException("Can't find id for '" + p_236820_ + "' in map " + p_236819_);
        } else {
            this.writeVarInt(i);
        }
    }

    @Nullable
    public <T> T readById(IdMap<T> p_236817_) {
        int i = this.readVarInt();
        return p_236817_.byId(i);
    }

    public static <T> IntFunction<T> limitValue(IntFunction<T> p_182696_, int p_182697_) {
        return (p_182686_) -> {
            if (p_182686_ > p_182697_) {
                throw new DecoderException("Value " + p_182686_ + " is larger than limit " + p_182697_);
            } else {
                return p_182696_.apply(p_182686_);
            }
        };
    }

    public <T, C extends Collection<T>> C readCollection(IntFunction<C> pCollectionFactory, FriendlyByteBuf.Reader<T> pElementReader) {
        int i = this.readVarInt();
        C c = pCollectionFactory.apply(i);

        for (int j = 0; j < i; ++j) {
            c.add(pElementReader.apply(this));
        }

        return c;
    }

    public <T> void writeCollection(Collection<T> pCollection, FriendlyByteBuf.Writer<T> pElementWriter) {
        this.writeVarInt(pCollection.size());

        for (T t : pCollection) {
            pElementWriter.accept(this, t);
        }
    }

    public <T> List<T> readList(FriendlyByteBuf.Reader<T> pElementReader) {
        return this.readCollection(Lists::newArrayListWithCapacity, pElementReader);
    }

    public IntList readIntIdList() {
        int i = this.readVarInt();
        IntList intlist = new IntArrayList();

        for (int j = 0; j < i; ++j) {
            intlist.add(this.readVarInt());
        }

        return intlist;
    }

    public void writeIntIdList(IntList p_178346_) {
        this.writeVarInt(p_178346_.size());
        p_178346_.forEach((java.util.function.IntConsumer) this::writeVarInt);
    }

    public <K, V, M extends Map<K, V>> M readMap(IntFunction<M> pMapFactory, FriendlyByteBuf.Reader<K> pKeyReader, FriendlyByteBuf.Reader<V> pValueReader) {
        int i = this.readVarInt();
        M m = pMapFactory.apply(i);

        for (int j = 0; j < i; ++j) {
            K k = pKeyReader.apply(this);
            V v = pValueReader.apply(this);
            m.put(k, v);
        }

        return m;
    }

    public <K, V> Map<K, V> readMap(FriendlyByteBuf.Reader<K> pKeyReader, FriendlyByteBuf.Reader<V> pValueReader) {
        return this.readMap(Maps::newHashMapWithExpectedSize, pKeyReader, pValueReader);
    }

    public <K, V> void writeMap(Map<K, V> pMap, FriendlyByteBuf.Writer<K> pKeyWriter, FriendlyByteBuf.Writer<V> pValueWriter) {
        this.writeVarInt(pMap.size());
        pMap.forEach((p_236856_, p_236857_) -> {
            pKeyWriter.accept(this, p_236856_);
            pValueWriter.accept(this, p_236857_);
        });
    }

    public void readWithCount(Consumer<FriendlyByteBuf> pReader) {
        int i = this.readVarInt();

        for (int j = 0; j < i; ++j) {
            pReader.accept(this);
        }
    }

    public <T> void writeOptional(Optional<T> p_236836_, FriendlyByteBuf.Writer<T> p_236837_) {
        if (p_236836_.isPresent()) {
            this.writeBoolean(true);
            p_236837_.accept(this, p_236836_.get());
        } else {
            this.writeBoolean(false);
        }
    }

    public <T> Optional<T> readOptional(FriendlyByteBuf.Reader<T> p_236861_) {
        return this.readBoolean() ? Optional.of(p_236861_.apply(this)) : Optional.empty();
    }

    @Nullable
    public <T> T readNullable(FriendlyByteBuf.Reader<T> p_236869_) {
        return (T) (this.readBoolean() ? p_236869_.apply(this) : null);
    }

    public <T> void writeNullable(@Nullable T p_236822_, FriendlyByteBuf.Writer<T> p_236823_) {
        if (p_236822_ != null) {
            this.writeBoolean(true);
            p_236823_.accept(this, p_236822_);
        } else {
            this.writeBoolean(false);
        }
    }

    public <L, R> void writeEither(Either<L, R> p_236811_, FriendlyByteBuf.Writer<L> p_236812_, FriendlyByteBuf.Writer<R> p_236813_) {
        p_236811_.ifLeft((p_236867_) -> {
                     this.writeBoolean(true);
                     p_236812_.accept(this, p_236867_);
                 })
                .ifRight((p_236852_) -> {
                    this.writeBoolean(false);
                    p_236813_.accept(this, p_236852_);
                });
    }

    public <L, R> Either<L, R> readEither(FriendlyByteBuf.Reader<L> p_236863_, FriendlyByteBuf.Reader<R> p_236864_) {
        return this.readBoolean() ? Either.left(p_236863_.apply(this)) : Either.right(p_236864_.apply(this));
    }

    public byte[] readByteArray() {
        return this.readByteArray(this.readableBytes());
    }

    public FriendlyByteBuf writeByteArray(byte[] pArray) {
        this.writeVarInt(pArray.length);
        this.writeBytes(pArray);
        return this;
    }

    public byte[] readByteArray(int pMaxLength) {
        int i = this.readVarInt();
        if (i > pMaxLength) {
            throw new DecoderException("ByteArray with size " + i + " is bigger than allowed " + pMaxLength);
        } else {
            byte[] abyte = new byte[i];
            this.readBytes(abyte);
            return abyte;
        }
    }

    public FriendlyByteBuf writeVarIntArray(int[] pArray) {
        this.writeVarInt(pArray.length);

        for (int i : pArray) {
            this.writeVarInt(i);
        }

        return this;
    }

    public int[] readVarIntArray() {
        return this.readVarIntArray(this.readableBytes());
    }

    public int[] readVarIntArray(int pMaxLength) {
        int i = this.readVarInt();
        if (i > pMaxLength) {
            throw new DecoderException("VarIntArray with size " + i + " is bigger than allowed " + pMaxLength);
        } else {
            int[] aint = new int[i];

            for (int j = 0; j < aint.length; ++j) {
                aint[j] = this.readVarInt();
            }

            return aint;
        }
    }

    public FriendlyByteBuf writeLongArray(long[] pArray) {
        this.writeVarInt(pArray.length);

        for (long i : pArray) {
            this.writeLong(i);
        }

        return this;
    }

    public long[] readLongArray() {
        return this.readLongArray((long[]) null);
    }

    public long[] readLongArray(@Nullable long[] pArray) {
        return this.readLongArray(pArray, this.readableBytes() / 8);
    }

    public long[] readLongArray(@Nullable long[] pArray, int pMaxLength) {
        int i = this.readVarInt();
        if (pArray == null || pArray.length != i) {
            if (i > pMaxLength) {
                throw new DecoderException("LongArray with size " + i + " is bigger than allowed " + pMaxLength);
            }

            pArray = new long[i];
        }

        for (int j = 0; j < pArray.length; ++j) {
            pArray[j] = this.readLong();
        }

        return pArray;
    }

    @VisibleForTesting
    public byte[] accessByteBufWithCorrectSize() {
        int i = this.writerIndex();
        byte[] abyte = new byte[i];
        this.getBytes(0, abyte);
        return abyte;
    }

    public FriendlyByteBuf writeInt1088(Int1088 value) {
        this.writeVarInt(value.limbCount());
        for (int i = 0; i < value.limbCount(); ++i) {
            long limb = value.limb(i);
            this.writeVarLong((limb << 1) ^ (limb >> 63));
        }
        return this;
    }

    public Int1088 readInt1088() {
        try {
            int start = this.readerIndex();
            int count = this.readVarInt();
            if (count < 1 || count > Int1088.LIMBS || this.readerIndex() - start != 1) {
                throw new DecoderException("Invalid Int1088 limb count: " + count);
            }
            long[] limbs = new long[count];
            for (int i = 0; i < count; ++i) {
                start = this.readerIndex();
                long encoded = this.readVarLong();
                int size = this.readerIndex() - start;
                if (size != getVarLongSize(encoded) || size == 10 && (this.getUnsignedByte(start + 9) & 126) != 0) {
                    throw new DecoderException("Non-canonical Int1088 limb VarLong");
                }
                limbs[i] = (encoded >>> 1) ^ -(encoded & 1L);
            }
            Int1088 value = Int1088.fromLimbs(limbs, 0, count);
            if (value.limbCount() != count) {
                throw new DecoderException("Non-canonical Int1088 limb count: " + count);
            }
            return value;
        } catch (DecoderException exception) {
            throw exception;
        } catch (IndexOutOfBoundsException exception) {
            throw new DecoderException("Truncated Int1088", exception);
        } catch (RuntimeException exception) {
            throw new DecoderException("Invalid Int1088", exception);
        }
    }

    public FriendlyByteBuf writeInt1088(long[] limbs, int offset, int length) {
        return this.writeInt1088(Int1088.fromLimbs(limbs, offset, length));
    }

    public BlockPos readBlockPos() {
        return new BlockPos(this.readInt1088(), this.readInt(), this.readInt1088());
    }

    public FriendlyByteBuf writeBlockPos(BlockPos pPos) {
        this.writeInt1088(pPos.getX());
        this.writeInt(pPos.getY());
        this.writeInt1088(pPos.getZ());
        return this;
    }

    public ChunkPos readChunkPos() {
        return new ChunkPos(this.readInt1088(), this.readInt1088());
    }

    public FriendlyByteBuf writeChunkPos(ChunkPos pChunkPos) {
        this.writeInt1088(pChunkPos.x());
        this.writeInt1088(pChunkPos.z());
        return this;
    }

    public SectionPos readSectionPos() {
        return SectionPos.of(this.readInt1088(), this.readInt(), this.readInt1088());
    }

    public FriendlyByteBuf writeSectionPos(SectionPos pSectionPos) {
        this.writeInt1088(pSectionPos.getX());
        this.writeInt(pSectionPos.getY());
        this.writeInt1088(pSectionPos.getZ());
        return this;
    }

    public SectorVec3 readSectorVec3() {
        Int1088 x = this.readInt1088();
        double subX = this.readDouble();
        double y = this.readDouble();
        Int1088 z = this.readInt1088();
        double subZ = this.readDouble();
        if (!Double.isFinite(subX) || !Double.isFinite(subZ) || !Double.isFinite(y)
                || subX < 0.0D || subX >= 1.0D || subZ < 0.0D || subZ >= 1.0D) {
            throw new DecoderException("Invalid sector position");
        }
        return SectorVec3.fromBlockAndFraction(x, subX, y, z, subZ);
    }

    public FriendlyByteBuf writeSectorVec3(SectorVec3 pos) {
        this.writeInt1088(pos.blockX());
        this.writeDouble(pos.subX());
        this.writeDouble(pos.y());
        this.writeInt1088(pos.blockZ());
        this.writeDouble(pos.subZ());
        return this;
    }

    public GlobalPos readGlobalPos() {
        ResourceKey<Level> resourcekey = this.readResourceKey(Registry.DIMENSION_REGISTRY);
        BlockPos blockpos = this.readBlockPos();
        return GlobalPos.of(resourcekey, blockpos);
    }

    public void writeGlobalPos(GlobalPos p_236815_) {
        this.writeResourceKey(p_236815_.dimension());
        this.writeBlockPos(p_236815_.pos());
    }

    public Component readComponent() {
        Component component = Component.Serializer.fromJson(this.readUtf(262144));
        if (component == null) {
            throw new DecoderException("Received unexpected null component");
        } else {
            return component;
        }
    }

    public FriendlyByteBuf writeComponent(Component pComponent) {
        return this.writeUtf(Component.Serializer.toJson(pComponent), 262144);
    }

    public <T extends Enum<T>> T readEnum(Class<T> pEnumClass) {
        return (pEnumClass.getEnumConstants())[this.readVarInt()];
    }

    public FriendlyByteBuf writeEnum(Enum<?> pValue) {
        return this.writeVarInt(pValue.ordinal());
    }

    public int readVarInt() {
        int i = 0;
        int j = 0;

        byte b0;
        do {
            b0 = this.readByte();
            i |= (b0 & 127) << j++ * 7;
            if (j > 5) {
                throw new RuntimeException("VarInt too big");
            }
        } while ((b0 & 128) == 128);

        return i;
    }

    public long readVarLong() {
        long i = 0L;
        int j = 0;

        byte b0;
        do {
            b0 = this.readByte();
            i |= (long) (b0 & 127) << j++ * 7;
            if (j > 10) {
                throw new RuntimeException("VarLong too big");
            }
        } while ((b0 & 128) == 128);

        return i;
    }

    public FriendlyByteBuf writeUUID(UUID pUuid) {
        this.writeLong(pUuid.getMostSignificantBits());
        this.writeLong(pUuid.getLeastSignificantBits());
        return this;
    }

    public UUID readUUID() {
        return new UUID(this.readLong(), this.readLong());
    }

    public FriendlyByteBuf writeVarInt(int p_130131_) {
        while ((p_130131_ & -128) != 0) {
            this.writeByte(p_130131_ & 127 | 128);
            p_130131_ >>>= 7;
        }

        this.writeByte(p_130131_);
        return this;
    }

    public FriendlyByteBuf writeVarLong(long pValue) {
        while ((pValue & -128L) != 0L) {
            this.writeByte((int) (pValue & 127L) | 128);
            pValue >>>= 7;
        }

        this.writeByte((int) pValue);
        return this;
    }

    public FriendlyByteBuf writeNbt(@Nullable CompoundTag pNbt) {
        if (pNbt == null) {
            this.writeByte(0);
        } else {
            try {
                NbtIo.write(pNbt, new ByteBufOutputStream(this));
            } catch (IOException ioexception) {
                throw new EncoderException(ioexception);
            }
        }

        return this;
    }

    @Nullable
    public CompoundTag readNbt() {
        return this.readNbt(new NbtAccounter(2097152L));
    }

    @Nullable
    public CompoundTag readAnySizeNbt() {
        return this.readNbt(NbtAccounter.UNLIMITED);
    }

    @Nullable
    public CompoundTag readNbt(NbtAccounter p_130082_) {
        int i = this.readerIndex();
        byte b0 = this.readByte();
        if (b0 == 0) {
            return null;
        } else {
            this.readerIndex(i);

            try {
                return NbtIo.read(new ByteBufInputStream(this), p_130082_);
            } catch (IOException ioexception) {
                throw new EncoderException(ioexception);
            }
        }
    }

    public FriendlyByteBuf writeItem(ItemStack pStack) {
        if (pStack.isEmpty()) {
            this.writeBoolean(false);
        } else {
            this.writeBoolean(true);
            Item item = pStack.getItem();
            this.writeId(Registry.ITEM, item);
            this.writeByte(pStack.getCount());
            CompoundTag compoundtag = null;
            if (item.canBeDepleted() || item.shouldOverrideMultiplayerNbt()) {
                compoundtag = pStack.getTag();
            }

            this.writeNbt(compoundtag);
        }

        return this;
    }

    public ItemStack readItem() {
        if (!this.readBoolean()) {
            return ItemStack.EMPTY;
        } else {
            Item item = this.readById(Registry.ITEM);
            int i = this.readByte();
            ItemStack itemstack = new ItemStack(item, i);
            itemstack.setTag(this.readNbt());
            return itemstack;
        }
    }

    public String readUtf() {
        return this.readUtf(32767);
    }

    public String readUtf(int pMaxLength) {
        int i = getMaxEncodedUtfLength(pMaxLength);
        int j = this.readVarInt();
        if (j > i) {
            throw new DecoderException("The received encoded string buffer length is longer than maximum allowed (" + j + " > " + i + ")");
        } else if (j < 0) {
            throw new DecoderException("The received encoded string buffer length is less than zero! Weird string!");
        } else {
            String s = this.toString(this.readerIndex(), j, StandardCharsets.UTF_8);
            this.readerIndex(this.readerIndex() + j);
            if (s.length() > pMaxLength) {
                throw new DecoderException("The received string length is longer than maximum allowed (" + s.length() + " > " + pMaxLength + ")");
            } else {
                return s;
            }
        }
    }

    public FriendlyByteBuf writeUtf(String pString) {
        return this.writeUtf(pString, 32767);
    }

    public FriendlyByteBuf writeUtf(String pString, int pMaxLength) {
        if (pString.length() > pMaxLength) {
            throw new EncoderException("String too big (was " + pString.length() + " characters, max " + pMaxLength + ")");
        } else {
            byte[] abyte = pString.getBytes(StandardCharsets.UTF_8);
            int i = getMaxEncodedUtfLength(pMaxLength);
            if (abyte.length > i) {
                throw new EncoderException("String too big (was " + abyte.length + " bytes encoded, max " + i + ")");
            } else {
                this.writeVarInt(abyte.length);
                this.writeBytes(abyte);
                return this;
            }
        }
    }

    private static int getMaxEncodedUtfLength(int p_236871_) {
        return p_236871_ * 3;
    }

    public ResourceLocation readResourceLocation() {
        return new ResourceLocation(this.readUtf(32767));
    }

    public FriendlyByteBuf writeResourceLocation(ResourceLocation pResourceLocation) {
        this.writeUtf(pResourceLocation.toString());
        return this;
    }

    public <T> ResourceKey<T> readResourceKey(ResourceKey<? extends Registry<T>> p_236802_) {
        ResourceLocation resourcelocation = this.readResourceLocation();
        return ResourceKey.create(p_236802_, resourcelocation);
    }

    public void writeResourceKey(ResourceKey<?> p_236859_) {
        this.writeResourceLocation(p_236859_.location());
    }

    public Date readDate() {
        return new Date(this.readLong());
    }

    public FriendlyByteBuf writeDate(Date pTime) {
        this.writeLong(pTime.getTime());
        return this;
    }

    public Instant readInstant() {
        return Instant.ofEpochMilli(this.readLong());
    }

    public void writeInstant(Instant p_236827_) {
        this.writeLong(p_236827_.toEpochMilli());
    }

    public PublicKey readPublicKey() {
        try {
            return Crypt.byteToPublicKey(this.readByteArray(512));
        } catch (CryptException cryptexception) {
            throw new DecoderException("Malformed public key bytes", cryptexception);
        }
    }

    public FriendlyByteBuf writePublicKey(PublicKey p_236825_) {
        this.writeByteArray(p_236825_.getEncoded());
        return this;
    }

    public BlockHitResult readBlockHitResult() {
        BlockPos blockpos = this.readBlockPos();
        Direction direction = this.readEnum(Direction.class);
        float f = this.readFloat();
        float f1 = this.readFloat();
        float f2 = this.readFloat();
        boolean flag = this.readBoolean();
        if (!Float.isFinite(f) || !Float.isFinite(f1) || !Float.isFinite(f2)) {
            throw new DecoderException("Invalid block hit offset");
        }
        // The packet stores the hit point relative to its full-width block address.
        // Reconstruct that split before creating the compatibility Vec3 mirror.
        return new BlockHitResult(SectorVec3.fromBlockAndFraction(blockpos.getX(), (double) f,
                                          (double) blockpos.getY() + (double) f1, blockpos.getZ(), (double) f2),
                direction, blockpos, flag);
    }

    public void writeBlockHitResult(BlockHitResult pResult) {
        SectorVec3 exact = pResult.getExactLocation();
        if (exact == null) {
            throw new EncoderException("Block hit requires an exact position");
        }
        BlockPos blockpos = pResult.getBlockPos();
        this.writeBlockPos(blockpos);
        this.writeEnum(pResult.getDirection());
        SectorVec3 origin = SectorVec3.fromBlockAndFraction(blockpos.getX(), 0.0D, blockpos.getY(), blockpos.getZ(), 0.0D);
        this.writeFloat((float) exact.relativeX(origin));
        this.writeFloat((float) exact.relativeY(origin));
        this.writeFloat((float) exact.relativeZ(origin));
        this.writeBoolean(pResult.isInside());
    }

    public BitSet readBitSet() {
        return BitSet.valueOf(this.readLongArray());
    }

    public void writeBitSet(BitSet pBitSet) {
        this.writeLongArray(pBitSet.toLongArray());
    }

    public GameProfile readGameProfile() {
        UUID uuid = this.readUUID();
        String s = this.readUtf(16);
        GameProfile gameprofile = new GameProfile(uuid, s);
        PropertyMap propertymap = gameprofile.getProperties();
        this.readWithCount((p_236809_) -> {
            Property property = this.readProperty();
            propertymap.put(property.getName(), property);
        });
        return gameprofile;
    }

    public void writeGameProfile(GameProfile p_236804_) {
        this.writeUUID(p_236804_.getId());
        this.writeUtf(p_236804_.getName());
        this.writeCollection(p_236804_.getProperties().values(), FriendlyByteBuf::writeProperty);
    }

    public Property readProperty() {
        String s = this.readUtf();
        String s1 = this.readUtf();
        if (this.readBoolean()) {
            String s2 = this.readUtf();
            return new Property(s, s1, s2);
        } else {
            return new Property(s, s1);
        }
    }

    public void writeProperty(Property p_236806_) {
        this.writeUtf(p_236806_.getName());
        this.writeUtf(p_236806_.getValue());
        if (p_236806_.hasSignature()) {
            this.writeBoolean(true);
            this.writeUtf(p_236806_.getSignature());
        } else {
            this.writeBoolean(false);
        }
    }

    public int capacity() {
        return this.source.capacity();
    }

    public ByteBuf capacity(int pNewCapacity) {
        return this.source.capacity(pNewCapacity);
    }

    public int maxCapacity() {
        return this.source.maxCapacity();
    }

    public ByteBufAllocator alloc() {
        return this.source.alloc();
    }

    public ByteOrder order() {
        return this.source.order();
    }

    public ByteBuf order(ByteOrder pEndianness) {
        return this.source.order(pEndianness);
    }

    public ByteBuf unwrap() {
        return this.source.unwrap();
    }

    public boolean isDirect() {
        return this.source.isDirect();
    }

    public boolean isReadOnly() {
        return this.source.isReadOnly();
    }

    public ByteBuf asReadOnly() {
        return this.source.asReadOnly();
    }

    public int readerIndex() {
        return this.source.readerIndex();
    }

    public ByteBuf readerIndex(int pReaderIndex) {
        return this.source.readerIndex(pReaderIndex);
    }

    public int writerIndex() {
        return this.source.writerIndex();
    }

    public ByteBuf writerIndex(int pWriterIndex) {
        return this.source.writerIndex(pWriterIndex);
    }

    public ByteBuf setIndex(int pReaderIndex, int pWriterIndex) {
        return this.source.setIndex(pReaderIndex, pWriterIndex);
    }

    public int readableBytes() {
        return this.source.readableBytes();
    }

    public int writableBytes() {
        return this.source.writableBytes();
    }

    public int maxWritableBytes() {
        return this.source.maxWritableBytes();
    }

    public boolean isReadable() {
        return this.source.isReadable();
    }

    public boolean isReadable(int pSize) {
        return this.source.isReadable(pSize);
    }

    public boolean isWritable() {
        return this.source.isWritable();
    }

    public boolean isWritable(int pSize) {
        return this.source.isWritable(pSize);
    }

    public ByteBuf clear() {
        return this.source.clear();
    }

    public ByteBuf markReaderIndex() {
        return this.source.markReaderIndex();
    }

    public ByteBuf resetReaderIndex() {
        return this.source.resetReaderIndex();
    }

    public ByteBuf markWriterIndex() {
        return this.source.markWriterIndex();
    }

    public ByteBuf resetWriterIndex() {
        return this.source.resetWriterIndex();
    }

    public ByteBuf discardReadBytes() {
        return this.source.discardReadBytes();
    }

    public ByteBuf discardSomeReadBytes() {
        return this.source.discardSomeReadBytes();
    }

    public ByteBuf ensureWritable(int pSize) {
        return this.source.ensureWritable(pSize);
    }

    public int ensureWritable(int pSize, boolean pForce) {
        return this.source.ensureWritable(pSize, pForce);
    }

    public boolean getBoolean(int pIndex) {
        return this.source.getBoolean(pIndex);
    }

    public byte getByte(int pIndex) {
        return this.source.getByte(pIndex);
    }

    public short getUnsignedByte(int pIndex) {
        return this.source.getUnsignedByte(pIndex);
    }

    public short getShort(int pIndex) {
        return this.source.getShort(pIndex);
    }

    public short getShortLE(int pIndex) {
        return this.source.getShortLE(pIndex);
    }

    public int getUnsignedShort(int pIndex) {
        return this.source.getUnsignedShort(pIndex);
    }

    public int getUnsignedShortLE(int pIndex) {
        return this.source.getUnsignedShortLE(pIndex);
    }

    public int getMedium(int pIndex) {
        return this.source.getMedium(pIndex);
    }

    public int getMediumLE(int pIndex) {
        return this.source.getMediumLE(pIndex);
    }

    public int getUnsignedMedium(int pIndex) {
        return this.source.getUnsignedMedium(pIndex);
    }

    public int getUnsignedMediumLE(int pIndex) {
        return this.source.getUnsignedMediumLE(pIndex);
    }

    public int getInt(int pIndex) {
        return this.source.getInt(pIndex);
    }

    public int getIntLE(int pIndex) {
        return this.source.getIntLE(pIndex);
    }

    public long getUnsignedInt(int pIndex) {
        return this.source.getUnsignedInt(pIndex);
    }

    public long getUnsignedIntLE(int pIndex) {
        return this.source.getUnsignedIntLE(pIndex);
    }

    public long getLong(int pIndex) {
        return this.source.getLong(pIndex);
    }

    public long getLongLE(int pIndex) {
        return this.source.getLongLE(pIndex);
    }

    public char getChar(int pIndex) {
        return this.source.getChar(pIndex);
    }

    public float getFloat(int pIndex) {
        return this.source.getFloat(pIndex);
    }

    public double getDouble(int pIndex) {
        return this.source.getDouble(pIndex);
    }

    public ByteBuf getBytes(int pIndex, ByteBuf pDestination) {
        return this.source.getBytes(pIndex, pDestination);
    }

    public ByteBuf getBytes(int pIndex, ByteBuf pDestination, int pLength) {
        return this.source.getBytes(pIndex, pDestination, pLength);
    }

    public ByteBuf getBytes(int pIndex, ByteBuf pDestination, int pDestinationIndex, int pLength) {
        return this.source.getBytes(pIndex, pDestination, pDestinationIndex, pLength);
    }

    public ByteBuf getBytes(int pIndex, byte[] pDestination) {
        return this.source.getBytes(pIndex, pDestination);
    }

    public ByteBuf getBytes(int pIndex, byte[] pDestination, int pDestinationIndex, int pLength) {
        return this.source.getBytes(pIndex, pDestination, pDestinationIndex, pLength);
    }

    public ByteBuf getBytes(int pIndex, ByteBuffer pDestination) {
        return this.source.getBytes(pIndex, pDestination);
    }

    public ByteBuf getBytes(int pIndex, OutputStream pOut, int pLength) throws IOException {
        return this.source.getBytes(pIndex, pOut, pLength);
    }

    public int getBytes(int pIndex, GatheringByteChannel pOut, int pLength) throws IOException {
        return this.source.getBytes(pIndex, pOut, pLength);
    }

    public int getBytes(int pIndex, FileChannel pOut, long pPosition, int pLength) throws IOException {
        return this.source.getBytes(pIndex, pOut, pPosition, pLength);
    }

    public CharSequence getCharSequence(int pIndex, int pLength, Charset pCharset) {
        return this.source.getCharSequence(pIndex, pLength, pCharset);
    }

    public ByteBuf setBoolean(int pIndex, boolean pValue) {
        return this.source.setBoolean(pIndex, pValue);
    }

    public ByteBuf setByte(int pIndex, int pValue) {
        return this.source.setByte(pIndex, pValue);
    }

    public ByteBuf setShort(int pIndex, int pValue) {
        return this.source.setShort(pIndex, pValue);
    }

    public ByteBuf setShortLE(int pIndex, int pValue) {
        return this.source.setShortLE(pIndex, pValue);
    }

    public ByteBuf setMedium(int pIndex, int pValue) {
        return this.source.setMedium(pIndex, pValue);
    }

    public ByteBuf setMediumLE(int pIndex, int pValue) {
        return this.source.setMediumLE(pIndex, pValue);
    }

    public ByteBuf setInt(int pIndex, int pValue) {
        return this.source.setInt(pIndex, pValue);
    }

    public ByteBuf setIntLE(int pIndex, int pValue) {
        return this.source.setIntLE(pIndex, pValue);
    }

    public ByteBuf setLong(int pIndex, long pValue) {
        return this.source.setLong(pIndex, pValue);
    }

    public ByteBuf setLongLE(int pIndex, long pValue) {
        return this.source.setLongLE(pIndex, pValue);
    }

    public ByteBuf setChar(int pIndex, int pValue) {
        return this.source.setChar(pIndex, pValue);
    }

    public ByteBuf setFloat(int pIndex, float pValue) {
        return this.source.setFloat(pIndex, pValue);
    }

    public ByteBuf setDouble(int pIndex, double pValue) {
        return this.source.setDouble(pIndex, pValue);
    }

    public ByteBuf setBytes(int pIndex, ByteBuf pSource) {
        return this.source.setBytes(pIndex, pSource);
    }

    public ByteBuf setBytes(int pIndex, ByteBuf pSource, int pLength) {
        return this.source.setBytes(pIndex, pSource, pLength);
    }

    public ByteBuf setBytes(int pIndex, ByteBuf pSource, int pSourceIndex, int pLength) {
        return this.source.setBytes(pIndex, pSource, pSourceIndex, pLength);
    }

    public ByteBuf setBytes(int pIndex, byte[] pSource) {
        return this.source.setBytes(pIndex, pSource);
    }

    public ByteBuf setBytes(int pIndex, byte[] pSource, int pSourceIndex, int pLength) {
        return this.source.setBytes(pIndex, pSource, pSourceIndex, pLength);
    }

    public ByteBuf setBytes(int pIndex, ByteBuffer pSource) {
        return this.source.setBytes(pIndex, pSource);
    }

    public int setBytes(int pIndex, InputStream pIn, int pLength) throws IOException {
        return this.source.setBytes(pIndex, pIn, pLength);
    }

    public int setBytes(int pIndex, ScatteringByteChannel pIn, int pLength) throws IOException {
        return this.source.setBytes(pIndex, pIn, pLength);
    }

    public int setBytes(int pIndex, FileChannel pIn, long pPosition, int pLength) throws IOException {
        return this.source.setBytes(pIndex, pIn, pPosition, pLength);
    }

    public ByteBuf setZero(int pIndex, int pLength) {
        return this.source.setZero(pIndex, pLength);
    }

    public int setCharSequence(int pIndex, CharSequence pCharSequence, Charset pCharset) {
        return this.source.setCharSequence(pIndex, pCharSequence, pCharset);
    }

    public boolean readBoolean() {
        return this.source.readBoolean();
    }

    public byte readByte() {
        return this.source.readByte();
    }

    public short readUnsignedByte() {
        return this.source.readUnsignedByte();
    }

    public short readShort() {
        return this.source.readShort();
    }

    public short readShortLE() {
        return this.source.readShortLE();
    }

    public int readUnsignedShort() {
        return this.source.readUnsignedShort();
    }

    public int readUnsignedShortLE() {
        return this.source.readUnsignedShortLE();
    }

    public int readMedium() {
        return this.source.readMedium();
    }

    public int readMediumLE() {
        return this.source.readMediumLE();
    }

    public int readUnsignedMedium() {
        return this.source.readUnsignedMedium();
    }

    public int readUnsignedMediumLE() {
        return this.source.readUnsignedMediumLE();
    }

    public int readInt() {
        return this.source.readInt();
    }

    public int readIntLE() {
        return this.source.readIntLE();
    }

    public long readUnsignedInt() {
        return this.source.readUnsignedInt();
    }

    public long readUnsignedIntLE() {
        return this.source.readUnsignedIntLE();
    }

    public long readLong() {
        return this.source.readLong();
    }

    public long readLongLE() {
        return this.source.readLongLE();
    }

    public char readChar() {
        return this.source.readChar();
    }

    public float readFloat() {
        return this.source.readFloat();
    }

    public double readDouble() {
        return this.source.readDouble();
    }

    public ByteBuf readBytes(int pLength) {
        return this.source.readBytes(pLength);
    }

    public ByteBuf readSlice(int pLength) {
        return this.source.readSlice(pLength);
    }

    public ByteBuf readRetainedSlice(int pLength) {
        return this.source.readRetainedSlice(pLength);
    }

    public ByteBuf readBytes(ByteBuf pDestination) {
        return this.source.readBytes(pDestination);
    }

    public ByteBuf readBytes(ByteBuf pDestination, int pLength) {
        return this.source.readBytes(pDestination, pLength);
    }

    public ByteBuf readBytes(ByteBuf pDestination, int pDestinationIndex, int pLength) {
        return this.source.readBytes(pDestination, pDestinationIndex, pLength);
    }

    public ByteBuf readBytes(byte[] pDestination) {
        return this.source.readBytes(pDestination);
    }

    public ByteBuf readBytes(byte[] pDestination, int pDestinationIndex, int pLength) {
        return this.source.readBytes(pDestination, pDestinationIndex, pLength);
    }

    public ByteBuf readBytes(ByteBuffer pDestination) {
        return this.source.readBytes(pDestination);
    }

    public ByteBuf readBytes(OutputStream pOut, int pLength) throws IOException {
        return this.source.readBytes(pOut, pLength);
    }

    public int readBytes(GatheringByteChannel pOut, int pLength) throws IOException {
        return this.source.readBytes(pOut, pLength);
    }

    public CharSequence readCharSequence(int pLength, Charset pCharset) {
        return this.source.readCharSequence(pLength, pCharset);
    }

    public int readBytes(FileChannel pOut, long pPosition, int pLength) throws IOException {
        return this.source.readBytes(pOut, pPosition, pLength);
    }

    public ByteBuf skipBytes(int pLength) {
        return this.source.skipBytes(pLength);
    }

    public ByteBuf writeBoolean(boolean pValue) {
        return this.source.writeBoolean(pValue);
    }

    public ByteBuf writeByte(int pValue) {
        return this.source.writeByte(pValue);
    }

    public ByteBuf writeShort(int pValue) {
        return this.source.writeShort(pValue);
    }

    public ByteBuf writeShortLE(int pValue) {
        return this.source.writeShortLE(pValue);
    }

    public ByteBuf writeMedium(int pValue) {
        return this.source.writeMedium(pValue);
    }

    public ByteBuf writeMediumLE(int pValue) {
        return this.source.writeMediumLE(pValue);
    }

    public ByteBuf writeInt(int pValue) {
        return this.source.writeInt(pValue);
    }

    public ByteBuf writeIntLE(int pValue) {
        return this.source.writeIntLE(pValue);
    }

    public ByteBuf writeLong(long pValue) {
        return this.source.writeLong(pValue);
    }

    public ByteBuf writeLongLE(long pValue) {
        return this.source.writeLongLE(pValue);
    }

    public ByteBuf writeChar(int pValue) {
        return this.source.writeChar(pValue);
    }

    public ByteBuf writeFloat(float pValue) {
        return this.source.writeFloat(pValue);
    }

    public ByteBuf writeDouble(double pValue) {
        return this.source.writeDouble(pValue);
    }

    public ByteBuf writeBytes(ByteBuf pSource) {
        return this.source.writeBytes(pSource);
    }

    public ByteBuf writeBytes(ByteBuf pSource, int pLength) {
        return this.source.writeBytes(pSource, pLength);
    }

    public ByteBuf writeBytes(ByteBuf pSource, int pSourceIndex, int pLength) {
        return this.source.writeBytes(pSource, pSourceIndex, pLength);
    }

    public ByteBuf writeBytes(byte[] pSource) {
        return this.source.writeBytes(pSource);
    }

    public ByteBuf writeBytes(byte[] pSource, int pSourceIndex, int pLength) {
        return this.source.writeBytes(pSource, pSourceIndex, pLength);
    }

    public ByteBuf writeBytes(ByteBuffer pSource) {
        return this.source.writeBytes(pSource);
    }

    public int writeBytes(InputStream pIn, int pLength) throws IOException {
        return this.source.writeBytes(pIn, pLength);
    }

    public int writeBytes(ScatteringByteChannel pIn, int pLength) throws IOException {
        return this.source.writeBytes(pIn, pLength);
    }

    public int writeBytes(FileChannel pIn, long pPosition, int pLength) throws IOException {
        return this.source.writeBytes(pIn, pPosition, pLength);
    }

    public ByteBuf writeZero(int pLength) {
        return this.source.writeZero(pLength);
    }

    public int writeCharSequence(CharSequence pCharSequence, Charset pCharset) {
        return this.source.writeCharSequence(pCharSequence, pCharset);
    }

    public int indexOf(int pFromIndex, int pToIndex, byte pValue) {
        return this.source.indexOf(pFromIndex, pToIndex, pValue);
    }

    public int bytesBefore(byte pValue) {
        return this.source.bytesBefore(pValue);
    }

    public int bytesBefore(int pLength, byte pValue) {
        return this.source.bytesBefore(pLength, pValue);
    }

    public int bytesBefore(int pIndex, int pLength, byte pValue) {
        return this.source.bytesBefore(pIndex, pLength, pValue);
    }

    public int forEachByte(ByteProcessor pProcessor) {
        return this.source.forEachByte(pProcessor);
    }

    public int forEachByte(int pIndex, int pLength, ByteProcessor pProcessor) {
        return this.source.forEachByte(pIndex, pLength, pProcessor);
    }

    public int forEachByteDesc(ByteProcessor pProcessor) {
        return this.source.forEachByteDesc(pProcessor);
    }

    public int forEachByteDesc(int pIndex, int pLength, ByteProcessor pProcessor) {
        return this.source.forEachByteDesc(pIndex, pLength, pProcessor);
    }

    public ByteBuf copy() {
        return this.source.copy();
    }

    public ByteBuf copy(int pIndex, int pLength) {
        return this.source.copy(pIndex, pLength);
    }

    public ByteBuf slice() {
        return this.source.slice();
    }

    public ByteBuf retainedSlice() {
        return this.source.retainedSlice();
    }

    public ByteBuf slice(int pIndex, int pLength) {
        return this.source.slice(pIndex, pLength);
    }

    public ByteBuf retainedSlice(int pIndex, int pLength) {
        return this.source.retainedSlice(pIndex, pLength);
    }

    public ByteBuf duplicate() {
        return this.source.duplicate();
    }

    public ByteBuf retainedDuplicate() {
        return this.source.retainedDuplicate();
    }

    public int nioBufferCount() {
        return this.source.nioBufferCount();
    }

    public ByteBuffer nioBuffer() {
        return this.source.nioBuffer();
    }

    public ByteBuffer nioBuffer(int pIndex, int pLength) {
        return this.source.nioBuffer(pIndex, pLength);
    }

    public ByteBuffer internalNioBuffer(int pIndex, int pLength) {
        return this.source.internalNioBuffer(pIndex, pLength);
    }

    public ByteBuffer[] nioBuffers() {
        return this.source.nioBuffers();
    }

    public ByteBuffer[] nioBuffers(int pIndex, int pLength) {
        return this.source.nioBuffers(pIndex, pLength);
    }

    public boolean hasArray() {
        return this.source.hasArray();
    }

    public byte[] array() {
        return this.source.array();
    }

    public int arrayOffset() {
        return this.source.arrayOffset();
    }

    public boolean hasMemoryAddress() {
        return this.source.hasMemoryAddress();
    }

    public long memoryAddress() {
        return this.source.memoryAddress();
    }

    public String toString(Charset pCharset) {
        return this.source.toString(pCharset);
    }

    public String toString(int pIndex, int pLength, Charset pCharset) {
        return this.source.toString(pIndex, pLength, pCharset);
    }

    public int hashCode() {
        return this.source.hashCode();
    }

    public boolean equals(Object pOther) {
        return this.source.equals(pOther);
    }

    public int compareTo(ByteBuf p_130123_) {
        return this.source.compareTo(p_130123_);
    }

    public String toString() {
        return this.source.toString();
    }

    public ByteBuf retain(int pIncrement) {
        return this.source.retain(pIncrement);
    }

    public ByteBuf retain() {
        return this.source.retain();
    }

    public ByteBuf touch() {
        return this.source.touch();
    }

    public ByteBuf touch(Object pHint) {
        return this.source.touch(pHint);
    }

    public int refCnt() {
        return this.source.refCnt();
    }

    public boolean release() {
        return this.source.release();
    }

    public boolean release(int pDecrement) {
        return this.source.release(pDecrement);
    }

    @FunctionalInterface
    public interface Reader<T> extends Function<FriendlyByteBuf, T> {
        default FriendlyByteBuf.Reader<Optional<T>> asOptional() {
            return (p_236878_) -> {
                return p_236878_.readOptional(this);
            };
        }
    }

    @FunctionalInterface
    public interface Writer<T> extends BiConsumer<FriendlyByteBuf, T> {
        default FriendlyByteBuf.Writer<Optional<T>> asOptional() {
            return (p_236881_, p_236882_) -> {
                p_236881_.writeOptional(p_236882_, this);
            };
        }
    }
}
