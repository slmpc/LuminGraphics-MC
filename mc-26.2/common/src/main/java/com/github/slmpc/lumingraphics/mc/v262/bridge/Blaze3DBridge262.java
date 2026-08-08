package com.github.slmpc.lumingraphics.mc.v262.bridge;

import com.github.slmpc.lumingraphics.mc.bridge.BridgeCapability;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeCompatibilityAudit;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeContextIdentity;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeInvalidationToken;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeLease;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeResult;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeUnsupportedDetail;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeUnsupportedReason;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeWrongContextException;
import com.github.slmpc.lumingraphics.mc.v262.access.GlObjectFactory262;
import com.github.slmpc.lumingraphics.mc.v262.access.GlShaderModuleAccess262;
import com.github.slmpc.prismrhi.backend.BackendApi;
import com.github.slmpc.prismrhi.PRhiInvalidArgumentException;
import com.github.slmpc.prismrhi.PRhiInvalidStateException;
import com.github.slmpc.prismrhi.PRhiResourceClosedException;
import com.github.slmpc.prismrhi.PRhiResourceInvalidatedException;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlBufferAdoption;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlAdoptedResource;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalDevice;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlImageAdoption;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlImageViewAdoption;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlNativeObjectTypes;
import com.github.slmpc.prismrhi.context.PRhiContextIdentity;
import com.github.slmpc.prismrhi.context.PRhiInvalidationToken;
import com.github.slmpc.prismrhi.format.PRhiExtent3D;
import com.github.slmpc.prismrhi.pipeline.PRhiGraphicsPipeline;
import com.github.slmpc.prismrhi.resource.PRhiBuffer;
import com.github.slmpc.prismrhi.resource.PRhiBufferCreateInfo;
import com.github.slmpc.prismrhi.resource.PRhiImage;
import com.github.slmpc.prismrhi.resource.PRhiImageCreateInfo;
import com.github.slmpc.prismrhi.resource.PRhiImageView;
import com.github.slmpc.prismrhi.resource.PRhiImageViewCreateInfo;
import com.github.slmpc.prismrhi.resource.PRhiNativeObject;
import com.github.slmpc.prismrhi.resource.PRhiOwnership;
import com.github.slmpc.prismrhi.resource.PRhiSampler;
import com.github.slmpc.prismrhi.shader.PRhiShader;
import com.github.slmpc.prismrhi.shader.PRhiShaderDesc;
import com.github.slmpc.prismrhi.shader.PRhiShaderNativeObjectTypes;
import com.github.slmpc.prismrhi.shader.PRhiShaderStage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.FrameBufferCache;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

public final class Blaze3DBridge262 {
    public static final String VERSION = "26.2";
    private final OpenGlExternalDevice rhiDevice;
    private final BridgeContextIdentity bridgeContext;
    private final BridgeInvalidationToken bridgeToken;
    private final PRhiContextIdentity rhiContext;
    private final PRhiInvalidationToken rhiToken;
    private final Object glCapabilities;
    private final Supplier<?> currentGlCapabilities;
    private final Supplier<BridgeContextIdentity> currentBridgeContext;
    private final GlObjectFactory262 glFactory;

    public Blaze3DBridge262(OpenGlExternalDevice rhiDevice, BridgeContextIdentity bridgeContext,
            BridgeInvalidationToken bridgeToken, PRhiContextIdentity rhiContext,
            PRhiInvalidationToken rhiToken, Object glCapabilities, Supplier<?> currentGlCapabilities,
            Supplier<BridgeContextIdentity> currentBridgeContext, GlObjectFactory262 glFactory) {
        this.rhiDevice = Objects.requireNonNull(rhiDevice, "rhiDevice");
        this.bridgeContext = Objects.requireNonNull(bridgeContext, "bridgeContext");
        this.bridgeToken = Objects.requireNonNull(bridgeToken, "bridgeToken");
        this.rhiContext = Objects.requireNonNull(rhiContext, "rhiContext");
        this.rhiToken = Objects.requireNonNull(rhiToken, "rhiToken");
        this.glCapabilities = Objects.requireNonNull(glCapabilities, "glCapabilities");
        this.currentGlCapabilities = Objects.requireNonNull(currentGlCapabilities, "currentGlCapabilities");
        this.currentBridgeContext = Objects.requireNonNull(currentBridgeContext, "currentBridgeContext");
        this.glFactory = Objects.requireNonNull(glFactory, "glFactory");
        if (bridgeToken.context() != bridgeContext) throw new IllegalArgumentException("bridge token context mismatch");
        rhiContext.requireSameContext(rhiDevice.contextIdentity());
        rhiToken.requireValid();
        if (rhiDevice.api() != BackendApi.OPENGL_41 && rhiDevice.api() != BackendApi.OPENGL_46) {
            throw new IllegalArgumentException("Minecraft 26.2 zero-copy bridge requires an OpenGL Prism device");
        }
    }

