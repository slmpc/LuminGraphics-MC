package com.github.slmpc.lumingraphics.mc.v1211.runtime;

import com.github.slmpc.lumingraphics.core.geometry.SurfaceMetrics;
import com.github.slmpc.lumingraphics.render.frame.RenderExecution;
import com.github.slmpc.lumingraphics.render.renderer.RendererSet;
import com.github.slmpc.lumingraphics.render.resource.DefaultRenderResources;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScheduler;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScissorMapper;
import com.github.slmpc.lumingraphics.text.atlas.TtfFontLoader;
import com.github.slmpc.lumingraphics.text.emoji.SystemEmojiAtlas;
import com.github.slmpc.lumingraphics.text.render.TtfTextRenderer;
import com.github.slmpc.lumingraphics.ui.render.LuminUiRenderer;
import com.github.slmpc.lumingraphics.ui.render.SchedulerTextBatchSink;
import com.github.slmpc.lumingraphics.ui.scene.UiLayer;
import com.github.slmpc.lumingraphics.ui.scene.UiScene;
import com.github.slmpc.lumingraphics.ui.text.UiTextMetrics;
import com.github.slmpc.lumingraphics.ui.theme.UiTheme;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import com.github.slmpc.prismrhi.format.PRhiFormat;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Minecraft 1.21.1 拥有的 Lumin UI runtime。 */
public final class MinecraftUiRuntime1211 implements AutoCloseable {
    public enum TextureFilter { NEAREST, LINEAR }

    static final float UI_TEXT_SCALE = 0.30f;
    private static final int FONT_ATLAS_SIZE = 1024;

    public record UiConfig(String defaultFontId, Map<String, ResourceLocation> fontResources,
                           TextureFilter textureFilter, int rendererCapacity, int quadtreeThreshold,
                           int fontPixelHeight, int fontPadding, int atlasWidth, int atlasHeight,
                           int maxAtlasPages) {
        public UiConfig {
            if (defaultFontId == null || defaultFontId.isBlank()) {
                throw new IllegalArgumentException("defaultFontId is blank");
            }
            fontResources = Map.copyOf(Objects.requireNonNull(fontResources, "fontResources"));
            if (!fontResources.containsKey(defaultFontId)) {
                throw new IllegalArgumentException("defaultFontId is not registered: " + defaultFontId);
            }
            Objects.requireNonNull(textureFilter, "textureFilter");
            if (rendererCapacity <= 0 || quadtreeThreshold <= 0 || fontPixelHeight <= 0 || fontPadding <= 0
                    || atlasWidth != FONT_ATLAS_SIZE || atlasHeight != FONT_ATLAS_SIZE || maxAtlasPages <= 0) {
                throw new IllegalArgumentException("Minecraft UI resource sizes are invalid");
            }
        }

        public static UiConfig defaults(ResourceLocation defaultFontResource) {
            return new UiConfig("default", Map.of("default", defaultFontResource), TextureFilter.LINEAR,
                    64 * 1024, 16, 48, 4, FONT_ATLAS_SIZE, FONT_ATLAS_SIZE, 8);
        }
    }

    private static MinecraftUiRuntime1211 current;
    private final Minecraft client;
    private final UiConfig config;
    private final MinecraftGraphicsRuntime1211 graphics;
    private final Map<String, ResourceLocation> fontResources;
    private final Map<UiScene, TtfTextRenderer> scenes = new IdentityHashMap<>();
    private String defaultFontId;
    private DefaultRenderResources renderResources;
    private MinecraftUiResources1211 uiResources;
    private int fontGlyphsPerFrame = Integer.MAX_VALUE;
    private float uiTextScaleMultiplier = 1.0f;
    private boolean closed;

    private MinecraftUiRuntime1211(Minecraft client, UiConfig config, MinecraftGraphicsRuntime1211 graphics) {
        this.client = client;
        this.config = config;
        this.graphics = graphics;
        this.fontResources = new LinkedHashMap<>(config.fontResources());
        this.defaultFontId = config.defaultFontId();
    }

