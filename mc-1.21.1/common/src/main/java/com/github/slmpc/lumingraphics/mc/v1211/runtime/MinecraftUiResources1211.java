package com.github.slmpc.lumingraphics.mc.v1211.runtime;

import com.github.slmpc.lumingraphics.render.resource.DefaultRenderResources;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.github.slmpc.lumingraphics.text.atlas.AtlasPixels;
import com.github.slmpc.lumingraphics.text.atlas.AtlasPixelFormat;
import com.github.slmpc.lumingraphics.text.atlas.GlyphAtlasUpload;
import com.github.slmpc.lumingraphics.text.atlas.GlyphAtlasUploader;
import com.github.slmpc.lumingraphics.text.atlas.TtfFontLoader;
import com.github.slmpc.lumingraphics.text.emoji.SystemEmojiAtlas;
import com.github.slmpc.lumingraphics.text.font.FontResource;
import com.github.slmpc.lumingraphics.text.layout.TextLayoutEngine;
import com.github.slmpc.lumingraphics.ui.resource.UiResourceNotFoundException;
import com.github.slmpc.lumingraphics.ui.resource.UiResourceResolver;
import com.github.slmpc.lumingraphics.ui.text.UiTextMetrics;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlImageAdoption;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlImageViewAdoption;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlNativeObjectTypes;
import com.github.slmpc.prismrhi.barrier.PRhiImageBarrier;
import com.github.slmpc.prismrhi.barrier.PRhiPipelineBarrier;
import com.github.slmpc.prismrhi.barrier.PRhiResourceState;
import com.github.slmpc.prismrhi.descriptor.PRhiDescriptorSet;
import com.github.slmpc.prismrhi.format.PRhiExtent3D;
import com.github.slmpc.prismrhi.format.PRhiFormat;
import com.github.slmpc.prismrhi.resource.PRhiBuffer;
import com.github.slmpc.prismrhi.resource.PRhiBufferCreateInfo;
import com.github.slmpc.prismrhi.resource.PRhiBufferUsage;
import com.github.slmpc.prismrhi.resource.PRhiImage;
import com.github.slmpc.prismrhi.resource.PRhiImageCreateInfo;
import com.github.slmpc.prismrhi.resource.PRhiImageUsage;
import com.github.slmpc.prismrhi.resource.PRhiImageView;
import com.github.slmpc.prismrhi.resource.PRhiImageViewCreateInfo;
import com.github.slmpc.prismrhi.resource.PRhiMemoryUsage;
import com.github.slmpc.prismrhi.resource.PRhiNativeObject;
import com.github.slmpc.prismrhi.resource.PRhiOwnership;
import com.github.slmpc.prismrhi.resource.PRhiFilter;
import com.github.slmpc.prismrhi.resource.PRhiSampler;
import com.github.slmpc.prismrhi.resource.PRhiSamplerAddressMode;
import com.github.slmpc.prismrhi.resource.PRhiSamplerCreateInfo;
import com.mojang.blaze3d.platform.GlStateManager;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

/** Minecraft 1.21.1 资源包字体与 OpenGL 纹理解析服务。 */
final class MinecraftUiResources1211 implements UiResourceResolver, AutoCloseable {
    private static final AtomicLong NEXT_ATLAS_ID = new AtomicLong();

    private final Minecraft client;
    private final MinecraftGraphicsRuntime1211 runtime;
    private final DefaultRenderResources renderResources;
    private final MinecraftUiRuntime1211.UiConfig config;
    private final DoubleSupplier textScaleMultiplier;
    private final Map<TextureKey, BorrowedTexture> textures = new LinkedHashMap<>();
    private final Map<String, FontResource> fontSources = new LinkedHashMap<>();
    private final Map<String, Object> fontSourceKeys = new LinkedHashMap<>();
    private final Map<String, TtfFontLoader> fonts = new LinkedHashMap<>();
    private final TextLayoutEngine textLayouts = new TextLayoutEngine();
    private final TtfFontLoader.GlyphBudget fontGlyphBudget = new TtfFontLoader.GlyphBudget();
    private final ExecutorService glyphRasterizer = Executors.newSingleThreadExecutor(task ->
            Thread.ofPlatform().daemon().name("Lumin Glyph Rasterizer 1.21.1").unstarted(task));
    private final GlyphAtlasUploader uploader = this::uploadAtlas;
    private SystemEmojiAtlas emoji;
    private String defaultFontId;
    private boolean closed;

