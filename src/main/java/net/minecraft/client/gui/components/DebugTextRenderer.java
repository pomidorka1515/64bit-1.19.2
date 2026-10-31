package net.minecraft.client.gui.components;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Retains only the lines used by the current F3 frame, not an unbounded text history. */
final class DebugTextRenderer implements AutoCloseable {
    private static final int COLOR = 14737632;
    private final Font font;
    private final BufferBuilder builder = new BufferBuilder(4096);
    private final Map<Object, Text> texts = new HashMap<>();
    private final Map<WrapKey, Wrapped> wrapped = new HashMap<>();
    private long frame;
    private long resourceVersion = Long.MIN_VALUE;
    private Language language;

    DebugTextRenderer(Font font) {
        this.font = font;
    }

    void beginFrame() {
        if (this.resourceVersion != this.font.resourceVersion() || this.language != Language.getInstance()) {
            this.close();
            this.resourceVersion = this.font.resourceVersion();
            this.language = Language.getInstance();
        }
        ++this.frame;
    }

    void endFrame() {
        this.texts.values().removeIf(text -> {
            if (text.frame == this.frame) return false;
            text.close();
            return true;
        });
        this.wrapped.values().removeIf(value -> value.frame != this.frame);
    }

    List<FormattedCharSequence> wrap(String line, int width) {
        WrapKey key = new WrapKey(line, width);
        Wrapped value = this.wrapped.get(key);
        if (value == null) {
            value = new Wrapped(line.isEmpty() ? List.of(FormattedCharSequence.EMPTY)
                    : this.font.split(Component.literal(line), width));
            this.wrapped.put(key, value);
        }
        value.frame = this.frame;
        return value.lines;
    }

    int width(Object line) {
        return this.text(line).width;
    }

    void draw(PoseStack pose, Object line, int x, int y) {
        Text text = this.text(line);
        // Obfuscated styles must choose fresh glyphs every frame.
        if (text.dynamic) {
            if (line instanceof String string) this.font.draw(pose, string, x, y, COLOR);
            else this.font.draw(pose, (FormattedCharSequence) line, x, y, COLOR);
            return;
        }
        if (text.batches == null) this.build(line, text);
        pose.pushPose();
        pose.translate(x, y, 0);
        Matrix4f modelView = RenderSystem.getModelViewMatrix().copy();
        modelView.multiply(pose.last().pose());
        try {
            for (Batch batch : text.batches) {
                batch.type.setupRenderState();
                try {
                    batch.buffer.bind();
                    batch.buffer.drawWithShader(modelView, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
                } finally {
                    VertexBuffer.unbind();
                    batch.type.clearRenderState();
                }
            }
        } finally {
            pose.popPose();
        }
    }

    private Text text(Object line) {
        Text text = this.texts.get(line);
        if (text == null) {
            int width;
            boolean dynamic;
            if (line instanceof String string) {
                width = this.font.width(string);
                dynamic = string.contains("\u00a7k") || string.contains("\u00a7K");
            } else {
                FormattedCharSequence sequence = (FormattedCharSequence) line;
                width = this.font.width(sequence);
                dynamic = !sequence.accept((index, style, codePoint) -> !style.isObfuscated());
            }
            text = new Text(width, dynamic);
            this.texts.put(line, text);
        }
        text.frame = this.frame;
        return text;
    }

    private void build(Object line, Text text) {
        text.batches = new ArrayList<>();
        MeshBuilder meshes = new MeshBuilder(text.batches);
        Matrix4f identity = new Matrix4f();
        identity.setIdentity();
        if (line instanceof String string) {
            this.font.drawInBatch(string, 0, 0, COLOR, false, identity, meshes, false, 0, 15728880);
        } else {
            this.font.drawInBatch((FormattedCharSequence) line, 0, 0, COLOR, false, identity, meshes, false, 0, 15728880);
        }
        meshes.flush();
    }

    @Override
    public void close() {
        this.texts.values().forEach(Text::close);
        this.texts.clear();
        this.wrapped.clear();
    }

    private final class MeshBuilder implements MultiBufferSource {
        private final List<Batch> batches;
        private RenderType type;

        MeshBuilder(List<Batch> batches) {
            this.batches = batches;
        }

        public VertexConsumer getBuffer(RenderType type) {
            if (this.type != type) {
                this.flush();
                this.type = type;
                builder.begin(type.mode(), type.format());
            }
            return builder;
        }

        void flush() {
            if (this.type == null) return;
            VertexBuffer buffer = new VertexBuffer();
            this.batches.add(new Batch(this.type, buffer));
            try {
                buffer.bind();
                // Font render types use sorted translucent quads, including text decorations.
                builder.setQuadSortOrigin(0, 0, 0);
                buffer.upload(builder.end());
            } finally {
                VertexBuffer.unbind();
            }
            this.type = null;
        }
    }

    private record Batch(RenderType type, VertexBuffer buffer) {}
    private record WrapKey(String line, int width) {}

    private static final class Wrapped {
        final List<FormattedCharSequence> lines;
        long frame;

        Wrapped(List<FormattedCharSequence> lines) {
            this.lines = lines;
        }
    }

    private static final class Text implements AutoCloseable {
        final int width;
        final boolean dynamic;
        long frame;
        List<Batch> batches;

        Text(int width, boolean dynamic) {
            this.width = width;
            this.dynamic = dynamic;
        }

        public void close() {
            if (this.batches != null) this.batches.forEach(batch -> batch.buffer.close());
        }
    }
}
