package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import com.github.slmpc.lumingraphics.render.frame.RenderExecution;
import com.github.slmpc.lumingraphics.render.resource.RenderResources;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.github.slmpc.lumingraphics.render.shader.FullscreenEffectBinding;
import com.github.slmpc.lumingraphics.render.shader.FullscreenEffectPass;
import com.github.slmpc.lumingraphics.render.shader.FullscreenEffectRequest;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSet;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSetAllocateInfo;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSetLayout;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSetLayoutCreateInfo;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorStage;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorType;
import com.github.slmpc.prismrhi.device.RhiDevice;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipeline;
import com.github.slmpc.prismrhi.rendering.RhiRect2D;
import com.github.slmpc.prismrhi.rendering.RhiRenderingAttachment;
import com.github.slmpc.prismrhi.rendering.RhiRenderingInfo;
import com.github.slmpc.prismrhi.resource.RhiBuffer;
import com.github.slmpc.prismrhi.resource.RhiBufferCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiBufferUsage;
import com.github.slmpc.prismrhi.resource.RhiImageView;
import com.github.slmpc.prismrhi.resource.RhiMemoryUsage;
import com.github.slmpc.prismrhi.resource.RhiSampler;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 为 Minecraft HUD blur 绑定反馈纹理和逐帧 uniform。 */
final class MinecraftBlurResources2612 implements RenderResources, AutoCloseable {
    private static final int MIN_UNIFORM_BYTES = 16;

    private final RenderResources delegate;
    private final RhiDescriptorSetLayout effectLayout;
    private final List<RetiredBinding> retired = new ArrayList<>();
    private BindingScope active;
    private boolean closed;

    MinecraftBlurResources2612(RenderResources delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        effectLayout = delegate.device().createDescriptorSetLayout(RhiDescriptorSetLayoutCreateInfo.builder()
                .binding(0, RhiDescriptorType.COMBINED_IMAGE_SAMPLER, 1, RhiDescriptorStage.FRAGMENT)
                .binding(1, RhiDescriptorType.UNIFORM_BUFFER, 1, RhiDescriptorStage.FRAGMENT)
                .binding(2, RhiDescriptorType.UNIFORM_BUFFER, 1, RhiDescriptorStage.FRAGMENT)
                .build());
    }

    BindingScope bind(Render2DTexture input, RhiImageView inputView, RhiSampler sampler, RhiImageView targetView) {
        requireOpen();
        if (active != null) throw new IllegalStateException("A blur binding is already active");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(inputView, "inputView");
        Objects.requireNonNull(sampler, "sampler");
        Objects.requireNonNull(targetView, "targetView");
        if (inputView == targetView) throw new IllegalArgumentException("Blur cannot sample from its render target");
        active = new BindingScope(input, inputView, sampler, targetView);
        return active;
    }

    @Override public RhiDevice device() { requireOpen(); return delegate.device(); }
    @Override public RhiGraphicsPipeline requirePipeline(String id) { requireOpen(); return delegate.requirePipeline(id); }
    @Override public RhiDescriptorSet requireFrameDescriptor() {
        requireOpen();
        return delegate.requireFrameDescriptor();
    }
    @Override public RhiDescriptorSet requireTextureDescriptor(Render2DTexture texture) {
        requireOpen();
        return delegate.requireTextureDescriptor(texture);
    }
    @Override public RhiDescriptorSet requireSegmentedShadowDescriptor(Render2DCommand.SegmentedShadow shadow) {
        requireOpen();
        return delegate.requireSegmentedShadowDescriptor(shadow);
    }

