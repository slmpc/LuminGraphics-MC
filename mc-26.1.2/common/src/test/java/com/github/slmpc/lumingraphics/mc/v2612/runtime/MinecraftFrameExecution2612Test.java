package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.slmpc.lumingraphics.core.geometry.SurfaceMetrics;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

class MinecraftFrameExecution2612Test {
    @Test
    void frameUniformProjectsLogicalUiCoordinatesIntoClipSpace() throws Exception {
        Method capture = MinecraftFrameExecution2612.class
                .getDeclaredMethod("captureUniforms", SurfaceMetrics.class, SurfaceMetrics.class);
        capture.setAccessible(true);
        SurfaceMetrics metrics = new SurfaceMetrics(2560, 1440, 2.0);
        ByteBuffer bytes = (ByteBuffer) capture.invoke(null, metrics, metrics);
        Matrix4f projection = new Matrix4f(bytes.asFloatBuffer());

        assertVector(new Vector4f(0.0f, 0.0f, 0.0f, 1.0f).mul(projection), -1.0f, 1.0f);
        assertVector(new Vector4f(1280.0f, 720.0f, 0.0f, 1.0f).mul(projection), 1.0f, -1.0f);
        assertEquals(2560.0f, bytes.getFloat(64));
        assertEquals(1440.0f, bytes.getFloat(68));
    }

    @Test
    void glyphAtlasStagingUsesDirectBufferWithoutChangingPixels() throws Exception {
        Method stagingBytes = MinecraftGlyphAtlasUploader2612.class
                .getDeclaredMethod("stagingBytes", byte[].class);
        stagingBytes.setAccessible(true);
        byte[] pixels = {0, 1, 127, (byte) 255};
        ByteBuffer bytes = (ByteBuffer) stagingBytes.invoke(null, (Object) pixels);

        byte[] uploaded = new byte[bytes.remaining()];
        bytes.get(uploaded);
        assertEquals(true, bytes.isDirect());
        assertEquals(true, Arrays.equals(pixels, uploaded));
    }

    private static void assertVector(Vector4f actual, float x, float y) {
        assertEquals(x, actual.x, 0.0001f);
        assertEquals(y, actual.y, 0.0001f);
        assertEquals(0.0f, actual.z, 0.0001f);
        assertEquals(1.0f, actual.w, 0.0001f);
    }
}
