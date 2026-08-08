package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import com.github.slmpc.lumingraphics.core.context.LuminGraphicsContext;
import com.github.slmpc.lumingraphics.core.geometry.SurfaceMetrics;
import com.github.slmpc.lumingraphics.core.target.RenderTarget;
import com.github.slmpc.lumingraphics.core.threading.RenderThreadGate;
import com.github.slmpc.lumingraphics.mc.v2612.bridge.Blaze3DBridge2612;
import com.github.slmpc.lumingraphics.mc.v2612.bridge.GlStateManagerBridge2612;
import com.github.slmpc.prismrhi.PrismRHI;
import com.github.slmpc.prismrhi.backend.PRhiBackendProvider;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalContext;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalDevice;
import com.github.slmpc.prismrhi.backend.opengl41.Gl41BackendProvider;
import com.github.slmpc.prismrhi.backend.opengl46.Gl46BackendProvider;
import com.github.slmpc.prismrhi.command.PRhiCommandBuffer;
import com.github.slmpc.prismrhi.command.PRhiCommandBufferLevel;
import com.github.slmpc.prismrhi.command.PRhiCommandPool;
import com.github.slmpc.prismrhi.command.PRhiCommandPoolCreateInfo;
import com.github.slmpc.prismrhi.context.PRhiContextIdentity;
import com.github.slmpc.prismrhi.context.PRhiInvalidationToken;
import com.github.slmpc.prismrhi.device.PRhiDeviceCreateInfo;
import com.github.slmpc.prismrhi.instance.PRhiInstance;
import com.github.slmpc.prismrhi.instance.PRhiInstanceCreateInfo;
import com.github.slmpc.prismrhi.queue.PRhiQueue;
import com.github.slmpc.prismrhi.queue.PRhiQueueType;
import com.github.slmpc.prismrhi.queue.PRhiSubmitInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.lwjgl.opengl.GL;

/** Minecraft 26.1.2 当前 OpenGL context 上的 Prism/Lumin 公共运行时。 */
public final class MinecraftGraphicsRuntime2612 implements AutoCloseable {
    private static MinecraftGraphicsRuntime2612 current;

    public record CreationConfig(Executor renderExecutor, Supplier<SurfaceMetrics> metricsSupplier,
                                 Supplier<com.mojang.blaze3d.pipeline.RenderTarget> renderTargetSupplier) {
        public CreationConfig {
            Objects.requireNonNull(renderExecutor, "renderExecutor");
            Objects.requireNonNull(metricsSupplier, "metricsSupplier");
            Objects.requireNonNull(renderTargetSupplier, "renderTargetSupplier");
        }
    }

    private final OpenGlExternalContext externalContext;
    private final PRhiInstance instance;
    private final OpenGlExternalDevice device;
    private final PRhiQueue graphicsQueue;
    private final PRhiCommandPool commandPool;
    private final PRhiCommandBuffer commandBuffer;
    private final Blaze3DBridge2612 blazeBridge;
    private final MinecraftRenderTargetBridge2612 targetBridge;
    private final LuminGraphicsContext luminContext;
    private final FrameCoordinator2612 frames;
    private final RuntimeLifecycle2612 lifecycle;
    private volatile double projectionScale = Double.NaN;

    private MinecraftGraphicsRuntime2612(OpenGlExternalContext context, PRhiInstance instance,
            OpenGlExternalDevice device, PRhiQueue queue, PRhiCommandPool pool, PRhiCommandBuffer buffer,
            Blaze3DBridge2612 bridge, MinecraftRenderTargetBridge2612 targets,
            LuminGraphicsContext luminContext, FrameCoordinator2612 frames) {
        this.externalContext = context;
        this.instance = instance;
        this.device = device;
        graphicsQueue = queue;
        commandPool = pool;
        commandBuffer = buffer;
        blazeBridge = bridge;
        targetBridge = targets;
        this.luminContext = luminContext;
        this.frames = frames;
        lifecycle = new RuntimeLifecycle2612(context::requireCurrent);
        lifecycle.register(pool);
        lifecycle.register(buffer);
        lifecycle.register(targets);
        lifecycle.register(frames);
    }

