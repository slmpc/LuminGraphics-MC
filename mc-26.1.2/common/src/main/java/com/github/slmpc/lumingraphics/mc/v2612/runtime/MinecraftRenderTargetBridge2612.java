package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import com.github.slmpc.lumingraphics.core.target.RenderTarget;
import com.github.slmpc.lumingraphics.mc.v2612.bridge.Blaze3DBridge2612;
import com.github.slmpc.prismrhi.context.PRhiContextIdentity;
import com.github.slmpc.prismrhi.resource.PRhiImageView;
import com.mojang.blaze3d.opengl.GlTextureView;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

final class MinecraftRenderTargetBridge2612 implements AutoCloseable {
    private final Blaze3DBridge2612 bridge;
    private final PRhiContextIdentity contextIdentity;
    private final Supplier<com.mojang.blaze3d.pipeline.RenderTarget> targetSupplier;
    private final BorrowedTargetCache2612<com.mojang.blaze3d.pipeline.RenderTarget, BorrowedTarget> cache;

    MinecraftRenderTargetBridge2612(Blaze3DBridge2612 bridge, PRhiContextIdentity contextIdentity,
                                    Supplier<com.mojang.blaze3d.pipeline.RenderTarget> targetSupplier) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        this.contextIdentity = Objects.requireNonNull(contextIdentity, "contextIdentity");
        this.targetSupplier = Objects.requireNonNull(targetSupplier, "targetSupplier");
        cache = new BorrowedTargetCache2612<>(this::borrow);
    }

    FrameCoordinator2612.TargetLease acquire() {
        var target = targetSupplier.get();
        if (target == null) throw new IllegalStateException("Minecraft main render target is unavailable");
        if (!(target.getColorTextureView() instanceof GlTextureView color)) {
            throw new IllegalStateException("Minecraft main render target color view is unavailable or not OpenGL");
        }
        return cache.acquire(new BorrowedTargetCache2612.Source<>(
                new TargetIdentity(color), target.width, target.height, target));
    }

    void invalidate(String reason) { cache.invalidate(reason); }
    @Override public void close() { cache.close(); }

    private BorrowedTarget borrow(com.mojang.blaze3d.pipeline.RenderTarget target) {
        PRhiImageView color = null;
        try {
            color = bridge.fromBlazeTextureView((GlTextureView) target.getColorTextureView()).orElseThrow();
            return new BorrowedTarget(new RenderTarget(color, Optional.empty(),
                    target.width, target.height, contextIdentity), color);
        } catch (RuntimeException failure) {
            RuntimeException cleanup = closeViews(color);
            if (cleanup != null) failure.addSuppressed(cleanup);
            throw new IllegalStateException("Failed to borrow Minecraft main render target", failure);
        }
    }

    private static RuntimeException closeViews(PRhiImageView color) {
        return GraphicsResourceCloser2612.closeReverse(
                color, color == null ? null : color.image());
    }

    private record TargetIdentity(GlTextureView color) {
        @Override public boolean equals(Object other) {
            return other instanceof TargetIdentity identity && color == identity.color;
        }
        @Override public int hashCode() {
            return System.identityHashCode(color);
        }
    }

    static final class BorrowedTarget implements AutoCloseable {
        private final RenderTarget target;
        private final PRhiImageView color;
        private boolean closed;

        BorrowedTarget(RenderTarget target, PRhiImageView color) {
            this.target = target;
            this.color = color;
        }
        RenderTarget target() {
            if (closed) throw new IllegalStateException("Borrowed Minecraft render target wrapper is closed");
            return target;
        }
        @Override public void close() {
            if (closed) return;
            closed = true;
            RuntimeException failure = closeViews(color);
            if (failure != null) throw failure;
        }
    }
}
