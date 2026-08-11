package com.github.slmpc.lumingraphics.mc.v1211.runtime;

import com.github.slmpc.lumingraphics.core.context.LuminGraphicsContext;
import com.github.slmpc.lumingraphics.core.geometry.SurfaceMetrics;
import com.github.slmpc.lumingraphics.core.threading.RenderThreadGate;
import com.github.slmpc.lumingraphics.mc.v1211.bridge.GlStateManagerBridge1211;
import com.github.slmpc.prismrhi.PrismRHI;
import com.github.slmpc.prismrhi.backend.PRhiBackendProvider;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalContext;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalDevice;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlImageAdoption;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlImageViewAdoption;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlNativeObjectTypes;
import com.github.slmpc.prismrhi.backend.opengl41.Gl41BackendProvider;
import com.github.slmpc.prismrhi.backend.opengl46.Gl46BackendProvider;
import com.github.slmpc.prismrhi.command.PRhiCommandBuffer;
import com.github.slmpc.prismrhi.command.PRhiCommandBufferLevel;
import com.github.slmpc.prismrhi.command.PRhiCommandPool;
import com.github.slmpc.prismrhi.command.PRhiCommandPoolCreateInfo;
import com.github.slmpc.prismrhi.context.PRhiContextIdentity;
import com.github.slmpc.prismrhi.context.PRhiInvalidationToken;
import com.github.slmpc.prismrhi.device.PRhiDeviceCreateInfo;
import com.github.slmpc.prismrhi.format.PRhiExtent3D;
import com.github.slmpc.prismrhi.format.PRhiFormat;
import com.github.slmpc.prismrhi.instance.PRhiInstance;
import com.github.slmpc.prismrhi.instance.PRhiInstanceCreateInfo;
import com.github.slmpc.prismrhi.queue.PRhiQueue;
import com.github.slmpc.prismrhi.queue.PRhiQueueType;
import com.github.slmpc.prismrhi.queue.PRhiSubmitInfo;
import com.github.slmpc.prismrhi.resource.PRhiImage;
import com.github.slmpc.prismrhi.resource.PRhiImageCreateInfo;
import com.github.slmpc.prismrhi.resource.PRhiImageUsage;
import com.github.slmpc.prismrhi.resource.PRhiImageView;
import com.github.slmpc.prismrhi.resource.PRhiImageViewCreateInfo;
import com.github.slmpc.prismrhi.resource.PRhiMemoryUsage;
import com.github.slmpc.prismrhi.resource.PRhiNativeObject;
import com.github.slmpc.prismrhi.resource.PRhiOwnership;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.lwjgl.opengl.GL;

/** Minecraft 1.21.1 当前 OpenGL context 上的 Prism/Lumin 运行时。 */
public final class MinecraftGraphicsRuntime1211 implements AutoCloseable {
    private static MinecraftGraphicsRuntime1211 current;

    public record CreationConfig(Executor renderExecutor, Supplier<SurfaceMetrics> metricsSupplier,
                                 Supplier<RenderTarget> renderTargetSupplier) {
        public CreationConfig {
            Objects.requireNonNull(renderExecutor, "renderExecutor");
            Objects.requireNonNull(metricsSupplier, "metricsSupplier");
            Objects.requireNonNull(renderTargetSupplier, "renderTargetSupplier");
        }
    }

    private final CreationConfig config;
    private final OpenGlExternalContext externalContext;
    private final PRhiInstance instance;
    private final OpenGlExternalDevice device;
    private final PRhiQueue graphicsQueue;
    private final PRhiCommandPool commandPool;
    private final PRhiCommandBuffer commandBuffer;
    private final LuminGraphicsContext luminContext;
    private final Deque<AutoCloseable> retiredAfterFrame = new ArrayDeque<>();
    private BorrowedTarget target;
    private long nextFrameId;
    private long activeFrameId = -1;
    private long lastEndedFrameId = -1;
    private double projectionScale = Double.NaN;
    private boolean closed;

    private MinecraftGraphicsRuntime1211(CreationConfig config, OpenGlExternalContext externalContext,
            PRhiInstance instance, OpenGlExternalDevice device, PRhiQueue graphicsQueue,
            PRhiCommandPool commandPool, PRhiCommandBuffer commandBuffer, LuminGraphicsContext luminContext) {
        this.config = config;
        this.externalContext = externalContext;
        this.instance = instance;
        this.device = device;
        this.graphicsQueue = graphicsQueue;
        this.commandPool = commandPool;
        this.commandBuffer = commandBuffer;
        this.luminContext = luminContext;
    }

