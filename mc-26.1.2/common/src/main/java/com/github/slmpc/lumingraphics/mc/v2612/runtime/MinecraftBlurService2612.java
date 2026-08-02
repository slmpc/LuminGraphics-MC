package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import com.github.slmpc.lumingraphics.render.frame.RenderExecution;
import com.github.slmpc.lumingraphics.render.resource.RenderResources;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.github.slmpc.lumingraphics.render.shader.BlurShader;
import com.github.slmpc.lumingraphics.render.shader.FullscreenEffect;
import com.github.slmpc.prismrhi.resource.RhiFilter;
import com.github.slmpc.prismrhi.resource.RhiImage;
import com.github.slmpc.prismrhi.resource.RhiImageView;
import com.github.slmpc.prismrhi.resource.RhiSampler;
import com.github.slmpc.prismrhi.resource.RhiSamplerAddressMode;
import com.github.slmpc.prismrhi.resource.RhiSamplerCreateInfo;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;

/** Minecraft 主目标 HUD blur 的 GPU 生命周期所有者。 */
final class MinecraftBlurService2612 implements AutoCloseable {
    private final Minecraft client;
    private final MinecraftGraphicsRuntime2612 graphics;
    private final MinecraftBlurResources2612 resources;
    private final RhiSampler sampler;
    private final Map<GlTextureView, BorrowedView> views = new IdentityHashMap<>();
    private final List<BorrowedView> retiredViews = new ArrayList<>();
    private final List<RetiredEffect> effects = new ArrayList<>();
    private TextureTarget feedbackInput;
    private boolean closed;

    MinecraftBlurService2612(Minecraft client, MinecraftGraphicsRuntime2612 graphics,
                             RenderResources renderResources) {
        this.client = Objects.requireNonNull(client, "client");
        this.graphics = Objects.requireNonNull(graphics, "graphics");
        resources = new MinecraftBlurResources2612(renderResources);
        sampler = resources.device().createSampler(new RhiSamplerCreateInfo(
                RhiFilter.LINEAR, RhiFilter.LINEAR,
                RhiSamplerAddressMode.CLAMP_TO_EDGE, RhiSamplerAddressMode.CLAMP_TO_EDGE,
                RhiSamplerAddressMode.CLAMP_TO_EDGE, 0.0f));
    }

    void apply(RenderExecution base, MinecraftBlurRegion2612 region) {
        requireOpen();
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(region, "region");
        RenderTarget target = client.getMainRenderTarget();
        validateTarget(target);
        releaseCompleted(base.completedFrameId());
        RenderTarget sampledInput = copyFeedbackInput(target, base.frameId());
        BorrowedView sampled = requireView(sampledInput, base.frameId());
        BorrowedView output = requireView(target, base.frameId());
        Render2DTexture texture = new Render2DTexture.Resource(
                "lumin-graphics-mc:blur-input/" + Long.toUnsignedString(base.frameId()) + '/' + effects.size());
        RenderExecution execution = new RenderExecution(base.context(), resources);
        BlurShader effect = new BlurShader(resources, 256);
        effects.add(new RetiredEffect(base.frameId(), effect));
        var binding = resources.bind(texture, sampled.view, sampler, output.view);
        try (binding) {
            effect.apply(execution, texture,
                    region.uniforms(target.width, target.height, client.getWindow().getGuiScale()));
        }
    }

    private static void validateTarget(RenderTarget target) {
        if (target.width <= 0 || target.height <= 0) {
            throw new IllegalArgumentException("Blur target dimensions must be positive");
        }
        if (target.getColorTexture() == null || target.getColorTextureView() == null) {
            throw new IllegalStateException("Blur target color texture is unavailable");
        }
    }

    private RenderTarget copyFeedbackInput(RenderTarget source, long frameId) {
        if (feedbackInput == null) {
            feedbackInput = new TextureTarget("LuminGraphics MC Blur Input", source.width, source.height, false);
        } else if (feedbackInput.width != source.width || feedbackInput.height != source.height) {
            retireView(feedbackInput, frameId);
            feedbackInput.resize(source.width, source.height);
        }
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                source.getColorTexture(), feedbackInput.getColorTexture(),
                0, 0, 0, 0, 0, source.width, source.height);
        return feedbackInput;
    }

    private BorrowedView requireView(RenderTarget target, long frameId) {
        if (!(target.getColorTextureView() instanceof GlTextureView blazeView)) {
            throw new IllegalStateException("Blur target is not OpenGL-backed");
        }
        BorrowedView existing = views.get(blazeView);
        if (existing != null) {
            existing.lastUsedFrameId = frameId;
            return existing;
        }
        RhiImageView view = graphics.blazeBridge().fromBlazeTextureView(blazeView).orElseThrow();
        BorrowedView borrowed = new BorrowedView(view);
        borrowed.lastUsedFrameId = frameId;
        views.put(blazeView, borrowed);
        return borrowed;
    }

    private void retireView(RenderTarget target, long frameId) {
        if (!(target.getColorTextureView() instanceof GlTextureView blazeView)) return;
        BorrowedView borrowed = views.remove(blazeView);
        if (borrowed != null) {
            borrowed.lastUsedFrameId = frameId;
            retiredViews.add(borrowed);
        }
    }

    private void releaseCompleted(long completedFrameId) {
        for (int index = effects.size() - 1; index >= 0; index--) {
            if (effects.get(index).frameId <= completedFrameId) effects.remove(index).close();
        }
        var iterator = views.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().isClosed() && entry.getValue().lastUsedFrameId <= completedFrameId) {
                iterator.remove();
                entry.getValue().close();
            }
        }
        for (int index = retiredViews.size() - 1; index >= 0; index--) {
            if (retiredViews.get(index).lastUsedFrameId <= completedFrameId) {
                retiredViews.remove(index).close();
            }
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = null;
        for (int index = effects.size() - 1; index >= 0; index--) {
            try { effects.get(index).close(); }
            catch (RuntimeException exception) { failure = GraphicsResourceCloser2612.merge(failure, exception); }
        }
        effects.clear();
        for (BorrowedView view : views.values()) {
            try { view.close(); }
            catch (RuntimeException exception) { failure = GraphicsResourceCloser2612.merge(failure, exception); }
        }
        views.clear();
        for (BorrowedView view : retiredViews) {
            try { view.close(); }
            catch (RuntimeException exception) { failure = GraphicsResourceCloser2612.merge(failure, exception); }
        }
        retiredViews.clear();
        failure = GraphicsResourceCloser2612.merge(failure,
                GraphicsResourceCloser2612.closeReverse(sampler, resources));
        if (feedbackInput != null) {
            try { feedbackInput.destroyBuffers(); }
            catch (RuntimeException exception) { failure = GraphicsResourceCloser2612.merge(failure, exception); }
            feedbackInput = null;
        }
        if (failure != null) throw failure;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Minecraft blur service is closed");
        graphics.luminContext().requireRenderThread();
    }

    private static final class BorrowedView implements AutoCloseable {
        private final RhiImageView view;
        private final RhiImage image;
        private long lastUsedFrameId;
        private boolean closed;

        private BorrowedView(RhiImageView view) {
            this.view = view;
            image = view.image();
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            RuntimeException failure = GraphicsResourceCloser2612.closeReverse(view, image);
            if (failure != null) throw failure;
        }
    }

    private record RetiredEffect(long frameId, FullscreenEffect effect) implements AutoCloseable {
        @Override public void close() { effect.close(); }
    }
}
