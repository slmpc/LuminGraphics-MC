package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import com.github.slmpc.lumingraphics.core.context.LuminGraphicsContext;
import com.github.slmpc.lumingraphics.core.geometry.SurfaceMetrics;
import com.github.slmpc.lumingraphics.core.target.RenderTarget;
import com.github.slmpc.lumingraphics.core.threading.RenderThreadGate;
import com.github.slmpc.lumingraphics.mc.v2612.bridge.Blaze3DBridge2612;
import com.github.slmpc.prismrhi.PrismRHI;
import com.github.slmpc.prismrhi.backend.RhiBackendProvider;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalContext;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalDevice;
import com.github.slmpc.prismrhi.backend.opengl41.Gl41BackendProvider;
import com.github.slmpc.prismrhi.backend.opengldsa.GlDsaBackendProvider;
import com.github.slmpc.prismrhi.command.RhiCommandBuffer;
import com.github.slmpc.prismrhi.command.RhiCommandBufferLevel;
import com.github.slmpc.prismrhi.command.RhiCommandPool;
import com.github.slmpc.prismrhi.command.RhiCommandPoolCreateInfo;
import com.github.slmpc.prismrhi.context.RhiContextIdentity;
import com.github.slmpc.prismrhi.context.RhiInvalidationToken;
import com.github.slmpc.prismrhi.device.RhiDeviceCreateInfo;
import com.github.slmpc.prismrhi.instance.RhiInstance;
import com.github.slmpc.prismrhi.instance.RhiInstanceCreateInfo;
import com.github.slmpc.prismrhi.queue.RhiQueue;
import com.github.slmpc.prismrhi.queue.RhiQueueType;
import com.github.slmpc.prismrhi.queue.RhiSubmitInfo;
import com.mojang.blaze3d.systems.RenderSystem;
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
    private final RhiInstance instance;
    private final OpenGlExternalDevice device;
    private final RhiQueue graphicsQueue;
    private final RhiCommandPool commandPool;
    private final RhiCommandBuffer commandBuffer;
    private final Blaze3DBridge2612 blazeBridge;
    private final MinecraftRenderTargetBridge2612 targetBridge;
    private final LuminGraphicsContext luminContext;
    private final FrameCoordinator2612 frames;
    private final RuntimeLifecycle2612 lifecycle;

    private MinecraftGraphicsRuntime2612(OpenGlExternalContext context, RhiInstance instance,
            OpenGlExternalDevice device, RhiQueue queue, RhiCommandPool pool, RhiCommandBuffer buffer,
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
        var invalidation = new RhiInvalidationToken();
        long handle = Integer.toUnsignedLong(System.identityHashCode(capabilities)) + 1L;
        var identity = new RhiContextIdentity(handle, "minecraft-26.1.2-render-context");
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
        RhiInstance instance = null;
        OpenGlExternalDevice device = null;
        RhiCommandPool pool = null;
        RhiCommandBuffer buffer = null;
        MinecraftRenderTargetBridge2612 targets = null;
        LuminGraphicsContext lumin = null;
        try {
            context.requireCurrent();
            RhiBackendProvider provider = context.capabilities().OpenGL45
                    ? new GlDsaBackendProvider(context) : new Gl41BackendProvider(context);
            if (!context.capabilities().OpenGL45 && !context.capabilities().OpenGL41) {
                throw new IllegalStateException("Minecraft requires OpenGL 4.1 or newer");
            }
            instance = PrismRHI.createInstance(provider, RhiInstanceCreateInfo.builder()
                    .applicationName("LuminGraphics-MC 26.1.2").build());
            device = (OpenGlExternalDevice) instance.createDevice(instance.enumeratePhysicalDevices().getFirst(),
                    RhiDeviceCreateInfo.builder().debugName("minecraft-26.1.2").build());
            RhiQueue queue = device.queue(RhiQueueType.GRAPHICS);
            pool = device.createCommandPool(new RhiCommandPoolCreateInfo(RhiQueueType.GRAPHICS, true, true));
            buffer = pool.allocateCommandBuffer(RhiCommandBufferLevel.PRIMARY);
            Blaze3DBridge2612 bridge = new Blaze3DBridge2612(device);
            targets = new MinecraftRenderTargetBridge2612(bridge, device.contextIdentity(), config.renderTargetSupplier());
            RhiCommandBuffer ownedBuffer = buffer;
            MinecraftRenderTargetBridge2612 ownedTargets = targets;
            FrameCoordinator2612 frames = new FrameCoordinator2612(new FrameCoordinator2612.Driver() {
                @Override public FrameCoordinator2612.TargetLease acquireTarget() { return ownedTargets.acquire(); }
                @Override public void resetCommandBuffer() { ownedBuffer.reset(); }
                @Override public void beginCommandBuffer() { ownedBuffer.begin(); }
                @Override public void endCommandBuffer() { ownedBuffer.end(); }
                @Override public void submitCommandBuffer() { queue.submit(RhiSubmitInfo.of(ownedBuffer)); }
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
    public RhiInstance instance() { requireAccess(); return instance; }
    public OpenGlExternalDevice device() { requireAccess(); return device; }
    public RhiQueue graphicsQueue() { requireAccess(); return graphicsQueue; }
    public RhiCommandPool commandPool() { requireAccess(); return commandPool; }
    public RhiCommandBuffer commandBuffer() {
        requireAccess();
        if (!frames.frameActive()) throw new IllegalStateException("No graphics frame is active");
        return commandBuffer;
    }
    public Blaze3DBridge2612 blazeBridge() { requireAccess(); return blazeBridge; }
    public LuminGraphicsContext luminContext() { requireAccess(); return luminContext; }
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

    private static synchronized void clearCurrent(MinecraftGraphicsRuntime2612 runtime) {
        if (current == runtime) current = null;
    }
}
