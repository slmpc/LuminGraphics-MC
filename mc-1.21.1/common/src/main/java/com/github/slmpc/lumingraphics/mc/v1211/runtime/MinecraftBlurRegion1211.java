package com.github.slmpc.lumingraphics.mc.v1211.runtime;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import java.util.List;
import java.util.Objects;

/** Minecraft 1.21.1 HUD 模糊区域描述；当前兼容层保留 API 但不执行后处理。 */
public record MinecraftBlurRegion1211(UiRect bounds, CornerRadii radii, float strength, List<Segment> segments) {
    public MinecraftBlurRegion1211 {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(radii, "radii");
        segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        if (bounds.width() <= 0.0f || bounds.height() <= 0.0f || !Float.isFinite(strength) || strength < 0.0f) {
            throw new IllegalArgumentException("Invalid blur region");
        }
    }

    public static MinecraftBlurRegion1211 rounded(UiRect bounds, float radius, float strength) {
        return new MinecraftBlurRegion1211(bounds, CornerRadii.uniform(radius), strength, List.of());
    }

    public record CornerRadii(float topLeft, float topRight, float bottomRight, float bottomLeft) {
        public CornerRadii {
            if (!finite(topLeft) || !finite(topRight) || !finite(bottomRight) || !finite(bottomLeft)) {
                throw new IllegalArgumentException("Invalid radius");
            }
        }
        public static CornerRadii uniform(float radius) { return new CornerRadii(radius, radius, radius, radius); }
    }

    public record Segment(UiRect bounds, float radius) {
        public Segment {
            Objects.requireNonNull(bounds, "bounds");
            if (bounds.width() <= 0.0f || bounds.height() <= 0.0f || !finite(radius)) {
                throw new IllegalArgumentException("Invalid blur segment");
            }
        }
    }

    private static boolean finite(float value) { return Float.isFinite(value) && value >= 0.0f; }
}
