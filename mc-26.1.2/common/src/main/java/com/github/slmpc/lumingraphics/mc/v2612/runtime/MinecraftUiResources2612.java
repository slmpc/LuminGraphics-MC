package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import com.github.slmpc.lumingraphics.render.resource.DefaultRenderResources;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.github.slmpc.lumingraphics.text.atlas.TtfFontLoader;
import com.github.slmpc.lumingraphics.text.font.FontResource;
import com.github.slmpc.lumingraphics.text.emoji.SystemEmojiAtlas;
import com.github.slmpc.lumingraphics.text.layout.TextLayoutEngine;
import com.github.slmpc.lumingraphics.ui.resource.UiResourceNotFoundException;
import com.github.slmpc.lumingraphics.ui.resource.UiResourceResolver;
import com.github.slmpc.lumingraphics.ui.text.UiTextMetrics;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSet;
import com.github.slmpc.prismrhi.resource.RhiFilter;
import com.github.slmpc.prismrhi.resource.RhiImageView;
import com.github.slmpc.prismrhi.resource.RhiSampler;
import com.github.slmpc.prismrhi.resource.RhiSamplerAddressMode;
import com.github.slmpc.prismrhi.resource.RhiSamplerCreateInfo;
import com.mojang.blaze3d.opengl.GlTextureView;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Minecraft 资源包字体与借用纹理的懒解析服务。 */
final class MinecraftUiResources2612 implements UiResourceResolver, AutoCloseable {
    private final Minecraft client;
    private final MinecraftGraphicsRuntime2612 runtime;
    private final DefaultRenderResources renderResources;
    private final MinecraftUiRuntime2612.UiConfig config;
    private final Map<TextureKey, BorrowedTexture> textures = new LinkedHashMap<>();
    private final Map<String, FontResource> fontSources = new LinkedHashMap<>();
    private final Map<String, TtfFontLoader> fonts = new LinkedHashMap<>();
    private final TextLayoutEngine textLayouts = new TextLayoutEngine();
    private MinecraftGlyphAtlasUploader2612 uploader;
    private SystemEmojiAtlas emoji;
    private String defaultFontId;
    private boolean closed;

    MinecraftUiResources2612(Minecraft client, MinecraftGraphicsRuntime2612 runtime,
                             DefaultRenderResources renderResources,
                             MinecraftUiRuntime2612.UiConfig config) {
        this.client = client;
        this.runtime = runtime;
        this.renderResources = renderResources;
        this.config = config;
        config.fontResources().forEach((id, resource) -> fontSources.put(id, resource(resource)));
        defaultFontId = config.defaultFontId();
    }

    @Override
    public synchronized Render2DTexture texture(String id) {
        requireOpen();
        Identifier identifier = Identifier.tryParse(Objects.requireNonNull(id, "id"));
        if (identifier == null) throw new UiResourceNotFoundException("texture", id);
        TextureKey key = new TextureKey(identifier, config.textureFilter());
        if (client.getResourceManager().getResource(identifier).isEmpty()) {
            throw new UiResourceNotFoundException("texture", id);
        }
        if (!(client.getTextureManager().getTexture(identifier).getTextureView() instanceof GlTextureView current)) {
            throw new IllegalStateException("Minecraft texture is not OpenGL-backed: " + identifier);
        }
        BorrowedTexture cached = textures.get(key);
        if (cached != null && cached.minecraftView() == current && !current.isClosed()) return cached.texture();
        if (cached != null) cached.close();
        BorrowedTexture created = borrow(key, current);
        textures.put(key, created);
        return created.texture();
    }

    @Override
    public synchronized TtfFontLoader font(String id) {
        requireOpen();
        String resolvedId = id == null ? defaultFontId : id;
        FontResource source = fontSources.get(resolvedId);
        if (source == null) throw new UiResourceNotFoundException("font", resolvedId);
        return fonts.computeIfAbsent(resolvedId, ignored -> createFont(source));
    }

    @Override
    public Render2DTexture atlasTexture(Object texture) {
        if (texture instanceof MinecraftGlyphAtlasTexture2612 atlas) return atlas.luminTexture();
        return UiResourceResolver.super.atlasTexture(texture);
    }

    synchronized void registerResourceFont(String id, Identifier resource) {
        registerFont(id, resource(resource));
    }

    synchronized void useDefaultFont(String id) {
        requireOpen();
        if (!fontSources.containsKey(id)) throw new UiResourceNotFoundException("font", id);
        defaultFontId = id;
    }

    synchronized void useCustomDefaultFont(String id, Path path) {
        registerFont(id, FontResource.path(path));
        defaultFontId = id;
    }

    synchronized UiTextMetrics textMetrics() {
        requireOpen();
        return UiTextMetrics.of((text, scale, fontId) -> {
            var measured = textLayouts.measure(text, scale, font(fontId));
            return new UiTextMetrics.Measurement(measured.width(), measured.height());
        });
    }

    synchronized SystemEmojiAtlas systemEmojiAtlas() {
        requireOpen();
        if (emoji == null) emoji = new SystemEmojiAtlas(1024, 1024, 64, uploader());
        return emoji;
    }

