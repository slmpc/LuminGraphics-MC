package com.github.slmpc.lumingraphics.mc.v2612.text;

import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftGlyphAtlasTexture2612;
import com.github.slmpc.lumingraphics.mc.v2612.mixin.RenderTypeFactory2612;
import com.github.slmpc.lumingraphics.text.atlas.GlyphDescriptor;
import com.github.slmpc.lumingraphics.text.atlas.TtfFontLoader;
import com.github.slmpc.lumingraphics.text.font.MissingGlyphException;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

/** Lumin font loader 到 Minecraft 26.1.2 Font/TextRenderable 的公共适配器。 */
public final class MinecraftFontAdapter2612 {
    public record RenderOptions(RenderPipeline antialiasedPipeline, RenderPipeline pixelatedPipeline,
                                BooleanSupplier antialiasing) {
        public RenderOptions {
            Objects.requireNonNull(antialiasedPipeline, "antialiasedPipeline");
            Objects.requireNonNull(pixelatedPipeline, "pixelatedPipeline");
            Objects.requireNonNull(antialiasing, "antialiasing");
        }

        RenderPipeline pipeline() { return antialiasing.getAsBoolean() ? antialiasedPipeline : pixelatedPipeline; }
        FilterMode filter() { return antialiasing.getAsBoolean() ? FilterMode.LINEAR : FilterMode.NEAREST; }
    }

    private static final Map<RenderKey, RenderType> RENDER_TYPES = new HashMap<>();
    private final Supplier<TtfFontLoader> font;
    private TtfFontLoader metricsFont;
    private FontMetricsAdapter metrics;

    public MinecraftFontAdapter2612(Supplier<TtfFontLoader> font) {
        this.font = Objects.requireNonNull(font, "font");
    }

    public synchronized @Nullable BakedGlyph glyph(int codepoint, RenderOptions options) {
        Objects.requireNonNull(options, "options");
        if (!Character.isValidCodePoint(codepoint)) return null;
        TtfFontLoader loader = font.get();
        FontMetricsAdapter currentMetrics = metrics(loader);
        if (Character.isWhitespace(codepoint)) {
            return new FontGlyphAdapter(currentMetrics.advance(codepoint, Style.EMPTY),
                    (x, y, color, shadow, style, bold, shadowOffset) -> null);
        }
        try {
            GlyphDescriptor descriptor = loader.requireGlyph(codepoint);
            float advance = currentMetrics.advance(codepoint, Style.EMPTY);
            return new FontGlyphAdapter(advance, (x, y, color, shadowColor, style, boldOffset, shadowOffset) ->
                    new StyledGlyph(loader, descriptor, x, y, color, shadowColor, style,
                            boldOffset, shadowOffset, advance, options));
        } catch (MissingGlyphException missing) {
            return null;
        }
    }

    public synchronized float width(String text) { return metrics(font.get()).width(text); }
    public synchronized float width(FormattedCharSequence text) { return metrics(font.get()).width(text); }
    public synchronized float width(FormattedText text) { return metrics(font.get()).width(text); }

    private FontMetricsAdapter metrics(TtfFontLoader loader) {
        if (metrics == null || metricsFont != loader) {
            metrics = new FontMetricsAdapter(loader);
            metricsFont = loader;
        }
        return metrics;
    }

    private static synchronized RenderType renderType(MinecraftGlyphAtlasTexture2612 texture,
                                                       RenderOptions options) {
        RenderPipeline pipeline = options.pipeline();
        RenderKey key = new RenderKey(texture.minecraftId(), pipeline);
        return RENDER_TYPES.computeIfAbsent(key, ignored -> RenderTypeFactory2612.lumin$create(
                "lumin_graphics_mc_text", RenderSetup.builder(pipeline)
                        .withTexture("Sampler0", texture.minecraftId(),
                                () -> RenderSystem.getSamplerCache().getClampToEdge(options.filter()))
                        .bufferSize(RenderType.SMALL_BUFFER_SIZE).createRenderSetup()));
    }