    MinecraftUiResources1211(Minecraft client, MinecraftGraphicsRuntime1211 runtime,
            DefaultRenderResources renderResources, MinecraftUiRuntime1211.UiConfig config,
            DoubleSupplier textScaleMultiplier) {
        this.client = client;
        this.runtime = runtime;
        this.renderResources = renderResources;
        this.config = config;
        this.textScaleMultiplier = textScaleMultiplier;
        config.fontResources().forEach((id, resource) -> {
            fontSources.put(id, resource(resource));
            fontSourceKeys.put(id, new ResourceFontKey(resource));
        });
        defaultFontId = config.defaultFontId();
    }

    @Override public synchronized Render2DTexture texture(String id) {
        requireOpen();
        ResourceLocation location = ResourceLocation.tryParse(Objects.requireNonNull(id, "id"));
        if (location == null || client.getResourceManager().getResource(location).isEmpty()) {
            throw new UiResourceNotFoundException("texture", id);
        }
        int nativeId = client.getTextureManager().getTexture(location).getId();
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._bindTexture(nativeId);
        int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        GlStateManager._bindTexture(previous);
        if (width <= 0 || height <= 0) throw new UiResourceNotFoundException("texture", id);
        TextureKey key = new TextureKey(location, config.textureFilter());
        BorrowedTexture cached = textures.get(key);
        if (cached != null && cached.matches(nativeId, width, height)) return cached.texture();
        if (cached != null) cached.close();
        BorrowedTexture created = borrowTexture(key, nativeId, width, height);
        textures.put(key, created);
        return created.texture();
    }

    @Override public synchronized TtfFontLoader font(String id) {
        requireOpen();
        String resolvedId = id == null ? defaultFontId : id;
        FontResource source = fontSources.get(resolvedId);
        if (source == null) throw new UiResourceNotFoundException("font", resolvedId);
        return fonts.computeIfAbsent(resolvedId, ignored -> createFont(source));
    }

    synchronized void registerResourceFont(String id, ResourceLocation resource) {
        registerFont(id, resource(resource), new ResourceFontKey(resource));
    }

    synchronized void useDefaultFont(String id) {
        requireOpen();
        if (!fontSources.containsKey(id)) throw new UiResourceNotFoundException("font", id);
        defaultFontId = id;
    }

    synchronized void useCustomDefaultFont(String id, Path path) {
        Path normalized = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        registerFont(id, FontResource.path(normalized), new PathFontKey(normalized));
        defaultFontId = id;
    }

    synchronized UiTextMetrics textMetrics() {
        requireOpen();
        return UiTextMetrics.of((text, scale, fontId) -> {
            float effectiveScale = (float) (scale * textScaleMultiplier.getAsDouble());
            var measured = textLayouts.measure(text, effectiveScale, font(fontId));
            return new UiTextMetrics.Measurement(measured.width(), measured.height());
        });
    }

    synchronized SystemEmojiAtlas systemEmojiAtlas() {
        requireOpen();
        if (emoji == null) emoji = new SystemEmojiAtlas(1024, 1024, 64, uploader);
        return emoji;
    }

    synchronized void beginFontFrame(long frameId, int maxGlyphs) {
        requireOpen();
        fontGlyphBudget.beginFrame(frameId, maxGlyphs);
    }

