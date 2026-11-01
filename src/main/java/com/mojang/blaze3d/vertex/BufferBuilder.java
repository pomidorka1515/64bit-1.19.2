package com.mojang.blaze3d.vertex;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Floats;
import com.mojang.blaze3d.platform.MemoryTracker;
import com.mojang.logging.LogUtils;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.mojang.math.Vector4f;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntConsumer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Vec3i;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.commons.lang3.mutable.MutableInt;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class BufferBuilder extends DefaultedVertexConsumer implements BufferVertexConsumer {
    private static final int GROWTH_SIZE = 2097152;
    private static final Logger LOGGER = LogUtils.getLogger();
    private ByteBuffer buffer;
    private int renderedBufferCount;
    private int renderedBufferPointer;
    private int nextElementByte;
    private int vertices;
    @Nullable
    private VertexFormatElement currentElement;
    private int elementIndex;
    private VertexFormat format;
    private VertexFormat.Mode mode;
    private boolean fastFormat;
    private boolean fullFormat;
    private boolean building;
    @Nullable
    private Vector3f[] sortingPoints;
    private float sortX = Float.NaN;
    private float sortY = Float.NaN;
    private float sortZ = Float.NaN;
    private boolean indexOnly;

    public BufferBuilder(int pCapacity) {
        this.buffer = MemoryTracker.create(pCapacity * 6);
    }

    private void ensureVertexCapacity() {
        this.ensureCapacity(this.format.getVertexSize());
    }

    private void ensureCapacity(int pIncreaseAmount) {
        if (this.nextElementByte + pIncreaseAmount > this.buffer.capacity()) {
            int i = this.buffer.capacity();
            int j = i + roundUp(pIncreaseAmount);
            LOGGER.debug("Needed to grow BufferBuilder buffer: Old size {} bytes, new size {} bytes.", i, j);
            ByteBuffer bytebuffer = MemoryTracker.resize(this.buffer, j);
            bytebuffer.rewind();
            this.buffer = bytebuffer;
        }
    }

    private static int roundUp(int pX) {
        int i = 2097152;
        if (pX == 0) {
            return i;
        } else {
            if (pX < 0) {
                i *= -1;
            }

            int j = pX % i;
            return j == 0 ? pX : pX + i - j;
        }
    }

    public void setQuadSortOrigin(float pSortX, float pSortY, float pSortZ) {
        if (this.mode == VertexFormat.Mode.QUADS) {
            if (this.sortX != pSortX || this.sortY != pSortY || this.sortZ != pSortZ) {
                this.sortX = pSortX;
                this.sortY = pSortY;
                this.sortZ = pSortZ;
                if (this.sortingPoints == null) {
                    this.sortingPoints = this.makeQuadSortingPoints();
                }
            }
        }
    }

    public BufferBuilder.SortState getSortState() {
        return new BufferBuilder.SortState(this.mode, this.vertices, this.sortingPoints, this.sortX, this.sortY, this.sortZ);
    }

    public void restoreSortState(BufferBuilder.SortState pSortState) {
        this.buffer.rewind();
        this.mode = pSortState.mode;
        this.vertices = pSortState.vertices;
        this.nextElementByte = this.renderedBufferPointer;
        this.sortingPoints = pSortState.sortingPoints;
        this.sortX = pSortState.sortX;
        this.sortY = pSortState.sortY;
        this.sortZ = pSortState.sortZ;
        this.indexOnly = true;
    }

    public void begin(VertexFormat.Mode pMode, VertexFormat pFormat) {
        if (this.building) {
            throw new IllegalStateException("Already building!");
        } else {
            this.building = true;
            this.mode = pMode;
            this.switchFormat(pFormat);
            this.currentElement = pFormat.getElements().get(0);
            this.elementIndex = 0;
            this.buffer.rewind();
        }
    }

    private void switchFormat(VertexFormat pFormat) {
        if (this.format != pFormat) {
            this.format = pFormat;
            boolean flag = pFormat == DefaultVertexFormat.NEW_ENTITY;
            boolean flag1 = pFormat == DefaultVertexFormat.BLOCK;
            this.fastFormat = flag || flag1;
            this.fullFormat = flag;
        }
    }

    private IntConsumer intConsumer(int p_231159_, VertexFormat.IndexType p_231160_) {
        MutableInt mutableint = new MutableInt(p_231159_);
        IntConsumer intconsumer;
        switch (p_231160_) {
            case BYTE:
                intconsumer = (p_231174_) -> {
                    this.buffer.put(mutableint.getAndIncrement(), (byte) p_231174_);
                };
                break;
            case SHORT:
                intconsumer = (p_231167_) -> {
                    this.buffer.putShort(mutableint.getAndAdd(2), (short) p_231167_);
                };
                break;
            case INT:
                intconsumer = (p_231163_) -> {
                    this.buffer.putInt(mutableint.getAndAdd(4), p_231163_);
                };
                break;
            default:
                throw new IncompatibleClassChangeError();
        }

        return intconsumer;
    }

    private Vector3f[] makeQuadSortingPoints() {
        FloatBuffer floatbuffer = this.buffer.asFloatBuffer();
        int i = this.renderedBufferPointer / 4;
        int j = this.format.getIntegerSize();
        int k = j * this.mode.primitiveStride;
        int l = this.vertices / this.mode.primitiveStride;
        Vector3f[] avector3f = new Vector3f[l];

        for (int i1 = 0; i1 < l; ++i1) {
            float f = floatbuffer.get(i + i1 * k + 0);
            float f1 = floatbuffer.get(i + i1 * k + 1);
            float f2 = floatbuffer.get(i + i1 * k + 2);
            float f3 = floatbuffer.get(i + i1 * k + j * 2 + 0);
            float f4 = floatbuffer.get(i + i1 * k + j * 2 + 1);
            float f5 = floatbuffer.get(i + i1 * k + j * 2 + 2);
            float f6 = (f + f3) / 2.0F;
            float f7 = (f1 + f4) / 2.0F;
            float f8 = (f2 + f5) / 2.0F;
            avector3f[i1] = new Vector3f(f6, f7, f8);
        }

        return avector3f;
    }

    private void putSortedQuadIndices(VertexFormat.IndexType pIndexType) {
        float[] afloat = new float[this.sortingPoints.length];
        int[] aint = new int[this.sortingPoints.length];

        for (int i = 0; i < this.sortingPoints.length; aint[i] = i++) {
            float f = this.sortingPoints[i].x() - this.sortX;
            float f1 = this.sortingPoints[i].y() - this.sortY;
            float f2 = this.sortingPoints[i].z() - this.sortZ;
            afloat[i] = f * f + f1 * f1 + f2 * f2;
        }

        IntArrays.mergeSort(aint, (p_166784_, p_166785_) -> {
            return Floats.compare(afloat[p_166785_], afloat[p_166784_]);
        });
        IntConsumer intconsumer = this.intConsumer(this.nextElementByte, pIndexType);

        for (int j : aint) {
            intconsumer.accept(j * this.mode.primitiveStride + 0);
            intconsumer.accept(j * this.mode.primitiveStride + 1);
            intconsumer.accept(j * this.mode.primitiveStride + 2);
            intconsumer.accept(j * this.mode.primitiveStride + 2);
            intconsumer.accept(j * this.mode.primitiveStride + 3);
            intconsumer.accept(j * this.mode.primitiveStride + 0);
        }
    }

    public boolean isCurrentBatchEmpty() {
        return this.vertices == 0;
    }

    @Nullable
    public BufferBuilder.RenderedBuffer endOrDiscardIfEmpty() {
        this.ensureDrawing();
        if (this.isCurrentBatchEmpty()) {
            this.reset();
            return null;
        } else {
            BufferBuilder.RenderedBuffer bufferbuilder$renderedbuffer = this.storeRenderedBuffer();
            this.reset();
            return bufferbuilder$renderedbuffer;
        }
    }

    public BufferBuilder.RenderedBuffer end() {
        this.ensureDrawing();
        BufferBuilder.RenderedBuffer bufferbuilder$renderedbuffer = this.storeRenderedBuffer();
        this.reset();
        return bufferbuilder$renderedbuffer;
    }

    private void ensureDrawing() {
        if (!this.building) {
            throw new IllegalStateException("Not building!");
        }
    }

    private BufferBuilder.RenderedBuffer storeRenderedBuffer() {
        int i = this.mode.indexCount(this.vertices);
        int j = !this.indexOnly ? this.vertices * this.format.getVertexSize() : 0;
        VertexFormat.IndexType vertexformat$indextype = VertexFormat.IndexType.least(i);
        boolean flag;
        int k;
        if (this.sortingPoints != null) {
            int l = Mth.roundToward(i * vertexformat$indextype.bytes, 4);
            this.ensureCapacity(l);
            this.putSortedQuadIndices(vertexformat$indextype);
            flag = false;
            this.nextElementByte += l;
            k = j + l;
        } else {
            flag = true;
            k = j;
        }

        int i1 = this.renderedBufferPointer;
        this.renderedBufferPointer += k;
        ++this.renderedBufferCount;
        BufferBuilder.DrawState bufferbuilder$drawstate = new BufferBuilder.DrawState(this.format, this.vertices, i, this.mode, vertexformat$indextype, this.indexOnly, flag);
        return new BufferBuilder.RenderedBuffer(i1, bufferbuilder$drawstate);
    }

    private void reset() {
        this.building = false;
        this.vertices = 0;
        this.currentElement = null;
        this.elementIndex = 0;
        this.sortingPoints = null;
        this.sortX = Float.NaN;
        this.sortY = Float.NaN;
        this.sortZ = Float.NaN;
        this.indexOnly = false;
    }

    public void putByte(int pIndex, byte pByteValue) {
        this.buffer.put(this.nextElementByte + pIndex, pByteValue);
    }

    public void putShort(int pIndex, short pShortValue) {
        this.buffer.putShort(this.nextElementByte + pIndex, pShortValue);
    }

    public void putFloat(int pIndex, float pFloatValue) {
        this.buffer.putFloat(this.nextElementByte + pIndex, pFloatValue);
    }

    public void endVertex() {
        if (this.elementIndex != 0) {
            throw new IllegalStateException("Not filled all elements of the vertex");
        } else {
            ++this.vertices;
            this.ensureVertexCapacity();
            if (this.mode == VertexFormat.Mode.LINES || this.mode == VertexFormat.Mode.LINE_STRIP) {
                int i = this.format.getVertexSize();
                this.buffer.put(this.nextElementByte, this.buffer, this.nextElementByte - i, i);
                this.nextElementByte += i;
                ++this.vertices;
                this.ensureVertexCapacity();
            }
        }
    }

    public void nextElement() {
        ImmutableList<VertexFormatElement> immutablelist = this.format.getElements();
        this.elementIndex = (this.elementIndex + 1) % immutablelist.size();
        this.nextElementByte += this.currentElement.getByteSize();
        VertexFormatElement vertexformatelement = immutablelist.get(this.elementIndex);
        this.currentElement = vertexformatelement;
        if (vertexformatelement.getUsage() == VertexFormatElement.Usage.PADDING) {
            this.nextElement();
        }

        if (this.defaultColorSet && this.currentElement.getUsage() == VertexFormatElement.Usage.COLOR) {
            BufferVertexConsumer.super.color(this.defaultR, this.defaultG, this.defaultB, this.defaultA);
        }
    }

    public VertexConsumer color(int pRed, int pGreen, int pBlue, int pAlpha) {
        if (this.defaultColorSet) {
            throw new IllegalStateException();
        } else {
            return BufferVertexConsumer.super.color(pRed, pGreen, pBlue, pAlpha);
        }
    }

    public void vertex(float pX, float pY, float pZ, float pRed, float pGreen, float pBlue, float pAlpha, float pTexU, float pTexV, int pOverlayUV, int pLightmapUV, float pNormalX, float pNormalY, float pNormalZ) {
        if (this.defaultColorSet) {
            throw new IllegalStateException();
        } else if (this.fastFormat) {
            this.putFloat(0, pX);
            this.putFloat(4, pY);
            this.putFloat(8, pZ);
            this.putByte(12, (byte) ((int) (pRed * 255.0F)));
            this.putByte(13, (byte) ((int) (pGreen * 255.0F)));
            this.putByte(14, (byte) ((int) (pBlue * 255.0F)));
            this.putByte(15, (byte) ((int) (pAlpha * 255.0F)));
            this.putFloat(16, pTexU);
            this.putFloat(20, pTexV);
            int i;
            if (this.fullFormat) {
                this.putShort(24, (short) (pOverlayUV & '\uffff'));
                this.putShort(26, (short) (pOverlayUV >> 16 & '\uffff'));
                i = 28;
            } else {
                i = 24;
            }

            this.putShort(i + 0, (short) (pLightmapUV & '\uffff'));
            this.putShort(i + 2, (short) (pLightmapUV >> 16 & '\uffff'));
            this.putByte(i + 4, BufferVertexConsumer.normalIntValue(pNormalX));
            this.putByte(i + 5, BufferVertexConsumer.normalIntValue(pNormalY));
            this.putByte(i + 6, BufferVertexConsumer.normalIntValue(pNormalZ));
            this.nextElementByte += i + 8;
            this.endVertex();
        } else {
            super.vertex(pX, pY, pZ, pRed, pGreen, pBlue, pAlpha, pTexU, pTexV, pOverlayUV, pLightmapUV, pNormalX, pNormalY, pNormalZ);
        }
    }

    @Override
    public void putBulkData(PoseStack.Pose pPoseEntry, BakedQuad pQuad, float pRed, float pGreen, float pBlue, int pCombinedLight, int pCombinedOverlay) {
        if (!this.canWriteBulkQuad(pQuad)) {
            super.putBulkData(pPoseEntry, pQuad, pRed, pGreen, pBlue, pCombinedLight, pCombinedOverlay);
            return;
        }

        this.putBulkQuad(pPoseEntry, pQuad, 1.0F, 1.0F, 1.0F, 1.0F, pRed, pGreen, pBlue,
            pCombinedLight, pCombinedLight, pCombinedLight, pCombinedLight, pCombinedOverlay, false);
    }

    @Override
    public void putBulkData(PoseStack.Pose pPoseEntry, BakedQuad pQuad, float[] pColorMuls, float pRed, float pGreen, float pBlue, int[] pCombinedLights, int pCombinedOverlay, boolean pMulColor) {
        if (!this.canWriteBulkQuad(pQuad)) {
            super.putBulkData(pPoseEntry, pQuad, pColorMuls, pRed, pGreen, pBlue, pCombinedLights, pCombinedOverlay, pMulColor);
            return;
        }

        // Snapshot all entries before accessing the pose or emitting any vertices,
        // just as the inherited implementation does with temporary arrays.
        this.putBulkQuad(pPoseEntry, pQuad, pColorMuls[0], pColorMuls[1], pColorMuls[2], pColorMuls[3], pRed, pGreen, pBlue,
            pCombinedLights[0], pCombinedLights[1], pCombinedLights[2], pCombinedLights[3], pCombinedOverlay, pMulColor);
    }

    private boolean canWriteBulkQuad(@Nullable BakedQuad pQuad) {
        // Preserve virtual callbacks, malformed-input behavior and unusual layouts
        // on the reference path. No new bulk-writer contract is exposed.
        return this.getClass() == BufferBuilder.class && this.fastFormat && !this.defaultColorSet
            && pQuad != null && pQuad.getClass() == BakedQuad.class
            && pQuad.getVertices() != null && pQuad.getVertices().length == 32;
    }

    private void putBulkQuad(PoseStack.Pose pPoseEntry, BakedQuad pQuad, float pBrightness0, float pBrightness1, float pBrightness2, float pBrightness3,
                             float pRed, float pGreen, float pBlue, int pLight0, int pLight1, int pLight2, int pLight3, int pOverlay, boolean pMulColor) {
        int[] data = pQuad.getVertices();
        Vec3i face = pQuad.getDirection().getNormal();
        Vector3f normal = new Vector3f((float) face.getX().toDoubleRounded(), (float) face.getY(), (float) face.getZ().toDoubleRounded());
        Matrix4f matrix = pPoseEntry.pose();
        normal.transform(pPoseEntry.normal());
        Vector4f position = new Vector4f();

        for (int vertex = 0; vertex < 4; ++vertex) {
            int offset = vertex * 8;
            float brightness;
            int light;
            switch (vertex) {
                case 0 -> { brightness = pBrightness0; light = pLight0; }
                case 1 -> { brightness = pBrightness1; light = pLight1; }
                case 2 -> { brightness = pBrightness2; light = pLight2; }
                default -> { brightness = pBrightness3; light = pLight3; }
            }

            float red;
            float green;
            float blue;
            if (pMulColor) {
                // Match bytes 12..14 of the native-order stack buffer, including
                // on big-endian hosts. Keep division and multiplication order.
                int color = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? data[offset + 3] : Integer.reverseBytes(data[offset + 3]);
                float sourceRed = (float) (color & 255) / 255.0F;
                float sourceGreen = (float) (color >>> 8 & 255) / 255.0F;
                float sourceBlue = (float) (color >>> 16 & 255) / 255.0F;
                red = sourceRed * brightness * pRed;
                green = sourceGreen * brightness * pGreen;
                blue = sourceBlue * brightness * pBlue;
            } else {
                red = brightness * pRed;
                green = brightness * pGreen;
                blue = brightness * pBlue;
            }

            position.set(Float.intBitsToFloat(data[offset]), Float.intBitsToFloat(data[offset + 1]), Float.intBitsToFloat(data[offset + 2]), 1.0F);
            position.transform(matrix);
            this.vertex(position.x(), position.y(), position.z(), red, green, blue, 1.0F,
                Float.intBitsToFloat(data[offset + 4]), Float.intBitsToFloat(data[offset + 5]), pOverlay, light, normal.x(), normal.y(), normal.z());
        }
    }

    void releaseRenderedBuffer() {
        if (this.renderedBufferCount > 0 && --this.renderedBufferCount == 0) {
            this.clear();
        }
    }

    public void clear() {
        if (this.renderedBufferCount > 0) {
            LOGGER.warn("Clearing BufferBuilder with unused batches");
        }

        this.discard();
    }

    public void discard() {
        this.renderedBufferCount = 0;
        this.renderedBufferPointer = 0;
        this.nextElementByte = 0;
    }

    public VertexFormatElement currentElement() {
        if (this.currentElement == null) {
            throw new IllegalStateException("BufferBuilder not started");
        } else {
            return this.currentElement;
        }
    }

    public boolean building() {
        return this.building;
    }

    ByteBuffer bufferSlice(int p_231170_, int p_231171_) {
        return MemoryUtil.memSlice(this.buffer, p_231170_, p_231171_ - p_231170_);
    }

    @OnlyIn(Dist.CLIENT)
    public static record DrawState(VertexFormat format, int vertexCount, int indexCount, VertexFormat.Mode mode, VertexFormat.IndexType indexType, boolean indexOnly, boolean sequentialIndex) {
        public int vertexBufferSize() {
            return this.vertexCount * this.format.getVertexSize();
        }

        public int vertexBufferStart() {
            return 0;
        }

        public int vertexBufferEnd() {
            return this.vertexBufferSize();
        }

        public int indexBufferStart() {
            return this.indexOnly ? 0 : this.vertexBufferEnd();
        }

        public int indexBufferEnd() {
            return this.indexBufferStart() + this.indexBufferSize();
        }

        private int indexBufferSize() {
            return this.sequentialIndex ? 0 : this.indexCount * this.indexType.bytes;
        }

        public int bufferSize() {
            return this.indexBufferEnd();
        }
    }

    @OnlyIn(Dist.CLIENT)
    public class RenderedBuffer {
        private final int pointer;
        private final BufferBuilder.DrawState drawState;
        private boolean released;

        RenderedBuffer(int p_231194_, BufferBuilder.DrawState p_231195_) {
            this.pointer = p_231194_;
            this.drawState = p_231195_;
        }

        public ByteBuffer vertexBuffer() {
            int i = this.pointer + this.drawState.vertexBufferStart();
            int j = this.pointer + this.drawState.vertexBufferEnd();
            return BufferBuilder.this.bufferSlice(i, j);
        }

        public ByteBuffer indexBuffer() {
            int i = this.pointer + this.drawState.indexBufferStart();
            int j = this.pointer + this.drawState.indexBufferEnd();
            return BufferBuilder.this.bufferSlice(i, j);
        }

        public BufferBuilder.DrawState drawState() {
            return this.drawState;
        }

        public boolean isEmpty() {
            return this.drawState.vertexCount == 0;
        }

        public void release() {
            if (this.released) {
                throw new IllegalStateException("Buffer has already been released!");
            } else {
                BufferBuilder.this.releaseRenderedBuffer();
                this.released = true;
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class SortState {
        final VertexFormat.Mode mode;
        final int vertices;
        @Nullable
        final Vector3f[] sortingPoints;
        final float sortX;
        final float sortY;
        final float sortZ;

        SortState(VertexFormat.Mode pMode, int pVertices, @Nullable Vector3f[] pSortingPoints, float pSortX, float pSortY, float pSortZ) {
            this.mode = pMode;
            this.vertices = pVertices;
            this.sortingPoints = pSortingPoints;
            this.sortX = pSortX;
            this.sortY = pSortY;
            this.sortZ = pSortZ;
        }
    }
}
