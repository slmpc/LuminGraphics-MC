package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import com.github.slmpc.lumingraphics.core.geometry.SurfaceMetrics;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecraftFrameExecution2612ProjectionTest {
    @Test
    void clientScaleControlsOrthographicProjectionInsteadOfMinecraftGuiScale() {
        SurfaceMetrics framebuffer = new SurfaceMetrics(1920, 1080, 3.0);
        SurfaceMetrics projection = new SurfaceMetrics(1920, 1080, 2.0);

        ByteBuffer uniforms = MinecraftFrameExecution2612.captureUniforms(framebuffer, projection);

        assertEquals(2.0f / 960.0f, uniforms.getFloat(0), 0.000001f,
                "projection width must use Client Setting scale");
        assertEquals(-2.0f / 540.0f, uniforms.getFloat(5 * Float.BYTES), 0.000001f,
                "projection height must use Client Setting scale");
        assertEquals(1920.0f, uniforms.getFloat(16 * Float.BYTES), 0.000001f,
                "framebuffer width must remain the physical target width");
        assertEquals(1080.0f, uniforms.getFloat(17 * Float.BYTES), 0.000001f,
                "framebuffer height must remain the physical target height");
    }
}