    @Override public FullscreenEffectBinding requireFullscreenEffectBinding(
            FullscreenEffectRequest request, RenderExecution execution) {
        requireOpen();
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(execution, "execution");
        if (execution.resources() != this) {
            throw new IllegalArgumentException("Blur execution belongs to another resource service");
        }
        BindingScope scope = active;
        if (scope == null || scope.closed) throw new IllegalStateException("No blur input is bound");
        if (!scope.input.equals(request.input())) {
            throw new IllegalArgumentException("Blur requested an input outside the active binding");
        }
        var inputExtent = scope.inputView.image().extent();
        var targetExtent = scope.targetView.image().extent();
        if (inputExtent.width() != execution.width() || inputExtent.height() != execution.height()
                || targetExtent.width() != execution.width() || targetExtent.height() != execution.height()) {
            throw new IllegalStateException("Blur binding is stale after target resize");
        }

        releaseCompleted(execution.completedFrameId());
        ByteBuffer payload = request.uniforms();
        RhiBuffer first = createUniformBuffer(payload);
        RhiBuffer second = null;
        RhiDescriptorSet descriptor = null;
        try {
            second = createUniformBuffer(ByteBuffer.allocate(0));
            descriptor = device().allocateDescriptorSet(RhiDescriptorSetAllocateInfo.of(effectLayout));
            RhiBuffer finalSecond = second;
            descriptor.update(writer -> writer
                    .combinedImageSampler(0, 0, scope.inputView, scope.sampler)
                    .uniformBuffer(1, first)
                    .uniformBuffer(2, finalSecond));
            retired.add(new RetiredBinding(execution.frameId(), descriptor, first, second));
            RhiRenderingInfo rendering = RhiRenderingInfo.builder(RhiRect2D.of(execution.width(), execution.height()))
                    .color(RhiRenderingAttachment.color(scope.targetView)).build();
            return new FullscreenEffectBinding(descriptor, FullscreenEffectPass.rendering(rendering));
        } catch (RuntimeException failure) {
            closeAfterFailure(descriptor, second, first, failure);
            throw failure;
        }
    }

    private RhiBuffer createUniformBuffer(ByteBuffer payload) {
        ByteBuffer content = payload.slice();
        RhiBuffer buffer = device().createBuffer(RhiBufferCreateInfo.builder(
                        Math.max(MIN_UNIFORM_BYTES, content.remaining()))
                .usage(RhiBufferUsage.UNIFORM_BUFFER)
                .memoryUsage(RhiMemoryUsage.CPU_TO_GPU)
                .build());
        if (content.hasRemaining()) buffer.write(content);
        return buffer;
    }

    private void releaseCompleted(long completedFrameId) {
        for (int index = retired.size() - 1; index >= 0; index--) {
            if (retired.get(index).frameId <= completedFrameId) retired.remove(index).close();
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (active != null) active.closed = true;
        active = null;
        RuntimeException failure = null;
        for (int index = retired.size() - 1; index >= 0; index--) {
            try { retired.get(index).close(); }
            catch (RuntimeException exception) { failure = GraphicsResourceCloser2612.merge(failure, exception); }
        }
        retired.clear();
        try { effectLayout.close(); }
        catch (RuntimeException exception) { failure = GraphicsResourceCloser2612.merge(failure, exception); }
        if (failure != null) throw failure;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Blur resources are closed");
    }

    private static void closeAfterFailure(AutoCloseable descriptor, AutoCloseable second,
                                          AutoCloseable first, RuntimeException failure) {
        for (AutoCloseable resource : new AutoCloseable[] { descriptor, second, first }) {
            if (resource == null) continue;
            try { resource.close(); }
            catch (Exception cleanupFailure) { failure.addSuppressed(cleanupFailure); }
        }
    }

    final class BindingScope implements AutoCloseable {
        private final Render2DTexture input;
        private final RhiImageView inputView;
        private final RhiSampler sampler;
        private final RhiImageView targetView;
        private boolean closed;

        private BindingScope(Render2DTexture input, RhiImageView inputView,
                             RhiSampler sampler, RhiImageView targetView) {
            this.input = input;
            this.inputView = inputView;
            this.sampler = sampler;
            this.targetView = targetView;
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            if (active == this) active = null;
        }
    }

    private record RetiredBinding(long frameId, RhiDescriptorSet descriptor,
                                  RhiBuffer first, RhiBuffer second) implements AutoCloseable {
        @Override public void close() {
            RuntimeException failure = GraphicsResourceCloser2612.closeReverse(descriptor, second, first);
            if (failure != null) throw failure;
        }
    }
}