    public static synchronized MinecraftUiRuntime1211 bindCurrent(Minecraft client, UiConfig config) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(config, "config");
        if (current != null) {
            current.requireOpen();
            return current;
        }
        MinecraftGraphicsRuntime1211 graphics = MinecraftGraphicsRuntime1211.bindCurrent(
                new MinecraftGraphicsRuntime1211.CreationConfig(client::execute,
                        () -> new SurfaceMetrics(client.getMainRenderTarget().width,
                                client.getMainRenderTarget().height, client.getWindow().getGuiScale()),
                        client::getMainRenderTarget));
        MinecraftUiRuntime1211 created = new MinecraftUiRuntime1211(client, config, graphics);
        created.setProjectionScale(client.getWindow().getGuiScale());
        if (client.getResourceManager() instanceof ReloadableResourceManager reloadable) {
            ResourceManagerReloadListener listener = manager -> created.onResourceReload();
            reloadable.registerReloadListener(listener);
        }
        graphics.beginFrame();
        current = created;
        return created;
    }

    public static MinecraftUiRuntime1211 bindCurrent(Minecraft client) {
        return bindCurrent(client, UiConfig.defaults(
                ResourceLocation.fromNamespaceAndPath("lumin_graphics_mc", "font/default.ttf")));
    }

    public static synchronized MinecraftUiRuntime1211 current() {
        if (current == null) throw new IllegalStateException("Minecraft UI runtime is not bound");
        current.requireOpen();
        return current;
    }

    public static synchronized MinecraftUiRuntime1211 currentOrNull() {
        if (current != null) current.requireOpen();
        return current;
    }

    public MinecraftGraphicsRuntime1211 graphicsRuntime() { requireOpen(); return graphics; }
    public synchronized void setProjectionScale(double scale) { requireOpen(); graphics.setProjectionScale(scale); }

    public synchronized void setFontGlyphsPerFrame(int maxGlyphs) {
        requireOpen();
        if (maxGlyphs <= 0) throw new IllegalArgumentException("maxGlyphs must be positive");
        fontGlyphsPerFrame = maxGlyphs;
        if (uiResources != null && graphics.frameActive()) {
            uiResources.beginFontFrame(graphics.activeFrameId(), maxGlyphs);
        }
    }

    public synchronized void setUiTextScaleMultiplier(float multiplier) {
        requireOpen();
        float effective = effectiveUiTextScale(multiplier);
        scenes.values().forEach(text -> text.setScaleMultiplier(effective));
        uiTextScaleMultiplier = multiplier;
    }

    public synchronized void registerFont(String id, ResourceLocation resource) {
        requireOpen();
        if (id == null || id.isBlank()) throw new IllegalArgumentException("font id is blank");
        ResourceLocation value = Objects.requireNonNull(resource, "resource");
        if (Objects.equals(fontResources.get(id), value)) return;
        fontResources.put(id, value);
        if (uiResources != null) uiResources.registerResourceFont(id, value);
    }

    public synchronized void useDefaultFont(String id) {
        requireOpen();
        if (!fontResources.containsKey(id)) throw new IllegalArgumentException("font is not registered: " + id);
        defaultFontId = id;
        if (uiResources != null) uiResources.useDefaultFont(id);
    }

    public synchronized void useCustomDefaultFont(String id, Path path) {
        requireOpen();
        ensureResources();
        uiResources.useCustomDefaultFont(id, path);
        defaultFontId = id;
    }

    public synchronized UiTextMetrics textMetrics() {
        requireOpen();
        ensureResources();
        return uiResources.textMetrics();
    }

    public synchronized TtfFontLoader font(String id) {
        requireOpen();
        ensureResources();
        return uiResources.font(id);
    }

    public synchronized SystemEmojiAtlas systemEmojiAtlas() {
        requireOpen();
        ensureResources();
        return uiResources.systemEmojiAtlas();
    }

    public synchronized UiScene createScene(UiTheme theme) {
        requireOpen();
        ensureResources();
        Render2DScheduler scheduler = new Render2DScheduler(
                RendererSet.create(renderResources, config.rendererCapacity()), config.quadtreeThreshold(),
                Render2DScissorMapper.topLeft(graphics::projectionMetrics));
        SchedulerTextBatchSink sink = new SchedulerTextBatchSink(uiResources);
        TtfTextRenderer text = new TtfTextRenderer(effectiveUiTextScale(uiTextScaleMultiplier), sink);
        try {
            UiScene scene = new UiScene(scheduler, Objects.requireNonNull(theme, "theme"),
                    new LuminUiRenderer(text, sink, uiResources), scenes::remove);
            scenes.put(scene, text);
            return scene;
        } catch (RuntimeException failure) {
            RuntimeException cleanup = GraphicsResourceCloser1211.closeReverse(text, scheduler);
            if (cleanup != null) failure.addSuppressed(cleanup);
            throw failure;
        }
    }

    public UiScene createScene() { return createScene(UiTheme.defaults()); }

    public synchronized void render(UiScene scene, Consumer<UiScene> submissions) {
        requireOpen();
        if (!scenes.containsKey(Objects.requireNonNull(scene, "scene"))) {
            throw new IllegalArgumentException("UI scene is not owned by this Minecraft runtime");
        }
        if (!graphics.frameActive()) graphics.beginFrame();
        uiResources.beginFontFrame(graphics.activeFrameId(), fontGlyphsPerFrame);
        scene.beginFrame();
        boolean ended = false;
        try {
            submissions.accept(scene);
            try (var frame = new MinecraftFrameExecution1211(graphics, renderResources)) {
                scene.endFrame(frame.execution());
                ended = true;
            }
        } finally {
            if (!ended && scene.frameActive()) scene.abortFrame();
        }
    }

    public void render(UiScene scene, UiLayer layer, UiTree tree) {
        render(scene, active -> active.submit(layer, tree));
    }

    public synchronized void renderPass(BiConsumer<RenderExecution, DefaultRenderResources> pass) {
        requireOpen();
        ensureResources();
        if (!graphics.frameActive()) graphics.beginFrame();
        try (var frame = new MinecraftFrameExecution1211(graphics, renderResources)) {
            pass.accept(frame.execution(), renderResources);
        }
    }

    /** 1.21.1 兼容层不接管 Minecraft 后处理目标，调用保留为无操作降级。 */
    public synchronized void applyBlur(MinecraftBlurRegion1211 region) {
        requireOpen();
        Objects.requireNonNull(region, "region");
    }

    public synchronized void onResourceReload() {
        if (closed || uiResources == null) return;
        scenes.keySet().forEach(scene -> { if (scene.frameActive()) scene.abortFrame(); });
        uiResources.invalidate();
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = GraphicsResourceCloser1211.closeReverse(
                scenes.keySet().toArray(AutoCloseable[]::new));
        scenes.clear();
        failure = GraphicsResourceCloser1211.merge(failure,
                GraphicsResourceCloser1211.closeReverse(uiResources, renderResources, graphics));
        uiResources = null;
        renderResources = null;
        clearCurrent(this);
        if (failure != null) throw failure;
    }

    private void ensureResources() {
        if (renderResources != null) return;
        if (!graphics.frameActive()) graphics.beginFrame();
        renderResources = new DefaultRenderResources(graphics.device(), PRhiFormat.RGBA8_UNORM, PRhiFormat.UNDEFINED);
        try {
            uiResources = new MinecraftUiResources1211(client, graphics, renderResources, config,
                    () -> UI_TEXT_SCALE * uiTextScaleMultiplier);
            uiResources.beginFontFrame(graphics.activeFrameId(), fontGlyphsPerFrame);
            fontResources.forEach(uiResources::registerResourceFont);
            uiResources.useDefaultFont(defaultFontId);
        } catch (RuntimeException failure) {
            RuntimeException cleanup = GraphicsResourceCloser1211.closeReverse(renderResources);
            renderResources = null;
            if (cleanup != null) failure.addSuppressed(cleanup);
            throw failure;
        }
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Minecraft UI runtime is closed");
        graphics.luminContext().requireRenderThread();
    }

    private static float effectiveUiTextScale(float multiplier) {
        if (!Float.isFinite(multiplier) || multiplier <= 0.0f) {
            throw new IllegalArgumentException("UI text scale multiplier must be positive and finite");
        }
        return UI_TEXT_SCALE * multiplier;
    }

    private static synchronized void clearCurrent(MinecraftUiRuntime1211 runtime) {
        if (current == runtime) current = null;
    }
}