    public BridgeResult<BridgeLease<PRhiImage>> textureToLumin(GpuTexture source) {
        if (!(source instanceof GlTexture texture)) return subtype("texture", source);
        BridgeCompatibilityAudit audit = minecraftAudit("texture", "GlTexture", texture.glId(), () -> !texture.isClosed());
        if (!audit.isCompatible()) return unsupported(audit);
        try {
            PRhiImageCreateInfo info = new PRhiImageCreateInfo(
                    new PRhiExtent3D(texture.getWidth(0), texture.getHeight(0), texture.getDepthOrLayers()),
                    BridgeTranslations262.format(texture.getFormat()),
                    BridgeTranslations262.imageUsage(texture.usage(), texture.getFormat()),
                    com.github.slmpc.prismrhi.resource.PRhiMemoryUsage.GPU_ONLY);
            PRhiImage adopted = rhiDevice.adoptImage(new OpenGlImageAdoption(
                    new PRhiNativeObject(OpenGlNativeObjectTypes.TEXTURE, texture.glId()), info,
                    PRhiOwnership.BORROWED, rhiContext, rhiToken));
            return success(adopted);
        } catch (IllegalArgumentException | PRhiResourceClosedException | PRhiResourceInvalidatedException error) {
            return drift("texture metadata/adoption failed: " + error.getMessage());
        }
    }

    public BridgeResult<BridgeLease<RhiTextureView262>> textureViewToLumin(GpuTextureView source) {
        if (!(source instanceof GlTextureView view)) return subtype("texture-view", source);
        if (view.isClosed() || view.texture().isClosed()) return state(BridgeUnsupportedReason.CLOSED, "texture view or parent is closed");
        BridgeResult<BridgeLease<PRhiImage>> imageResult = textureToLumin(view.texture());
        if (imageResult instanceof BridgeResult.Unsupported<BridgeLease<PRhiImage>> unsupported) {
            return BridgeResult.unsupported(unsupported.detail());
        }
        PRhiImage image = imageResult.orElseThrow().access(bridgeContext);
        PRhiImageViewCreateInfo info = PRhiImageViewCreateInfo.builder(image)
                .format(BridgeTranslations262.format(view.texture().getFormat()))
                .mipRange(view.baseMipLevel(), view.mipLevels()).build();
        try {
            PRhiImageView rhiView = rhiDevice.adoptImageView(new OpenGlImageViewAdoption(info));
            RhiTextureView262 value = new RhiTextureView262(image, rhiView);
            return BridgeResult.success(BridgeLease.owned(value, bridgeContext, bridgeToken,
                    this::requireCloseContext, value::close));
        } catch (PRhiInvalidArgumentException | PRhiResourceClosedException | PRhiResourceInvalidatedException error) {
            image.close();
            return rhiFailure("texture-view adoption", error);
        }
    }

    public BridgeResult<BridgeLease<RhiBufferSlice262>> bufferToLumin(GpuBufferSlice source) {
        if (!(source.buffer() instanceof GlBuffer buffer)) return subtype("buffer", source.buffer());
        BridgeCompatibilityAudit audit = minecraftAudit("buffer", "GlBuffer", buffer.handle(), () -> !buffer.isClosed());
        if (!audit.isCompatible()) return unsupported(audit);
        try {
            PRhiBufferCreateInfo info = new PRhiBufferCreateInfo(buffer.size(),
                    BridgeTranslations262.bufferUsage(buffer.usage()), BridgeTranslations262.memoryUsage(buffer.usage()));
            PRhiBuffer adopted = rhiDevice.adoptBuffer(new OpenGlBufferAdoption(
                    new PRhiNativeObject(OpenGlNativeObjectTypes.BUFFER, buffer.handle()), info,
                    PRhiOwnership.BORROWED, rhiContext, rhiToken));
            return success(new RhiBufferSlice262(adopted, source.offset(), source.length()));
        } catch (IllegalArgumentException | PRhiResourceClosedException | PRhiResourceInvalidatedException error) {
            return drift("buffer metadata/adoption failed: " + error.getMessage());
        }
    }