    synchronized void invalidate() {
        if (closed) return;
        RuntimeException failure = closeReloadable();
        if (failure != null) throw failure;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = closeReloadable();
        failure = GraphicsResourceCloser2612.merge(failure,
                GraphicsResourceCloser2612.closeReverse(textLayouts));
        if (failure != null) throw failure;
    }

    private BorrowedTexture borrow(TextureKey key, GlTextureView minecraftView) {
        RhiImageView view = runtime.blazeBridge().fromBlazeTextureView(minecraftView).orElseThrow();
        RhiFilter filter = key.filter() == MinecraftUiRuntime2612.TextureFilter.NEAREST
                ? RhiFilter.NEAREST : RhiFilter.LINEAR;
        RhiSampler sampler = null;
        RhiDescriptorSet descriptor = null;
        Render2DTexture texture = new Render2DTexture.Resource(key.id().toString());
        try {
            sampler = runtime.device().createSampler(new RhiSamplerCreateInfo(filter, filter,
                    RhiSamplerAddressMode.CLAMP_TO_EDGE, RhiSamplerAddressMode.CLAMP_TO_EDGE,
                    RhiSamplerAddressMode.CLAMP_TO_EDGE, 0.0f));
            descriptor = renderResources.createTextureDescriptor(view, sampler);
            renderResources.registerTextureDescriptor(texture, descriptor, runtime.device().contextIdentity());
            return new BorrowedTexture(minecraftView, texture, view, sampler, descriptor, renderResources);
        } catch (RuntimeException failure) {
            RuntimeException cleanup = GraphicsResourceCloser2612.closeReverse(descriptor, sampler, view);
            if (cleanup != null) failure.addSuppressed(cleanup);
            throw failure;
        }
    }

    private FontResource resource(Identifier id) {
        return new FontResource() {
            @Override public byte[] read() throws IOException {
                try (var input = client.getResourceManager().getResource(id)
                        .orElseThrow(() -> new IOException("Minecraft font resource not found: " + id)).open()) {
                    return input.readAllBytes();
                }
            }

            @Override public String description() { return id.toString(); }
        };
    }

    private void registerFont(String id, FontResource source) {
        requireOpen();
        if (id == null || id.isBlank()) throw new IllegalArgumentException("font id is blank");
        FontResource previous = fontSources.put(id, Objects.requireNonNull(source, "source"));
        TtfFontLoader loaded = fonts.remove(id);
        if (loaded != null) loaded.close();
        if (previous == null && defaultFontId == null) defaultFontId = id;
    }

    private TtfFontLoader createFont(FontResource source) {
        return new TtfFontLoader(source, config.fontPixelHeight(), config.fontPadding(),
                config.atlasWidth(), config.atlasHeight(), config.maxAtlasPages(), uploader(), Runnable::run);
    }

    private MinecraftGlyphAtlasUploader2612 uploader() {
        if (uploader == null) uploader = new MinecraftGlyphAtlasUploader2612(client, runtime, renderResources);
        return uploader;
    }

    private RuntimeException closeReloadable() {
        textLayouts.clear();
        RuntimeException failure = GraphicsResourceCloser2612.closeReverse(emoji);
        failure = GraphicsResourceCloser2612.merge(failure,
                GraphicsResourceCloser2612.closeReverse(fonts.values().toArray(AutoCloseable[]::new)));
        emoji = null;
        fonts.clear();
        failure = GraphicsResourceCloser2612.merge(failure,
                GraphicsResourceCloser2612.closeReverse(uploader));
        uploader = null;
        failure = GraphicsResourceCloser2612.merge(failure,
                GraphicsResourceCloser2612.closeReverse(textures.values().toArray(AutoCloseable[]::new)));
        textures.clear();
        return failure;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Minecraft UI resources are closed");
    }

    private record TextureKey(Identifier id, MinecraftUiRuntime2612.TextureFilter filter) { }

    private static final class BorrowedTexture implements AutoCloseable {
        private final GlTextureView minecraftView;
        private final Render2DTexture texture;
        private final RhiImageView view;
        private final RhiSampler sampler;
        private final RhiDescriptorSet descriptor;
        private final DefaultRenderResources resources;
        private boolean closed;

        private BorrowedTexture(GlTextureView minecraftView, Render2DTexture texture, RhiImageView view,
                                RhiSampler sampler, RhiDescriptorSet descriptor,
                                DefaultRenderResources resources) {
            this.minecraftView = minecraftView;
            this.texture = texture;
            this.view = view;
            this.sampler = sampler;
            this.descriptor = descriptor;
            this.resources = resources;
        }

        GlTextureView minecraftView() { return minecraftView; }
        Render2DTexture texture() {
            if (closed) throw new IllegalStateException("Borrowed Minecraft texture is closed");
            return texture;
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            resources.unregisterTextureDescriptor(texture, descriptor);
            RuntimeException failure = GraphicsResourceCloser2612.closeReverse(descriptor, sampler, view);
            if (failure != null) throw failure;
        }
    }
}
