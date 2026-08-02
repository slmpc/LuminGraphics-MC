package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Objects;

/** Minecraft HUD 圆角区域模糊的公共描述与 std140 编码。 */
public record MinecraftBlurRegion2612(UiRect bounds, CornerRadii radii, float strength, List<Segment> segments) {
    private static final int MAX_SEGMENTS = 64;
    private static final int UNIFORM_BYTES = 2112;

    public MinecraftBlurRegion2612 {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(radii, "radii");
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        if (bounds.width() <= 0.0f || bounds.height() <= 0.0f || !Float.isFinite(strength) || strength < 0.0f
                || segments.size() > MAX_SEGMENTS) throw new IllegalArgumentException("Invalid blur region");
    }

    public static MinecraftBlurRegion2612 rounded(UiRect bounds, float radius, float strength) {
        return new MinecraftBlurRegion2612(bounds, CornerRadii.uniform(radius), strength, List.of());
    }

    ByteBuffer uniforms(int framebufferWidth, int framebufferHeight, float guiScale) {
        if (framebufferWidth <= 0 || framebufferHeight <= 0 || !Float.isFinite(guiScale) || guiScale <= 0.0f) {
            throw new IllegalArgumentException("Framebuffer dimensions and GUI scale must be positive");
        }
        ByteBuffer result = ByteBuffer.allocateDirect(UNIFORM_BYTES).order(ByteOrder.nativeOrder());
        put(result, framebufferWidth, framebufferHeight, strength, 0.0f);
        put(result, bounds.width() * guiScale, bounds.height() * guiScale, bounds.x() * guiScale,
                framebufferHeight - (bounds.y() + bounds.height()) * guiScale);
        put(result, radii.topLeft * guiScale, radii.topRight * guiScale, radii.bottomRight * guiScale, radii.bottomLeft * guiScale);
        put(result, segments.size(), 0.0f, 0.0f, 0.0f);
        for (int index = 0; index < MAX_SEGMENTS; index++) {
            UiRect rect = index < segments.size() ? segments.get(index).bounds : null;
            put(result, rect == null ? 0.0f : rect.x() * guiScale,
                    rect == null ? 0.0f : framebufferHeight - (rect.y() + rect.height()) * guiScale,
                    rect == null ? 0.0f : rect.width() * guiScale, rect == null ? 0.0f : rect.height() * guiScale);
        }
        for (int index = 0; index < MAX_SEGMENTS; index++) put(result,
                index < segments.size() ? segments.get(index).radius * guiScale : 0.0f, 0.0f, 0.0f, 0.0f);
        return result.flip();
    }

    private static void put(ByteBuffer buffer, float x, float y, float z, float w) { buffer.putFloat(x).putFloat(y).putFloat(z).putFloat(w); }
    public record CornerRadii(float topLeft, float topRight, float bottomRight, float bottomLeft) {
        public CornerRadii { if (!finite(topLeft) || !finite(topRight) || !finite(bottomRight) || !finite(bottomLeft)) throw new IllegalArgumentException("Invalid radius"); }
        public static CornerRadii uniform(float radius) { return new CornerRadii(radius, radius, radius, radius); }
    }
    public record Segment(UiRect bounds, float radius) {
        public Segment { Objects.requireNonNull(bounds, "bounds"); if (bounds.width() <= 0.0f || bounds.height() <= 0.0f || !finite(radius)) throw new IllegalArgumentException("Invalid blur segment"); }
    }
    private static boolean finite(float value) { return Float.isFinite(value) && value >= 0.0f; }
}
