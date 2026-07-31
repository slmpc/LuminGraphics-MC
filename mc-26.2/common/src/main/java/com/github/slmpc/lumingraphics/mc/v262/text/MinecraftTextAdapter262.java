package com.github.slmpc.lumingraphics.mc.v262.text;

import com.github.slmpc.lumingraphics.text.font.FontMetrics;
import com.github.slmpc.lumingraphics.text.layout.TextMeasurement;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;

public final class MinecraftTextAdapter262 {
    public static final String SOURCE_PROVENANCE =
            "neoform-26.2-2+loom-minecraft-merged-043a8b3edf-26.2-sources";
    private final Font font;

    public MinecraftTextAdapter262(Font font) {
        this.font = Objects.requireNonNull(font, "font");
    }

    public FontMetrics metrics() {
        return new FontMetrics(7, 2, 0, font.lineHeight);
    }

    public TextMeasurement measure(String text) {
        Objects.requireNonNull(text, "text");
        String[] lines = text.split("\\R", -1);
        int width = 0;
        for (String line : lines) width = Math.max(width, font.width(line));
        return new TextMeasurement(width, (float)lines.length * font.lineHeight, lines.length);
    }

    public PreparedText262 prepare(String text, float x, float y, int color, boolean shadow,
                                   int backgroundColor) {
        Font.PreparedText prepared = font.prepareText(text, x, y, color, shadow, backgroundColor);
        List<Renderable262> renderables = new ArrayList<>();
        prepared.visit(new Font.GlyphVisitor() {
            @Override public void acceptRenderable(TextRenderable renderable) {
                renderables.add(Renderable262.from(renderable));
            }
        });
        return new PreparedText262(List.copyOf(renderables), prepared.bounds());
    }

    public record PreparedText262(List<Renderable262> renderables, Object bounds) {
        public PreparedText262 { renderables = List.copyOf(renderables); }
    }

    public record Renderable262(float left, float top, float right, float bottom,
                                GpuTextureView textureView, RenderPipeline guiPipeline) {
        static Renderable262 from(TextRenderable value) {
            return new Renderable262(value.left(), value.top(), value.right(), value.bottom(),
                    value.textureView(), value.guiPipeline());
        }
    }
}