    public static synchronized MinecraftGraphicsRuntime2612 bindCurrent(CreationConfig config) {
        Objects.requireNonNull(config, "config");
        if (current != null) {
            current.requireAccess();
            return current;
        }
        RenderSystem.assertOnRenderThread();
        var capabilities = GL.getCapabilities();
        var invalidation = new PRhiInvalidationToken();
        long handle = Integer.toUnsignedLong(System.identityHashCode(capabilities)) + 1L;
        var identity = new PRhiContextIdentity(handle, "minecraft-26.1.2-render-context");
        var context = new OpenGlExternalContext(capabilities, Thread.currentThread(), identity, invalidation,
                expected -> GL.getCapabilities() == capabilities && identity.equals(expected));
        current = create(context, config);
        return current;
    }

    public static synchronized MinecraftGraphicsRuntime2612 current() {
        if (current == null) throw new IllegalStateException("LuminGraphics-MC runtime is not bound");
        current.requireAccess();
        return current;
    }

    private static MinecraftGraphicsRuntime2612 create(OpenGlExternalContext context, CreationConfig config) {
        PRhiInstance instance = null;
        OpenGlExternalDevice device = null;
        PRhiCommandPool pool = null;
        PRhiCommandBuffer buffer = null;
        MinecraftRenderTargetBridge2612 targets = null;
        LuminGraphicsContext lumin = null;
        try {
            context.requireCurrent();
            PRhiBackendProvider provider = isMacOs()
                    ? new Gl41BackendProvider(context)
                    : new Gl46BackendProvider(context);
            instance = PrismRHI.createInstance(provider, PRhiInstanceCreateInfo.builder()
                    .applicationName("LuminGraphics-MC 26.1.2").build());
            device = (OpenGlExternalDevice) instance.createDevice(instance.enumeratePhysicalDevices().getFirst(),
                    PRhiDeviceCreateInfo.builder().debugName("minecraft-26.1.2")
                            .glStateBridge(GlStateManagerBridge2612.INSTANCE).build());
            PRhiQueue queue = device.queue(PRhiQueueType.GRAPHICS);
            pool = device.createCommandPool(new PRhiCommandPoolCreateInfo(PRhiQueueType.GRAPHICS, true, true));
            buffer = pool.allocateCommandBuffer(PRhiCommandBufferLevel.PRIMARY);
            Blaze3DBridge2612 bridge = new Blaze3DBridge2612(device);
            targets = new MinecraftRenderTargetBridge2612(bridge, device.contextIdentity(), config.renderTargetSupplier());
            PRhiCommandBuffer ownedBuffer = buffer;
            MinecraftRenderTargetBridge2612 ownedTargets = targets;
            FrameCoordinator2612 frames = new FrameCoordinator2612(new FrameCoordinator2612.Driver() {
                @Override public FrameCoordinator2612.TargetLease acquireTarget() { return ownedTargets.acquire(); }
                @Override public void resetCommandBuffer() { ownedBuffer.reset(); }
                @Override public void beginCommandBuffer() { ownedBuffer.begin(); }
                @Override public void endCommandBuffer() { ownedBuffer.end(); }
                @Override public void submitCommandBuffer() { queue.submit(PRhiSubmitInfo.of(ownedBuffer)); }
            });
            lumin = new LuminGraphicsContext(device,
                    new RenderThreadGate(context.ownerThread(), config.renderExecutor()), config.metricsSupplier(),
                    () -> ((MinecraftRenderTargetBridge2612.BorrowedTarget) frames.currentTarget()).target());
            return new MinecraftGraphicsRuntime2612(context, instance, device, queue, pool, buffer,
                    bridge, targets, lumin, frames);
        } catch (RuntimeException failure) {
            RuntimeException cleanup = GraphicsResourceCloser2612.closeReverse(lumin, targets, buffer, pool, device, instance,
                    () -> { context.invalidation().invalidate(); context.invalidation().close(); });
            if (cleanup != null) failure.addSuppressed(cleanup);
            throw new IllegalStateException("Failed to bind LuminGraphics-MC runtime", failure);
        }
    }