    public BridgeResult<BridgeLease<PRhiShader>> shaderToLumin(GlShaderModule source) {
        if (source.getShaderId() <= 0) return state(BridgeUnsupportedReason.CLOSED, "shader is closed or invalid");
        if (!(source instanceof GlShaderModuleAccess262 access)) return drift("GlShaderModuleAccess262 mixin is absent");
        BridgeCompatibilityAudit audit = minecraftAudit("shader-module", "GlShaderModule",
                source.getShaderId(), () -> source.getShaderId() > 0);
        if (!audit.isCompatible()) return unsupported(audit);
        PRhiShaderStage stage = access.luminGraphics$type262() == ShaderType.VERTEX
                ? PRhiShaderStage.VERTEX : PRhiShaderStage.FRAGMENT;
        try {
            PRhiShader adopted = rhiDevice.adoptShader(new PRhiShaderDesc(stage, "main", source.getDebugLabel()),
                    new PRhiNativeObject(PRhiShaderNativeObjectTypes.OPENGL_SHADER_OBJECT, source.getShaderId()),
                    PRhiOwnership.BORROWED, rhiContext, rhiToken);
            return success(adopted);
        } catch (PRhiInvalidArgumentException | PRhiResourceClosedException | PRhiResourceInvalidatedException error) {
            return rhiFailure("shader adoption", error);
        }
    }

    public BridgeResult<BridgeLease<GlTexture>> textureToMinecraft(PRhiImage source, BridgeCapability capability,
            TextureMetadata262 metadata, FrameBufferCache frameBufferCache) {
        BridgeCompatibilityAudit audit = capability.audit("texture", "PRhiImage", "opengl", bridgeContext);
        if (!audit.isCompatible()) return unsupported(audit);
        final PRhiNativeObject nativeObject;
        try {
            nativeObject = source.getNativeObject(OpenGlNativeObjectTypes.TEXTURE).orElse(null);
        } catch (PRhiInvalidArgumentException | PRhiResourceClosedException | PRhiResourceInvalidatedException error) {
            return rhiFailure("texture native access", error);
        }
        if (nativeObject == null) return state(BridgeUnsupportedReason.NO_NATIVE_HANDLE, "RHI image has no OpenGL texture");
        if (!metadata.matches(source)) return drift("RHI image metadata does not match requested Minecraft wrapper");
        final int handle;
        try {
            handle = Math.toIntExact(nativeObject.value());
        } catch (ArithmeticException error) {
            return nativeHandleOverflow("texture", nativeObject.value());
        }
        try {
            GlTexture texture = glFactory.texture(metadata.usage(), metadata.label(),
                    BridgeTranslations262.format(source.format()), source.extent().width(), source.extent().height(),
                    source.extent().depth(), metadata.mipLevels(), handle, frameBufferCache);
            return success(texture);
        } catch (IllegalArgumentException | IllegalStateException | AssertionError error) {
            return factoryFailure("texture", error);
        }
    }