    synchronized void invalidate() {
        if (!closed) {
            RuntimeException failure = closeReloadable();
            if (failure != null) throw failure;
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = closeReloadable();
        failure = GraphicsResourceCloser1211.merge(failure, GraphicsResourceCloser1211.closeReverse(textLayouts));
        glyphRasterizer.shutdownNow();
        fontGlyphBudget.clear();
        if (failure != null) throw failure;
    }

    private BorrowedTexture borrowTexture(TextureKey key, int nativeId, int width, int height) {
        PRhiImage image = null;
        PRhiImageView view = null;
        PRhiSampler sampler = null;
        PRhiDescriptorSet descriptor = null;
        Render2DTexture texture = new Render2DTexture.Resource(key.location().toString());
        try {
            PRhiImageCreateInfo info = PRhiImageCreateInfo.builder(PRhiExtent3D.of2D(width, height))
                    .format(PRhiFormat.RGBA8_UNORM).usage(PRhiImageUsage.SAMPLED)
                    .memoryUsage(PRhiMemoryUsage.GPU_ONLY).build();
            image = runtime.device().adoptImage(new OpenGlImageAdoption(
                    new PRhiNativeObject(OpenGlNativeObjectTypes.TEXTURE, nativeId), info, PRhiOwnership.BORROWED,
                    runtime.device().contextIdentity(), runtime.externalContext().invalidation()));
            view = runtime.device().adoptImageView(new OpenGlImageViewAdoption(PRhiImageViewCreateInfo.of(image)));
            PRhiFilter filter = key.filter() == MinecraftUiRuntime1211.TextureFilter.NEAREST
                    ? PRhiFilter.NEAREST : PRhiFilter.LINEAR;
            sampler = runtime.device().createSampler(new PRhiSamplerCreateInfo(filter, filter,
                    PRhiSamplerAddressMode.CLAMP_TO_EDGE, PRhiSamplerAddressMode.CLAMP_TO_EDGE,
                    PRhiSamplerAddressMode.CLAMP_TO_EDGE, 0.0f));
            descriptor = renderResources.createTextureDescriptor(view, sampler);
            renderResources.registerTextureDescriptor(texture, descriptor, runtime.device().contextIdentity());
            return new BorrowedTexture(nativeId, width, height, texture, image, view, sampler, descriptor);
        } catch (RuntimeException failure) {
            RuntimeException cleanup = GraphicsResourceCloser1211.closeReverse(descriptor, sampler, view, image);
            if (cleanup != null) failure.addSuppressed(cleanup);
            throw failure;
        }
    }

    private GlyphAtlasUpload uploadAtlas(AtlasPixels pixels) {
        runtime.luminContext().requireRenderThread();
        PRhiBuffer staging = null;
        PRhiImage image = null;
        PRhiImageView view = null;
        PRhiSampler sampler = null;
        PRhiDescriptorSet descriptor = null;
        Render2DTexture texture = new Render2DTexture.Resource(
                "lumin-graphics-mc:glyph-atlas/" + NEXT_ATLAS_ID.incrementAndGet());
        try {
            byte[] data = pixels.data();
            PRhiFormat format = pixels.format() == AtlasPixelFormat.ALPHA8
                    ? PRhiFormat.R8_UNORM : PRhiFormat.RGBA8_UNORM;
            staging = runtime.device().createBuffer(PRhiBufferCreateInfo.builder(data.length)
                    .usage(PRhiBufferUsage.TRANSFER_SRC).memoryUsage(PRhiMemoryUsage.CPU_TO_GPU).build());
            staging.write(ByteBuffer.allocateDirect(data.length).put(data).flip());
            image = runtime.device().createImage(PRhiImageCreateInfo.builder(
                            PRhiExtent3D.of2D(pixels.width(), pixels.height()))
                    .format(format).usage(PRhiImageUsage.TRANSFER_DST).usage(PRhiImageUsage.SAMPLED).build());
            view = runtime.device().createImageView(PRhiImageViewCreateInfo.of(image));
            sampler = runtime.device().createSampler(PRhiSamplerCreateInfo.linearRepeat());
            descriptor = renderResources.createTextureDescriptor(view, sampler);
            renderResources.registerTextureDescriptor(texture, descriptor, runtime.device().contextIdentity());
            runtime.commandBuffer().pipelineBarrier(PRhiPipelineBarrier.builder().image(PRhiImageBarrier.of(
                    image, PRhiResourceState.UNDEFINED, PRhiResourceState.TRANSFER_DST)).build());
            runtime.commandBuffer().copyBufferToImage(staging, image);
            runtime.commandBuffer().pipelineBarrier(PRhiPipelineBarrier.builder().image(PRhiImageBarrier.of(
                    image, PRhiResourceState.TRANSFER_DST, PRhiResourceState.SAMPLED_IMAGE)).build());
            runtime.retireAfterFrame(staging);
            staging = null;
            PRhiImage ownedImage = image;
            PRhiImageView ownedView = view;
            PRhiSampler ownedSampler = sampler;
            PRhiDescriptorSet ownedDescriptor = descriptor;
            return new GlyphAtlasUpload(texture, () -> runtime.retireAfterFrame(() -> {
                renderResources.unregisterTextureDescriptor(texture, ownedDescriptor);
                RuntimeException failure = GraphicsResourceCloser1211.closeReverse(
                        ownedDescriptor, ownedSampler, ownedView, ownedImage);
                if (failure != null) throw failure;
            }));
        } catch (RuntimeException failure) {
            RuntimeException cleanup = GraphicsResourceCloser1211.closeReverse(
                    descriptor, sampler, view, image, staging);
            if (cleanup != null) failure.addSuppressed(cleanup);
            throw failure;
        }
    }

    private FontResource resource(ResourceLocation id) {
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

    private void registerFont(String id, FontResource source, Object sourceKey) {
        requireOpen();
        if (id == null || id.isBlank()) throw new IllegalArgumentException("font id is blank");
        if (Objects.equals(fontSourceKeys.get(id), sourceKey)) return;
        fontSources.put(id, Objects.requireNonNull(source, "source"));
        fontSourceKeys.put(id, Objects.requireNonNull(sourceKey, "sourceKey"));
        TtfFontLoader loaded = fonts.remove(id);
        if (loaded != null) loaded.close();
    }

    private TtfFontLoader createFont(FontResource source) {
        return new TtfFontLoader(source, config.fontPixelHeight(), config.fontPadding(),
                config.atlasWidth(), config.atlasHeight(), config.maxAtlasPages(),
                uploader, glyphRasterizer, fontGlyphBudget);
    }

    private RuntimeException closeReloadable() {
        textLayouts.clear();
        RuntimeException failure = GraphicsResourceCloser1211.closeReverse(emoji);
        failure = GraphicsResourceCloser1211.merge(failure,
                GraphicsResourceCloser1211.closeReverse(fonts.values().toArray(AutoCloseable[]::new)));
        failure = GraphicsResourceCloser1211.merge(failure,
                GraphicsResourceCloser1211.closeReverse(textures.values().toArray(AutoCloseable[]::new)));
        emoji = null;
        fonts.clear();
        textures.clear();
        fontGlyphBudget.clear();
        return failure;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Minecraft UI resources are closed");
    }

    private record TextureKey(ResourceLocation location, MinecraftUiRuntime1211.TextureFilter filter) { }
    private record ResourceFontKey(ResourceLocation location) { }
    private record PathFontKey(Path path) { }

    private final class BorrowedTexture implements AutoCloseable {
        private final int nativeId;
        private final int width;
        private final int height;
        private final Render2DTexture texture;
        private final PRhiImage image;
        private final PRhiImageView view;
        private final PRhiSampler sampler;
        private final PRhiDescriptorSet descriptor;
        private boolean released;

        private BorrowedTexture(int nativeId, int width, int height, Render2DTexture texture,
                PRhiImage image, PRhiImageView view, PRhiSampler sampler, PRhiDescriptorSet descriptor) {
            this.nativeId = nativeId;
            this.width = width;
            this.height = height;
            this.texture = texture;
            this.image = image;
            this.view = view;
            this.sampler = sampler;
            this.descriptor = descriptor;
        }

        boolean matches(int id, int textureWidth, int textureHeight) {
            return !released && nativeId == id && width == textureWidth && height == textureHeight;
        }

        Render2DTexture texture() {
            if (released) throw new IllegalStateException("Borrowed Minecraft texture is closed");
            return texture;
        }

        @Override public void close() {
            if (released) return;
            released = true;
            renderResources.unregisterTextureDescriptor(texture, descriptor);
            RuntimeException failure = GraphicsResourceCloser1211.closeReverse(descriptor, sampler, view, image);
            if (failure != null) throw failure;
        }
    }
}
