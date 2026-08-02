package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import com.github.slmpc.lumingraphics.core.geometry.SurfaceMetrics;
import com.github.slmpc.lumingraphics.render.renderer.RendererSet;
import com.github.slmpc.lumingraphics.render.resource.DefaultRenderResources;
import com.github.slmpc.lumingraphics.render.frame.RenderExecution;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScheduler;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScissorMapper;
import com.github.slmpc.lumingraphics.text.render.TtfTextRenderer;
import com.github.slmpc.lumingraphics.text.atlas.TtfFontLoader;
import com.github.slmpc.lumingraphics.text.emoji.SystemEmojiAtlas;
import com.github.slmpc.lumingraphics.mc.v2612.text.MinecraftFontAdapter2612;
import com.github.slmpc.lumingraphics.ui.render.LuminUiRenderer;
import com.github.slmpc.lumingraphics.ui.render.SchedulerTextBatchSink;
import com.github.slmpc.lumingraphics.ui.scene.UiLayer;
import com.github.slmpc.lumingraphics.ui.scene.UiScene;
import com.github.slmpc.lumingraphics.ui.theme.UiTheme;
import com.github.slmpc.lumingraphics.ui.text.UiTextMetrics;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import com.github.slmpc.prismrhi.format.RhiFormat;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.jspecify.annotations.Nullable;

/** Minecraft 26.1.2 拥有的 Lumin {@link UiTree} 运行时。 */
public final class MinecraftUiRuntime2612 implements AutoCloseable {
    public enum TextureFilter { NEAREST, LINEAR }
    static final float UI_TEXT_SCALE = 0.36f;
    static final int FONT_ATLAS_WIDTH = 1024;
    static final int FONT_ATLAS_HEIGHT = 1024;

    /** UI GPU 资源和默认字体的稳定输入。 */
    public record UiConfig(String defaultFontId, Map<String, Identifier> fontResources, TextureFilter textureFilter,
                           int rendererCapacity, int quadtreeThreshold, int fontPixelHeight, int fontPadding,
                           int atlasWidth, int atlasHeight, int maxAtlasPages) {
        public UiConfig {
            if (defaultFontId == null || defaultFontId.isBlank()) {
                throw new IllegalArgumentException("defaultFontId is blank");
            }
            fontResources = Map.copyOf(Objects.requireNonNull(fontResources, "fontResources"));
            if (!fontResources.containsKey(defaultFontId)) {
                throw new IllegalArgumentException("defaultFontId is not registered: " + defaultFontId);
            }
            Objects.requireNonNull(textureFilter, "textureFilter");
            if (rendererCapacity <= 0 || quadtreeThreshold <= 0 || fontPixelHeight <= 0 || fontPadding < 0
                    || atlasWidth <= 0 || atlasHeight <= 0 || maxAtlasPages <= 0) {
                throw new IllegalArgumentException("Minecraft UI resource sizes are invalid");
            }
            if (atlasWidth != FONT_ATLAS_WIDTH || atlasHeight != FONT_ATLAS_HEIGHT) {
                throw new IllegalArgumentException("Font atlas pages must be exactly 1024 x 1024 pixels");
            }
        }

        public static UiConfig defaults(Identifier defaultFontResource) {
            return new UiConfig("default", Map.of("default", defaultFontResource), TextureFilter.LINEAR,
                    64 * 1024, 16, 72, 16, FONT_ATLAS_WIDTH, FONT_ATLAS_HEIGHT, 8);
        }
    }

    private static MinecraftUiRuntime2612 current;

    private final Minecraft client;
    private final UiConfig config;
    private final MinecraftGraphicsRuntime2612 graphics;
    private final Map<String, Identifier> fontResources;
    private String defaultFontId;
    private final Set<UiScene> scenes = Collections.newSetFromMap(new IdentityHashMap<>());
    private DefaultRenderResources renderResources;
    private MinecraftUiResources2612 uiResources;
    private MinecraftBlurService2612 blurService;
    private MinecraftFontAdapter2612 minecraftFont;
    private boolean closed;

    private MinecraftUiRuntime2612(Minecraft client, UiConfig config,
                                   MinecraftGraphicsRuntime2612 graphics) {
        this.client = client;
        this.config = config;
        this.graphics = graphics;
        fontResources = new LinkedHashMap<>(config.fontResources());
        defaultFontId = config.defaultFontId();
    }