    public OpenGlExternalContext externalContext() { requireAccess(); return externalContext; }
    public PRhiInstance instance() { requireAccess(); return instance; }
    public OpenGlExternalDevice device() { requireAccess(); return device; }
    public PRhiQueue graphicsQueue() { requireAccess(); return graphicsQueue; }
    public PRhiCommandPool commandPool() { requireAccess(); return commandPool; }
    public PRhiCommandBuffer commandBuffer() {
        requireAccess();
        if (!frames.frameActive()) throw new IllegalStateException("No graphics frame is active");
        return commandBuffer;
    }
    public Blaze3DBridge2612 blazeBridge() { requireAccess(); return blazeBridge; }
    public LuminGraphicsContext luminContext() { requireAccess(); return luminContext; }
    /** 设置当前 Lumin 2D 帧使用的应用层正交投影缩放。 */
    public void setProjectionScale(double scale) {
        requireAccess();
        if (!Double.isFinite(scale) || scale <= 0.0) {
            throw new IllegalArgumentException("projection scale must be positive and finite");
        }
        projectionScale = scale;
    }
    /** 返回使用当前应用层缩放重建的 framebuffer 指标。 */
    public SurfaceMetrics projectionMetrics() {
        requireAccess();
        SurfaceMetrics framebuffer = luminContext.metrics();
        double scale = projectionScale;
        if (!Double.isFinite(scale) || scale <= 0.0) scale = framebuffer.scale();
        return new SurfaceMetrics(framebuffer.framebufferWidth(), framebuffer.framebufferHeight(), scale);
    }
    public RenderTarget currentRenderTarget() {
        requireAccess();
        if (!frames.frameActive()) throw new IllegalStateException("No graphics frame is active");
        return luminContext.renderTarget();
    }
    public void beginFrame(long frameId) { requireAccess(); frames.beginFrame(frameId); }
    public long endFrame() { requireAccess(); return frames.endFrame(); }
    public long abortFrame() { requireAccess(); return frames.abortFrame(); }
    public boolean frameActive() { return frames.frameActive(); }
    public long activeFrameId() { requireAccess(); return frames.activeFrameId(); }
    public long lastEndedFrameId() { requireAccess(); return frames.lastEndedFrameId(); }
    void retireAfterFrame(AutoCloseable resource) { requireAccess(); frames.retireAfterFrame(resource); }
    public void invalidateRenderTargets(String reason) { requireAccess(); targetBridge.invalidate(reason); }
    public void invalidateContext() { close(); }
    public boolean acceptingSubmissions() { return lifecycle.acceptingSubmissions(); }
    public <T extends AutoCloseable> T register(T resource) { return lifecycle.register(resource); }

    @Override public void close() {
        if (!lifecycle.acceptingSubmissions()) return;
        lifecycle.requireAccess();
        RuntimeException failure = GraphicsResourceCloser2612.closeReverse(frames);
        try {
            lifecycle.close(luminContext, device, instance,
                    () -> { externalContext.invalidation().invalidate(); externalContext.invalidation().close(); });
        } catch (RuntimeException exception) { failure = GraphicsResourceCloser2612.merge(failure, exception); }
        finally { clearCurrent(this); }
        if (failure != null) throw failure;
    }

    private void requireAccess() { lifecycle.requireAccess(); }

    private static boolean isMacOs() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    private static synchronized void clearCurrent(MinecraftGraphicsRuntime2612 runtime) {
        if (current == runtime) current = null;
    }
}