    public static synchronized MinecraftGraphicsRuntime1211 bindCurrent(CreationConfig config) {
        Objects.requireNonNull(config, "config");
        if (current != null) {
            current.requireAccess();
            return current;
        }
        RenderSystem.assertOnRenderThread();
        var capabilities = GL.getCapabilities();
        var invalidation = new PRhiInvalidationToken();
        var identity = new PRhiContextIdentity(
                Integer.toUnsignedLong(System.identityHashCode(capabilities)) + 1L,
                "minecraft-1.21.1-render-context");
        var context = new OpenGlExternalContext(capabilities, Thread.currentThread(), identity, invalidation,
                expected -> GL.getCapabilities() == capabilities && identity.equals(expected));
        current = create(config, context);
        return current;
    }

    public static synchronized MinecraftGraphicsRuntime1211 current() {
        if (current == null) throw new IllegalStateException("LuminGraphics-MC runtime is not bound");
        current.requireAccess();
        return current;
    }

    private static MinecraftGraphicsRuntime1211 create(CreationConfig config, OpenGlExternalContext context) {
        PRhiInstance instance = null;
        OpenGlExternalDevice device = null;
        PRhiCommandPool pool = null;
        PRhiCommandBuffer buffer = null;
        LuminGraphicsContext lumin = null;
        try {
            PRhiBackendProvider provider = context.capabilities().OpenGL46
                    ? new Gl46BackendProvider(context)
                    : new Gl41BackendProvider(context);
            instance = PrismRHI.createInstance(provider, PRhiInstanceCreateInfo.builder()
                    .applicationName("LuminGraphics-MC 1.21.1").build());
            device = (OpenGlExternalDevice) instance.createDevice(instance.enumeratePhysicalDevices().getFirst(),
                    PRhiDeviceCreateInfo.builder().debugName("minecraft-1.21.1")
                            .glStateBridge(GlStateManagerBridge1211.INSTANCE).build());
            PRhiQueue queue = device.queue(PRhiQueueType.GRAPHICS);
            pool = device.createCommandPool(new PRhiCommandPoolCreateInfo(PRhiQueueType.GRAPHICS, true, true));
            buffer = pool.allocateCommandBuffer(PRhiCommandBufferLevel.PRIMARY);
            OpenGlExternalDevice ownedDevice = device;
            MinecraftGraphicsRuntime1211[] owner = new MinecraftGraphicsRuntime1211[1];
            lumin = new LuminGraphicsContext(device,
                    new RenderThreadGate(context.ownerThread(), config.renderExecutor()), config.metricsSupplier(),
                    () -> owner[0].currentRenderTarget());
            MinecraftGraphicsRuntime1211 runtime = new MinecraftGraphicsRuntime1211(
                    config, context, instance, ownedDevice, queue, pool, buffer, lumin);
            owner[0] = runtime;
            return runtime;
        } catch (RuntimeException failure) {
            RuntimeException cleanup = GraphicsResourceCloser1211.closeReverse(
                    lumin, buffer, pool, device, instance,
                    () -> { context.invalidation().invalidate(); context.invalidation().close(); });
            if (cleanup != null) failure.addSuppressed(cleanup);
            throw new IllegalStateException("Failed to bind LuminGraphics-MC 1.21.1 runtime", failure);
        }
    }

    public synchronized void beginFrame() {
        requireAccess();
        if (activeFrameId >= 0) return;
        commandBuffer.reset();
        commandBuffer.begin();
        activeFrameId = nextFrameId++;
    }

    public synchronized void endFrame() {
        requireAccess();
        if (activeFrameId < 0) return;
        RuntimeException failure = null;
        try {
            commandBuffer.end();
            graphicsQueue.submit(PRhiSubmitInfo.of(commandBuffer));
        } catch (RuntimeException exception) {
            failure = exception;
        }
        failure = GraphicsResourceCloser1211.merge(failure, GraphicsResourceCloser1211.closeReverse(target));
        target = null;
        lastEndedFrameId = activeFrameId;
        activeFrameId = -1;
        failure = GraphicsResourceCloser1211.merge(failure,
                GraphicsResourceCloser1211.closeReverse(retiredAfterFrame));
        if (failure != null) throw failure;
    }

    public synchronized com.github.slmpc.lumingraphics.core.target.RenderTarget currentRenderTarget() {
        requireAccess();
        if (activeFrameId < 0) throw new IllegalStateException("No graphics frame is active");
        if (target == null) target = borrowTarget(config.renderTargetSupplier().get());
        return target.target();
    }