    /** 绑定 Minecraft 当前 OpenGL context，并从首帧开始统一维护命令缓冲。 */
    public static synchronized MinecraftUiRuntime2612 bindCurrent(Minecraft client, UiConfig config) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(config, "config");
        if (current != null) {
            current.requireOpen();
            return current;
        }
        MinecraftGraphicsRuntime2612 graphics = MinecraftGraphicsRuntime2612.bindCurrent(
                new MinecraftGraphicsRuntime2612.CreationConfig(client::execute,
                        () -> new SurfaceMetrics(client.getMainRenderTarget().width,
                                client.getMainRenderTarget().height, client.getWindow().getGuiScale()),
                        client::getMainRenderTarget));
        MinecraftUiRuntime2612 created = new MinecraftUiRuntime2612(client, config, graphics);
        created.setProjectionScale(client.getWindow().getGuiScale());
        if (client.getResourceManager() instanceof ReloadableResourceManager reloadable) {
            ResourceManagerReloadListener listener = manager -> created.onResourceReload();
            reloadable.registerReloadListener(listener);
        }
        graphics.beginFrame(0);
        current = created;
        return created;
    }

    /** 使用约定的资源包字体 {@code lumin_graphics_mc:font/default.ttf}。 */
    public static MinecraftUiRuntime2612 bindCurrent(Minecraft client) {
        return bindCurrent(client, UiConfig.defaults(
                Identifier.fromNamespaceAndPath("lumin_graphics_mc", "font/default.ttf")));
    }

    public static synchronized MinecraftUiRuntime2612 current() {
        if (current == null) throw new IllegalStateException("Minecraft UI runtime is not bound");
        current.requireOpen();
        return current;
    }

    /** 尚未完成 loader 绑定时返回 null，供早期 Minecraft GUI 调用回退原版路径。 */
    public static synchronized @Nullable MinecraftUiRuntime2612 currentOrNull() {
        if (current != null) current.requireOpen();
        return current;
    }

    public MinecraftGraphicsRuntime2612 graphicsRuntime() { requireOpen(); return graphics; }

    /** 设置 Epsilon 等应用层提供的 UI 正交投影缩放，不改变字体布局倍率。 */
    public synchronized void setProjectionScale(double scale) {
        requireOpen();
        graphics.setProjectionScale(scale);
    }

    /** 注册一个由 Minecraft ResourceManager 读取的 TTF/OTF 字体。 */
    public synchronized void registerFont(String id, Identifier resource) {
        requireOpen();
        if (id == null || id.isBlank()) throw new IllegalArgumentException("font id is blank");
        fontResources.put(id, Objects.requireNonNull(resource, "resource"));
        if (uiResources != null) uiResources.registerResourceFont(id, resource);
    }

    /** 将已注册字体设为未指定 font ID 时使用的默认字体。 */
    public synchronized void useDefaultFont(String id) {
        requireOpen();
        if (!fontResources.containsKey(id)) throw new IllegalArgumentException("font is not registered: " + id);
        defaultFontId = id;
        if (uiResources != null) uiResources.useDefaultFont(id);
    }

    /** 使用用户选择的本地 TTF/OTF 文件作为默认字体。 */
    public synchronized void useCustomDefaultFont(String id, Path path) {
        requireOpen();
        ensureResources();
        uiResources.useCustomDefaultFont(id, path);
        defaultFontId = id;
    }

    /** 使用与绘制相同的字体 loader 和 scale 测量 UI 文本。 */
    public synchronized UiTextMetrics textMetrics() {
        requireOpen();
        ensureResources();
        return uiResources.textMetrics();
    }

    /** 返回由本运行时持有并参与资源重载的字体 loader。 */
    public synchronized TtfFontLoader font(String id) {
        requireOpen();
        ensureResources();
        return uiResources.font(id);
    }

    /** 返回使用当前默认字体的 Minecraft Font 适配器。 */
    public synchronized MinecraftFontAdapter2612 minecraftFont() {
        requireOpen();
        ensureResources();
        if (minecraftFont == null) minecraftFont = new MinecraftFontAdapter2612(() -> uiResources.font(null));
        return minecraftFont;
    }

    /** 返回与字体共享 Minecraft-owned glyph atlas uploader 的系统 emoji atlas。 */
    public synchronized SystemEmojiAtlas systemEmojiAtlas() {
        requireOpen();
        ensureResources();
        return uiResources.systemEmojiAtlas();
    }

    /** 创建由本运行时持有资源并随本运行时关闭的可复用 UI scene。 */
    public synchronized UiScene createScene(UiTheme theme) {
        requireOpen();
        ensureResources();
        Render2DScheduler scheduler = new Render2DScheduler(
                RendererSet.create(renderResources, config.rendererCapacity()), config.quadtreeThreshold(),
                Render2DScissorMapper.topLeft(graphics::projectionMetrics));
        SchedulerTextBatchSink sink = new SchedulerTextBatchSink(uiResources);
        TtfTextRenderer text = new TtfTextRenderer(UI_TEXT_SCALE, sink);
        try {
            UiScene scene = new UiScene(scheduler, Objects.requireNonNull(theme, "theme"),
                    new LuminUiRenderer(text, sink, uiResources));
            scenes.add(scene);
            return scene;
        } catch (RuntimeException failure) {
            RuntimeException cleanup = GraphicsResourceCloser2612.closeReverse(text, scheduler);
            if (cleanup != null) failure.addSuppressed(cleanup);
            throw failure;
        }
    }

    public UiScene createScene() { return createScene(UiTheme.defaults()); }

    /** 在 Minecraft 主 RenderTarget 上执行一次完整 scene 帧。 */
    public synchronized void render(UiScene scene, Consumer<UiScene> submissions) {
        requireOpen();
        if (!scenes.contains(Objects.requireNonNull(scene, "scene"))) {
            throw new IllegalArgumentException("UI scene is not owned by this Minecraft runtime");
        }
        Objects.requireNonNull(submissions, "submissions");
        if (!graphics.frameActive()) throw new IllegalStateException("No Minecraft graphics frame is active");
        scene.beginFrame();
        boolean ended = false;
        try {
            submissions.accept(scene);
            try (var frame = new MinecraftFrameExecution2612(graphics, renderResources)) {
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

    /** 在 MC 持有的当前 2D 帧与资源中执行一次自定义 pass。 */
    public synchronized void renderPass(BiConsumer<RenderExecution, DefaultRenderResources> pass) {
        requireOpen();
        Objects.requireNonNull(pass, "pass");
        ensureResources();
        try (var frame = new MinecraftFrameExecution2612(graphics, renderResources)) {
            pass.accept(frame.execution(), renderResources);
        }
    }

    /** 将 Minecraft 主 RenderTarget 的当前内容模糊到指定 HUD 区域。 */
    public synchronized void applyBlur(MinecraftBlurRegion2612 region) {
        requireOpen();
        Objects.requireNonNull(region, "region");
        ensureResources();
        if (blurService == null) blurService = new MinecraftBlurService2612(client, graphics, renderResources);
        var target = graphics.currentRenderTarget();
        RenderExecution execution = new RenderExecution(graphics.commandBuffer(), renderResources,
                graphics.activeFrameId(), graphics.lastEndedFrameId(), target.width(), target.height());
        blurService.apply(execution, region);
    }

    /** 资源包重载后关闭旧字体 atlas 和借用纹理，下次访问按新资源重建。 */
    public synchronized void onResourceReload() {
        if (closed || uiResources == null) return;
        for (UiScene scene : scenes) if (scene.frameActive()) scene.abortFrame();
        uiResources.invalidate();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        RuntimeException failure = GraphicsResourceCloser2612.closeReverse(
                scenes.toArray(AutoCloseable[]::new));
        scenes.clear();
        failure = GraphicsResourceCloser2612.merge(failure,
                GraphicsResourceCloser2612.closeReverse(blurService, uiResources, renderResources, graphics));
        blurService = null;
        uiResources = null;
        minecraftFont = null;
        renderResources = null;
        clearCurrent(this);
        if (failure != null) throw failure;
    }

    private void ensureResources() {
        if (renderResources != null) return;
        if (!graphics.frameActive()) throw new IllegalStateException("No Minecraft graphics frame is active");
        var target = graphics.currentRenderTarget();
        RhiFormat depth = target.depthView().map(view -> view.format()).orElse(RhiFormat.UNDEFINED);
        renderResources = new DefaultRenderResources(graphics.device(), target.colorView().format(), depth);
        try {
            uiResources = new MinecraftUiResources2612(client, graphics, renderResources, config);
            fontResources.forEach(uiResources::registerResourceFont);
            uiResources.useDefaultFont(defaultFontId);
        } catch (RuntimeException failure) {
            RuntimeException cleanup = GraphicsResourceCloser2612.closeReverse(renderResources);
            renderResources = null;
            if (cleanup != null) failure.addSuppressed(cleanup);
            throw failure;
        }
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Minecraft UI runtime is closed");
        graphics.luminContext().requireRenderThread();
    }

    private static synchronized void clearCurrent(MinecraftUiRuntime2612 runtime) {
        if (current == runtime) current = null;
    }
}
