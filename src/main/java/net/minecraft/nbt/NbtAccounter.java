package net.minecraft.nbt;

public class NbtAccounter {
    public static final NbtAccounter UNLIMITED = new NbtAccounter(0L) {
        public void accountBits(long p_128927_) {
        }
    };
    private final long quota;
    private long usage;

    public NbtAccounter(long pQuota) {
        this.quota = pQuota;
    }

    public void accountBits(long pBits) {
        this.usage += pBits / 8L;
        if (this.usage > this.quota) {
            throw new RuntimeException("Tried to read NBT tag that was too big; tried to allocate: " + this.usage + "bytes where max allowed: " + this.quota);
        }
    }
}