    private record RenderKey(net.minecraft.resources.Identifier id, RenderPipeline pipeline) { }

    /** Atlas revision 退休时同步清理对应 RenderType，避免字体持续扩容时缓存线性增长。 */
    public static synchronized void releaseTexture(net.minecraft.resources.Identifier id) {
        RENDER_TYPES.keySet().removeIf(key -> key.id().equals(id));
    }

    private record StyledGlyph(TtfFontLoader font, GlyphDescriptor glyph, float x, float y,
                               int color, int shadowColor, Style style, float boldOffset,
                               float shadowOffset, float advance, RenderOptions options)
            implements TextRenderable.Styled, TextRenderableAdapter {
        private float scale() { return FontMetricsAdapter.VANILLA_LINE_HEIGHT / font.metrics().lineHeight(); }
        private float glyphX() { return x + glyph.xOffset() * scale(); }
        private float glyphY() { return y + font.metrics().ascent() * scale() + glyph.yOffset() * scale(); }
        private float glyphRight() { return glyphX() + glyph.width() * scale(); }
        private float glyphBottom() { return glyphY() + glyph.height() * scale(); }
        private MinecraftGlyphAtlasTexture2612 texture() {
            Object texture = glyph.atlas().upload().texture();
            if (texture instanceof MinecraftGlyphAtlasTexture2612 minecraftTexture) return minecraftTexture;
            throw new IllegalStateException("Glyph atlas is not owned by Minecraft 26.1.2 runtime: " + texture);
        }

        @Override public void render(Matrix4fc pose, VertexConsumer buffer, int packedLightCoords, boolean flat) {
            if (shadowColor != 0) renderQuad(pose, buffer, shadowOffset, shadowOffset, 0.0f, shadowColor);
            float depth = shadowColor != 0 && !flat ? Font.SHADOW_DEPTH : 0.0f;
            renderQuad(pose, buffer, 0.0f, 0.0f, depth, color);
            if (style.isBold()) renderQuad(pose, buffer, boldOffset, 0.0f,
                    depth + (flat ? 0.0f : 0.001f), color);
        }

        private void renderQuad(Matrix4fc pose, VertexConsumer buffer, float offsetX, float offsetY,
                                float depth, int drawColor) {
            float x0 = glyphX() + offsetX;
            float y0 = glyphY() + offsetY;
            float x1 = glyphRight() + offsetX;
            float y1 = glyphBottom() + offsetY;
            float shearTop = style.isItalic() ? 1.0f - 0.25f * (y0 - y) : 0.0f;
            float shearBottom = style.isItalic() ? 1.0f - 0.25f * (y1 - y) : 0.0f;
            var uv = glyph.uv();
            buffer.addVertex(pose, x0 + shearTop, y0, depth).setUv(uv.u0(), uv.v0()).setColor(drawColor);
            buffer.addVertex(pose, x0 + shearBottom, y1, depth).setUv(uv.u0(), uv.v1()).setColor(drawColor);
            buffer.addVertex(pose, x1 + shearBottom, y1, depth).setUv(uv.u1(), uv.v1()).setColor(drawColor);
            buffer.addVertex(pose, x1 + shearTop, y0, depth).setUv(uv.u1(), uv.v0()).setColor(drawColor);
        }

        @Override public RenderType renderType(Font.DisplayMode displayMode) {
            return MinecraftFontAdapter2612.renderType(texture(), options);
        }
        @Override public GpuTextureView textureView() { return texture().minecraftTextureView(); }
        @Override public RenderPipeline guiPipeline() { return options.pipeline(); }
        @Override public GpuSampler sampler() {
            return RenderSystem.getSamplerCache().getClampToEdge(options.filter());
        }
        @Override public float left() { return glyphX(); }
        @Override public float top() { return glyphY(); }
        @Override public float right() { return glyphRight() + (shadowColor != 0 ? shadowOffset : 0.0f); }
        @Override public float activeRight() { return x + advance; }
        @Override public float bottom() { return glyphBottom() + (shadowColor != 0 ? shadowOffset : 0.0f); }
    }
}
