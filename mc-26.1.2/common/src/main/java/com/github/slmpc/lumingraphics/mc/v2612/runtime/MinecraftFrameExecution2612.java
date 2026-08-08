package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import com.github.slmpc.lumingraphics.core.geometry.SurfaceMetrics;
import com.github.slmpc.lumingraphics.core.target.RenderTarget;
import com.github.slmpc.lumingraphics.render.frame.RenderExecution;
import com.github.slmpc.lumingraphics.render.resource.DefaultRenderResources;
import com.github.slmpc.prismrhi.rendering.RhiAttachmentLoadOp;
import com.github.slmpc.prismrhi.rendering.RhiRect2D;
import com.github.slmpc.prismrhi.rendering.RhiRenderingAttachment;
import com.github.slmpc.prismrhi.rendering.RhiRenderingInfo;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.joml.Matrix4f;

/** 将 Minecraft 当前帧状态转换为一次完整的 Lumin 2D render pass。 */
final class MinecraftFrameExecution2612 implements AutoCloseable {
    private static final int FRAME_UNIFORM_BYTES = 20 * Float.BYTES;

    private final RenderExecution execution;
    private boolean closed;

    MinecraftFrameExecution2612(MinecraftGraphicsRuntime2612 runtime, DefaultRenderResources resources) {
        RenderTarget target = runtime.currentRenderTarget();
        SurfaceMetrics framebufferMetrics = runtime.luminContext().metrics();
        SurfaceMetrics projectionMetrics = runtime.projectionMetrics();
        resources.releaseCompleted(runtime.lastEndedFrameId());
        resources.updateFrameUniforms(captureUniforms(framebufferMetrics, projectionMetrics));
        execution = new RenderExecution(runtime.commandBuffer(), resources, runtime.activeFrameId(),
                runtime.lastEndedFrameId(), target.width(), target.height());
        RhiRenderingInfo.Builder rendering = RhiRenderingInfo.builder(RhiRect2D.of(target.width(), target.height()))
                .color(RhiRenderingAttachment.color(target.colorView()));
        target.depthView().ifPresent(depth -> rendering.depth(
                RhiRenderingAttachment.depth(depth, RhiAttachmentLoadOp.LOAD, 1.0f)));
        execution.commands().beginRendering(rendering.build());
    }

    RenderExecution execution() {
        if (closed) throw new IllegalStateException("Minecraft UI frame execution is closed");
        return execution;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        execution.commands().endRendering();
    }

    static ByteBuffer captureUniforms(SurfaceMetrics framebufferMetrics, SurfaceMetrics projectionMetrics) {
        var logicalSize = projectionMetrics.logicalSize();
        Matrix4f projection = new Matrix4f().setOrtho(0.0f, (float) logicalSize.width(),
                (float) logicalSize.height(), 0.0f, -1.0f, 1.0f);
        ByteBuffer bytes = ByteBuffer.allocateDirect(FRAME_UNIFORM_BYTES).order(ByteOrder.nativeOrder());
        projection.get(bytes);
        bytes.position(16 * Float.BYTES);
        bytes.putFloat(framebufferMetrics.framebufferWidth()).putFloat(framebufferMetrics.framebufferHeight())
                .putFloat(1.0f / framebufferMetrics.framebufferWidth())
                .putFloat(1.0f / framebufferMetrics.framebufferHeight());
        return bytes.flip();
    }
}
