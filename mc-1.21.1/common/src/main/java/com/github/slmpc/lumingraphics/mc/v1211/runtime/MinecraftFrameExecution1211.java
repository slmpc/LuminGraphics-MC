package com.github.slmpc.lumingraphics.mc.v1211.runtime;

import com.github.slmpc.lumingraphics.core.geometry.SurfaceMetrics;
import com.github.slmpc.lumingraphics.core.target.RenderTarget;
import com.github.slmpc.lumingraphics.render.frame.RenderExecution;
import com.github.slmpc.lumingraphics.render.resource.DefaultRenderResources;
import com.github.slmpc.prismrhi.rendering.PRhiRect2D;
import com.github.slmpc.prismrhi.rendering.PRhiRenderingAttachment;
import com.github.slmpc.prismrhi.rendering.PRhiRenderingInfo;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.joml.Matrix4f;

/** 将 Minecraft 1.21.1 主目标转换为一次 Lumin 2D pass。 */
final class MinecraftFrameExecution1211 implements AutoCloseable {
    private static final int FRAME_UNIFORM_BYTES = 20 * Float.BYTES;
    private final RenderExecution execution;
    private boolean closed;

    MinecraftFrameExecution1211(MinecraftGraphicsRuntime1211 runtime, DefaultRenderResources resources) {
        RenderTarget target = runtime.currentRenderTarget();
        SurfaceMetrics framebufferMetrics = runtime.luminContext().metrics();
        SurfaceMetrics projectionMetrics = runtime.projectionMetrics();
        resources.releaseCompleted(runtime.lastEndedFrameId());
        resources.updateFrameUniforms(captureUniforms(framebufferMetrics, projectionMetrics));
        execution = new RenderExecution(runtime.commandBuffer(), resources, runtime.activeFrameId(),
                runtime.lastEndedFrameId(), target.width(), target.height());
        execution.commands().beginRendering(PRhiRenderingInfo.builder(PRhiRect2D.of(target.width(), target.height()))
                .color(PRhiRenderingAttachment.color(target.colorView())).build());
    }

    RenderExecution execution() {
        if (closed) throw new IllegalStateException("Minecraft UI frame execution is closed");
        return execution;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        execution.commands().endRendering();
    }

    private static ByteBuffer captureUniforms(SurfaceMetrics framebufferMetrics, SurfaceMetrics projectionMetrics) {
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
