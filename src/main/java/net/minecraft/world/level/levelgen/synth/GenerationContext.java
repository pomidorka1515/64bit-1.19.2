package net.minecraft.world.level.levelgen.synth;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Lexically scoped policy; asynchronous generation must explicitly capture it. */
public final class GenerationContext implements AutoCloseable {
    private static final ThreadLocal<GeneratorConfig> CURRENT = new ThreadLocal<>();
    private final GeneratorConfig previous;

    private GenerationContext(GeneratorConfig config) {
        this.previous = CURRENT.get();
        CURRENT.set(config);
    }

    public static GeneratorConfig current() {
        GeneratorConfig config = CURRENT.get();
        return config == null ? GeneratorConfig.DEFAULT : config;
    }

    public static GenerationContext open(GeneratorConfig config) { return new GenerationContext(config); }

    /** Standalone sampler setup; servers always supply a lexical world-owned snapshot. */
    static void setStandalone(GeneratorConfig config) { CURRENT.set(config); }

    public static <T> Supplier<T> capture(Supplier<T> task) {
        GeneratorConfig config = current();
        return () -> { try (GenerationContext ignored = open(config)) { return task.get(); } };
    }

    public static Executor capture(Executor executor) {
        GeneratorConfig config = current();
        return task -> executor.execute(() -> { try (GenerationContext ignored = open(config)) { task.run(); } });
    }

    @Override public void close() {
        if (this.previous == null) CURRENT.remove(); else CURRENT.set(this.previous);
    }
}