    public BridgeResult<BridgeLease<GlTextureView>> textureViewToMinecraft(PRhiImageView source,
            BridgeCapability capability, GlTexture parent, int baseMipLevel, int mipLevels,
            FrameBufferCache frameBufferCache) {
        if (source == null) return subtype("texture-view", null);
        BridgeCompatibilityAudit audit = capability.audit("texture", "PRhiImageView", "opengl", bridgeContext);
        if (!audit.isCompatible()) return unsupported(audit);
        if (parent == null) return state(BridgeUnsupportedReason.VIEW_REQUIRES_PARENT, "parent texture is absent");
        if (parent.isClosed()) return state(BridgeUnsupportedReason.VIEW_REQUIRES_PARENT, "parent texture is closed");
        final PRhiNativeObject nativeObject;
        try {
            PRhiImage image = Objects.requireNonNull(source.image(), "texture view backing image");
            if (source.api() != rhiDevice.api() || image.api() != rhiDevice.api()) {
                return state(BridgeUnsupportedReason.BACKEND_MISMATCH,
                        "texture view and backing image must use the bridge OpenGL backend");
            }
            if (!(image instanceof OpenGlAdoptedResource adopted)) {
                return subtype("texture-view backing image", image);
            }
            if (!rhiContext.equals(adopted.contextIdentity())) {
                return state(BridgeUnsupportedReason.CONTEXT_MISMATCH,
                        "texture view backing image belongs to a different RHI context");
            }
            if (adopted.invalidationToken() != rhiToken || !rhiToken.isValid()) {
                return state(BridgeUnsupportedReason.TOKEN_INVALIDATED,
                        "texture view backing image token is not the bridge token or is invalid");
            }
            nativeObject = image.getNativeObject(OpenGlNativeObjectTypes.TEXTURE).orElse(null);
            if (nativeObject != null) OpenGlNativeObjectTypes.TEXTURE.requireCompatible(nativeObject.type());
            if (!source.format().equals(image.format())) {
                return state(BridgeUnsupportedReason.TYPE_MISMATCH,
                        "texture view format differs from its backing image");
            }
        } catch (PRhiResourceClosedException | PRhiResourceInvalidatedException error) {
            return rhiFailure("texture-view native access", error);
        } catch (PRhiInvalidStateException error) {
            return state(BridgeUnsupportedReason.CONTEXT_MISMATCH,
                    "texture-view native access failed: " + error.getMessage());
        } catch (PRhiInvalidArgumentException error) {
            return state(BridgeUnsupportedReason.TYPE_MISMATCH,
                    "texture-view native access failed: " + error.getMessage());
        }
        if (nativeObject == null) {
            return state(BridgeUnsupportedReason.NO_NATIVE_HANDLE,
                    "texture view backing image has no OpenGL texture");
        }
        final int handle;
        try {
            handle = Math.toIntExact(nativeObject.value());
        } catch (ArithmeticException error) {
            return nativeHandleOverflow("texture-view", nativeObject.value());
        }
        if (handle != parent.glId()) {
            return state(BridgeUnsupportedReason.VIEW_REQUIRES_PARENT,
                    "texture view backing image does not match the supplied Minecraft parent");
        }
        try {
            if (BridgeTranslations262.format(source.format()) != parent.getFormat()) {
                return state(BridgeUnsupportedReason.VIEW_REQUIRES_PARENT,
                        "texture view format does not match the supplied Minecraft parent");
            }
        } catch (IllegalArgumentException error) {
            return state(BridgeUnsupportedReason.TYPE_MISMATCH, error.getMessage());
        }
        if (baseMipLevel < 0 || mipLevels <= 0 || baseMipLevel + mipLevels > parent.getMipLevels()) {
            return state(BridgeUnsupportedReason.MC_SHAPE_CHANGED, "texture view mip range is invalid");
        }
        try {
            return success(glFactory.textureView(parent, baseMipLevel, mipLevels, frameBufferCache));
        } catch (IllegalArgumentException | IllegalStateException | AssertionError error) {
            return factoryFailure("texture-view", error);
        }
    }

    public BridgeResult<BridgeLease<GlBuffer.Direct>> bufferToMinecraft(PRhiBuffer source,
            BridgeCapability capability, int usage, DirectStateAccess dsa, boolean canPersistentMap) {
        BridgeCompatibilityAudit audit = capability.audit("buffer", "PRhiBuffer", "opengl", bridgeContext);
        if (!audit.isCompatible()) return unsupported(audit);
        try {
            BridgeTranslations262.bufferUsage(usage);
            BridgeTranslations262.memoryUsage(usage);
        } catch (IllegalArgumentException error) {
            return drift(error.getMessage());
        }
        final PRhiNativeObject nativeObject;
        try {
            nativeObject = source.getNativeObject(OpenGlNativeObjectTypes.BUFFER).orElse(null);
        } catch (PRhiInvalidArgumentException | PRhiResourceClosedException | PRhiResourceInvalidatedException error) {
            return rhiFailure("buffer native access", error);
        }
        if (nativeObject == null) return state(BridgeUnsupportedReason.NO_NATIVE_HANDLE, "RHI buffer has no OpenGL buffer");
        final int handle;
        try {
            handle = Math.toIntExact(nativeObject.value());
        } catch (ArithmeticException error) {
            return nativeHandleOverflow("buffer", nativeObject.value());
        }
        try {
            return success(glFactory.buffer(dsa, usage, source.size(), handle, canPersistentMap));
        } catch (IllegalArgumentException | IllegalStateException | AssertionError error) {
            return factoryFailure("buffer", error);
        }
    }

