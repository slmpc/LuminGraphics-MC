package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

/** Minecraft 26.1.2 原生 GUI extraction 状态的独立提交桥。 */
public final class MinecraftGuiExtractionBridge2612 implements AutoCloseable {
    private static final ThreadLocal<Boolean> SUBMITTING = ThreadLocal.withInitial(() -> false);

    private static final ThreadLocal<Boolean> nativeSubmissionActive = ThreadLocal.withInitial(() -> false);

    /** 原版 GUI renderer 构造所需、但不由本桥持有的 Minecraft 协作对象。 */
    public record NativeResources(MultiBufferSource.BufferSource bufferSource,
                                  SubmitNodeCollector submitNodeCollector,
                                  FeatureRenderDispatcher featureRenderDispatcher) {
        public NativeResources {
            Objects.requireNonNull(bufferSource, "bufferSource");
            Objects.requireNonNull(submitNodeCollector, "submitNodeCollector");
            Objects.requireNonNull(featureRenderDispatcher, "featureRenderDispatcher");
        }
    }

    private final GuiRenderState renderState;
    private final Consumer<GpuBufferSlice> render;
    private final Runnable endFrame;
    private final Runnable closeResources;
    private final Runnable accessGuard;
    private boolean closed;

    public MinecraftGuiExtractionBridge2612(NativeResources resources) {
        Objects.requireNonNull(resources, "resources");
        RenderSystem.assertOnRenderThread();
        renderState = new GuiRenderState();
        GuiRenderer renderer = new GuiRenderer(renderState, resources.bufferSource(),
                resources.submitNodeCollector(), resources.featureRenderDispatcher(), List.of());
        render = renderer::render;
        endFrame = renderer::endFrame;
        closeResources = renderer::close;
        accessGuard = RenderSystem::assertOnRenderThread;
    }

    MinecraftGuiExtractionBridge2612(GuiRenderState renderState, Consumer<GpuBufferSlice> render,
                                     Runnable endFrame, Runnable closeResources, Runnable accessGuard) {
        this.renderState = Objects.requireNonNull(renderState, "renderState");
        this.render = Objects.requireNonNull(render, "render");
        this.endFrame = Objects.requireNonNull(endFrame, "endFrame");
        this.closeResources = Objects.requireNonNull(closeResources, "closeResources");
        this.accessGuard = Objects.requireNonNull(accessGuard, "accessGuard");
    }

    /** 为当前独立状态创建原版 GUI 提取器。 */
    public GuiGraphicsExtractor extractor(Minecraft client, int mouseX, int mouseY) {
        requireOpen();
        return new GuiGraphicsExtractor(Objects.requireNonNull(client, "client"), renderState, mouseX, mouseY);
    }

    /** 提交并结束当前 extraction 帧；原版 renderer 会重置状态供下一帧复用。 */
    public void submit(GpuBufferSlice fogBuffer) {
        requireOpen();
        fogBuffer = Objects.requireNonNull(fogBuffer, "fogBuffer");
        if (SUBMITTING.get()) return;
        SUBMITTING.set(true);
        nativeSubmissionActive.set(true);
        try {
            render.accept(fogBuffer);
            endFrame.run();
        } finally {
            nativeSubmissionActive.remove();
            SUBMITTING.remove();
        }
    }

    /** 原版独立 GUI renderer 正在提交时，外部 draw 注入必须跳过以避免回调自身。 */
    public static boolean isNativeSubmissionActive() {
        return nativeSubmissionActive.get();
    }

    @Override
    public void close() {
        if (closed) return;
        accessGuard.run();
        closed = true;
        closeResources.run();
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Minecraft GUI extraction bridge is closed");
        accessGuard.run();
    }
}
