package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import com.github.slmpc.lumingraphics.render.frame.RenderExecution;
import com.github.slmpc.lumingraphics.render.resource.RenderResources;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DCommand;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.github.slmpc.lumingraphics.render.shader.FullscreenEffectBinding;
import com.github.slmpc.lumingraphics.render.shader.FullscreenEffectPass;
import com.github.slmpc.lumingraphics.render.shader.FullscreenEffectRequest;
import com.github.slmpc.prismrhi.descriptor.PRhiDescriptorSet;
import com.github.slmpc.prismrhi.descriptor.PRhiDescriptorSetAllocateInfo;
import com.github.slmpc.prismrhi.descriptor.PRhiDescriptorSetLayout;
import com.github.slmpc.prismrhi.descriptor.PRhiDescriptorSetLayoutCreateInfo;
import com.github.slmpc.prismrhi.descriptor.PRhiDescriptorStage;
import com.github.slmpc.prismrhi.descriptor.PRhiDescriptorType;
import com.github.slmpc.prismrhi.device.PRhiDevice;
import com.github.slmpc.prismrhi.pipeline.PRhiGraphicsPipeline;
import com.github.slmpc.prismrhi.rendering.PRhiRect2D;
import com.github.slmpc.prismrhi.rendering.PRhiRenderingAttachment;
import com.github.slmpc.prismrhi.rendering.PRhiRenderingInfo;
import com.github.slmpc.prismrhi.resource.PRhiBuffer;
import com.github.slmpc.prismrhi.resource.PRhiBufferCreateInfo;
import com.github.slmpc.prismrhi.resource.PRhiBufferUsage;
import com.github.slmpc.prismrhi.resource.PRhiImageView;
import com.github.slmpc.prismrhi.resource.PRhiMemoryUsage;
import com.github.slmpc.prismrhi.resource.PRhiSampler;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 为 Minecraft HUD blur 绑定反馈纹理和逐帧 uniform。 */
final class MinecraftBlurResources2612 implements RenderResources, AutoCloseable {
    private static final int MIN_UNIFORM_BYTES = 16;

    private final RenderResources delegate;
    private final PRhiDescriptorSetLayout effectLayout;
    private final ByteBuffer uniformUploadStaging = ByteBuffer.allocateDirect(MinecraftBlurRegion2612.UNIFORM_BYTES);
    private final List<RetiredBinding> retired = new ArrayList<>();
    private BindingScope active;
    private boolean closed;

    MinecraftBlurResources2612(RenderResources delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        effectLayout = delegate.device().createDescriptorSetLayout(PRhiDescriptorSetLayoutCreateInfo.builder()
                .binding(0, PRhiDescriptorType.COMBINED_IMAGE_SAMPLER, 1, PRhiDescriptorStage.FRAGMENT)
                .binding(1, PRhiDescriptorType.UNIFORM_BUFFER, 1, PRhiDescriptorStage.FRAGMENT)
                .binding(2, PRhiDescriptorType.UNIFORM_BUFFER, 1, PRhiDescriptorStage.FRAGMENT)
                .build());
    }

    BindingScope bind(Render2DTexture input, PRhiImageView inputView, PRhiSampler sampler, PRhiImageView targetView) {
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

    @Override public PRhiDevice device() { requireOpen(); return delegate.device(); }
    @Override public PRhiGraphicsPipeline requirePipeline(String id) { requireOpen(); return delegate.requirePipeline(id); }
    @Override public PRhiDescriptorSet requireFrameDescriptor() {
        requireOpen();
        return delegate.requireFrameDescriptor();
    }
    @Override public PRhiDescriptorSet requireTextureDescriptor(Render2DTexture texture) {
        requireOpen();
        return delegate.requireTextureDescriptor(texture);
    }
    @Override public PRhiDescriptorSet requireSegmentedShadowDescriptor(Render2DCommand.SegmentedShadow shadow) {
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
        PRhiBuffer first = createUniformBuffer(payload);
        PRhiBuffer second = null;
        PRhiDescriptorSet descriptor = null;
        try {
            second = createUniformBuffer(ByteBuffer.allocate(0));
            descriptor = device().allocateDescriptorSet(PRhiDescriptorSetAllocateInfo.of(effectLayout));
            PRhiBuffer finalSecond = second;
            descriptor.update(writer -> writer
                    .combinedImageSampler(0, 0, scope.inputView, scope.sampler)
                    .uniformBuffer(1, first)
                    .uniformBuffer(2, finalSecond));
            retired.add(new RetiredBinding(execution.frameId(), descriptor, first, second));
            PRhiRenderingInfo rendering = PRhiRenderingInfo.builder(PRhiRect2D.of(execution.width(), execution.height()))
                    .color(PRhiRenderingAttachment.color(scope.targetView)).build();
            return new FullscreenEffectBinding(descriptor, FullscreenEffectPass.rendering(rendering));
        } catch (RuntimeException failure) {
            closeAfterFailure(descriptor, second, first, failure);
            throw failure;
        }
    }

    private PRhiBuffer createUniformBuffer(ByteBuffer payload) {
        ByteBuffer content = payload.slice();
        PRhiBuffer buffer = device().createBuffer(PRhiBufferCreateInfo.builder(
                        Math.max(MIN_UNIFORM_BYTES, content.remaining()))
                .usage(PRhiBufferUsage.UNIFORM_BUFFER)
                .memoryUsage(PRhiMemoryUsage.CPU_TO_GPU)
                .build());
        if (content.hasRemaining()) {
            uniformUploadStaging.clear().limit(content.remaining());
            uniformUploadStaging.put(content).flip();
            buffer.write(uniformUploadStaging.slice());
        }
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
        private final PRhiImageView inputView;
        private final PRhiSampler sampler;
        private final PRhiImageView targetView;
        private boolean closed;

        private BindingScope(Render2DTexture input, PRhiImageView inputView,
                             PRhiSampler sampler, PRhiImageView targetView) {
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

    private record RetiredBinding(long frameId, PRhiDescriptorSet descriptor,
                                  PRhiBuffer first, PRhiBuffer second) implements AutoCloseable {
        @Override public void close() {
            RuntimeException failure = GraphicsResourceCloser2612.closeReverse(descriptor, second, first);
            if (failure != null) throw failure;
        }
    }
}