    public BridgeResult<BridgeLease<GlShaderModule>> shaderToMinecraft(PRhiShader source,
            BridgeCapability capability, Identifier id) {
        BridgeCompatibilityAudit audit = capability.audit("shader-module", "PRhiShader", "opengl", bridgeContext);
        if (!audit.isCompatible()) return unsupported(audit);
        final PRhiNativeObject nativeObject;
        try {
            nativeObject = source.getNativeObject(PRhiShaderNativeObjectTypes.OPENGL_SHADER_OBJECT).orElse(null);
        } catch (PRhiInvalidArgumentException | PRhiResourceClosedException | PRhiResourceInvalidatedException error) {
            return rhiFailure("shader native access", error);
        }
        if (nativeObject == null) return state(BridgeUnsupportedReason.NO_NATIVE_HANDLE, "RHI shader has no OpenGL shader object");
        if (source.desc().stage() == PRhiShaderStage.COMPUTE) {
            return state(BridgeUnsupportedReason.TYPE_MISMATCH,
                    "Minecraft 26.2 has no compute GlShaderModule role");
        }
        ShaderType type = source.desc().stage() == PRhiShaderStage.VERTEX ? ShaderType.VERTEX : ShaderType.FRAGMENT;
        final int handle;
        try {
            handle = Math.toIntExact(nativeObject.value());
        } catch (ArithmeticException error) {
            return nativeHandleOverflow("shader-module", nativeObject.value());
        }
        try {
            return success(glFactory.shader(handle, id, type));
        } catch (IllegalArgumentException | IllegalStateException | AssertionError error) {
            return factoryFailure("shader-module", error);
        }
    }

    public BridgeResult<BridgeLease<PRhiSampler>> samplerToLumin(GpuSampler source) {
        return owned(() -> rhiDevice.createSampler(BridgeTranslations262.sampler(source)));
    }

    public BridgeResult<BridgeLease<GpuSampler>> samplerToMinecraft(PRhiSampler source,
            Function<PRhiSampler, GpuSampler> rebuilder) {
        try {
            GpuSampler value = Objects.requireNonNull(rebuilder.apply(source), "rebuilder returned null");
            return BridgeResult.success(BridgeLease.owned(value, bridgeContext, bridgeToken,
                    this::requireCloseContext, value::close));
        } catch (IllegalArgumentException | IllegalStateException | AssertionError error) {
            return drift("sampler rebuild failed: " + error.getMessage());
        }
    }

    public BridgeResult<BridgeLease<PRhiGraphicsPipeline>> pipelineToLumin(RenderPipeline source,
            Function<PipelineMetadata262, PRhiGraphicsPipeline> rebuilder) {
        return owned(() -> rebuilder.apply(PipelineMetadata262.from(source)));
    }

    public BridgeResult<BridgeLease<RenderPipeline>> pipelineToMinecraft(PRhiGraphicsPipeline source,
            Function<PRhiGraphicsPipeline, RenderPipeline> rebuilder) {
        try {
            RenderPipeline value = Objects.requireNonNull(rebuilder.apply(source), "rebuilder returned null");
            return BridgeResult.success(BridgeLease.owned(value, bridgeContext, bridgeToken,
                    this::requireCloseContext, () -> { }));
        } catch (IllegalArgumentException | IllegalStateException | AssertionError error) {
            return drift("rebuild failed: " + error.getMessage());
        }
    }

    public BridgeResult<BridgeLease<CommandEncoderAdapter262>> encoderToLumin(CommandEncoder encoder) {
        return success(new CommandEncoderAdapter262(encoder));
    }

    public BridgeResult<BridgeLease<CommandEncoder>> encoderToMinecraft(CommandEncoderAdapter262 adapter) {
        return success(adapter.encoder());
    }

    public BridgeResult<BridgeLease<RenderPassAdapter262>> renderPassToLumin(RenderPass pass) {
        return success(new RenderPassAdapter262(pass));
    }

    public BridgeResult<BridgeLease<RenderPass>> renderPassToMinecraft(RenderPassAdapter262 adapter) {
        return success(adapter.pass());
    }

    private BridgeCompatibilityAudit minecraftAudit(String type, String diagnostic, long handle,
            java.util.function.BooleanSupplier live) {
        return BridgeCapability.openGl(type, diagnostic, handle, bridgeContext, bridgeToken, live,
                glCapabilities, currentGlCapabilities, currentBridgeContext).audit(type, diagnostic, "opengl", bridgeContext);
    }