    public OpenGlExternalContext externalContext() { requireAccess(); return externalContext; }
    public OpenGlExternalDevice device() { requireAccess(); return device; }
    public PRhiCommandBuffer commandBuffer() {
        requireAccess();
        if (activeFrameId < 0) throw new IllegalStateException("No graphics frame is active");
        return commandBuffer;
    }
    public LuminGraphicsContext luminContext() { requireAccess(); return luminContext; }
    public boolean frameActive() { return activeFrameId >= 0; }
    public long activeFrameId() {
        requireAccess();
        if (activeFrameId < 0) throw new IllegalStateException("No graphics frame is active");
        return activeFrameId;
    }
    public long lastEndedFrameId() { requireAccess(); return lastEndedFrameId; }
    public boolean acceptingSubmissions() { return !closed; }

    public void setProjectionScale(double scale) {
        requireAccess();
        if (!Double.isFinite(scale) || scale <= 0.0) {
            throw new IllegalArgumentException("projection scale must be positive and finite");
        }
        projectionScale = scale;
    }

    public SurfaceMetrics projectionMetrics() {
        requireAccess();
        SurfaceMetrics framebuffer = luminContext.metrics();
        double scale = projectionScale;
        if (!Double.isFinite(scale) || scale <= 0.0) scale = framebuffer.scale();
        return new SurfaceMetrics(framebuffer.framebufferWidth(), framebuffer.framebufferHeight(), scale);
    }

    void retireAfterFrame(AutoCloseable resource) {
        requireAccess();
        retiredAfterFrame.addFirst(Objects.requireNonNull(resource, "resource"));
    }

    @Override public synchronized void close() {
        if (closed) return;
        requireAccess();
        RuntimeException failure = null;
        if (activeFrameId >= 0) {
            try { endFrame(); } catch (RuntimeException exception) { failure = exception; }
        }
        closed = true;
        failure = GraphicsResourceCloser1211.merge(failure,
                GraphicsResourceCloser1211.closeReverse(luminContext, commandBuffer, commandPool, device, instance,
                        () -> { externalContext.invalidation().invalidate(); externalContext.invalidation().close(); }));
        clearCurrent(this);
        if (failure != null) throw failure;
    }

    private BorrowedTarget borrowTarget(RenderTarget minecraftTarget) {
        if (minecraftTarget == null || minecraftTarget.width <= 0 || minecraftTarget.height <= 0
                || minecraftTarget.getColorTextureId() <= 0) {
            throw new IllegalStateException("Minecraft main render target is unavailable");
        }
        PRhiImage image = null;
        PRhiImageView view = null;
        try {
            PRhiImageCreateInfo info = PRhiImageCreateInfo.builder(
                            PRhiExtent3D.of2D(minecraftTarget.width, minecraftTarget.height))
                    .format(PRhiFormat.RGBA8_UNORM)
                    .usage(PRhiImageUsage.COLOR_ATTACHMENT).usage(PRhiImageUsage.SAMPLED)
                    .usage(PRhiImageUsage.TRANSFER_SRC).usage(PRhiImageUsage.TRANSFER_DST)
                    .memoryUsage(PRhiMemoryUsage.GPU_ONLY)
                    .build();
            image = device.adoptImage(new OpenGlImageAdoption(
                    new PRhiNativeObject(OpenGlNativeObjectTypes.TEXTURE, minecraftTarget.getColorTextureId()),
                    info, PRhiOwnership.BORROWED, device.contextIdentity(), externalContext.invalidation()));
            view = device.adoptImageView(new OpenGlImageViewAdoption(PRhiImageViewCreateInfo.of(image)));
            return new BorrowedTarget(new com.github.slmpc.lumingraphics.core.target.RenderTarget(
                    view, Optional.empty(), minecraftTarget.width, minecraftTarget.height, device.contextIdentity()),
                    view, image);
        } catch (RuntimeException failure) {
            RuntimeException cleanup = GraphicsResourceCloser1211.closeReverse(view, image);
            if (cleanup != null) failure.addSuppressed(cleanup);
            throw failure;
        }
    }

    private void requireAccess() {
        if (closed) throw new IllegalStateException("Minecraft graphics runtime is closed");
        externalContext.requireCurrent();
    }

    private static synchronized void clearCurrent(MinecraftGraphicsRuntime1211 runtime) {
        if (current == runtime) current = null;
    }

    private record BorrowedTarget(com.github.slmpc.lumingraphics.core.target.RenderTarget target,
                                  PRhiImageView view, PRhiImage image) implements AutoCloseable {
        @Override public void close() {
            RuntimeException failure = GraphicsResourceCloser1211.closeReverse(view, image);
            if (failure != null) throw failure;
        }
    }
}