    public static BridgeCompatibilityAudit audit262(BridgeCapability capability, String type,
            String diagnostic, BridgeContextIdentity context) {
        return Objects.requireNonNull(capability, "capability").audit(type, diagnostic, "opengl", context);
    }

    private <T> BridgeResult<BridgeLease<T>> success(T value) {
        return BridgeResult.success(BridgeLease.borrowed(value, bridgeContext, bridgeToken));
    }

    private <T extends AutoCloseable> BridgeResult<BridgeLease<T>> owned(Supplier<T> supplier) {
        try {
            T value = Objects.requireNonNull(supplier.get(), "rebuilder returned null");
            return BridgeResult.success(BridgeLease.owned(value, bridgeContext, bridgeToken,
                    this::requireCloseContext, value::close));
        } catch (IllegalArgumentException | IllegalStateException | AssertionError error) {
            return drift("rebuild failed: " + error.getMessage());
        }
    }

    private void requireCloseContext() {
        BridgeContextIdentity currentContext = currentBridgeContext.get();
        if (currentContext != bridgeContext) {
            throw new BridgeWrongContextException(bridgeContext.diagnosticId(),
                    currentContext == null ? "no bridge context" : currentContext.diagnosticId());
        }
        if (currentGlCapabilities.get() != glCapabilities) {
            throw new BridgeWrongContextException(bridgeContext.diagnosticId(), "different OpenGL capabilities");
        }
        rhiToken.requireValid();
    }

    private static <T> BridgeResult<T> unsupported(BridgeCompatibilityAudit audit) {
        return BridgeResult.unsupported(audit.detail());
    }

    private static <T> BridgeResult<T> subtype(String role, Object actual) {
        return BridgeResult.unsupported(new BridgeUnsupportedDetail.Mismatch(BridgeUnsupportedReason.TYPE_MISMATCH,
                role, "26.2 OpenGL subtype", actual == null ? "null" : actual.getClass().getName()));
    }

    private static <T> BridgeResult<T> drift(String message) {
        return state(BridgeUnsupportedReason.MC_SHAPE_CHANGED, message);
    }

    private static <T> BridgeResult<T> nativeHandleOverflow(String type, long handle) {
        return BridgeResult.unsupported(new BridgeUnsupportedDetail.NativeHandle(
                BridgeUnsupportedReason.NO_NATIVE_HANDLE, type, handle));
    }

    private static <T> BridgeResult<T> factoryFailure(String role, Throwable error) {
        return drift(role + " 26.2 wrapper factory failed: " + error.getMessage());
    }

    private static <T> BridgeResult<T> rhiFailure(String operation, RuntimeException error) {
        if (error instanceof PRhiResourceClosedException) {
            return state(BridgeUnsupportedReason.CLOSED, operation + " failed: " + error.getMessage());
        }
        if (error instanceof PRhiResourceInvalidatedException) {
            return state(BridgeUnsupportedReason.TOKEN_INVALIDATED, operation + " failed: " + error.getMessage());
        }
        return state(BridgeUnsupportedReason.TYPE_MISMATCH, operation + " failed: " + error.getMessage());
    }

    private static <T> BridgeResult<T> state(BridgeUnsupportedReason reason, String message) {
        return BridgeResult.unsupported(new BridgeUnsupportedDetail.State(reason,
                message == null || message.isBlank() ? "bridge operation failed" : message));
    }

    public record RhiTextureView262(PRhiImage image, PRhiImageView view) implements AutoCloseable {
        public RhiTextureView262 { Objects.requireNonNull(image); Objects.requireNonNull(view); }
        @Override public void close() {
            try {
                view.close();
            } finally {
                image.close();
            }
        }
    }

    public record RhiBufferSlice262(PRhiBuffer buffer, long offset, long length) {
        public RhiBufferSlice262 {
            Objects.requireNonNull(buffer);
            if (offset < 0 || length < 0 || offset + length > buffer.size()) {
                throw new IllegalArgumentException("buffer slice is out of bounds");
            }
        }
    }

    public record TextureMetadata262(int usage, String label, int mipLevels) {
        public TextureMetadata262 {
            Objects.requireNonNull(label, "label");
            if (mipLevels <= 0) throw new IllegalArgumentException("mipLevels must be positive");
        }
        boolean matches(PRhiImage image) {
            try {
                BridgeTranslations262.imageUsage(usage, BridgeTranslations262.format(image.format()));
                return image.extent().width() > 0 && image.extent().height() > 0 && image.extent().depth() > 0;
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
    }
}
