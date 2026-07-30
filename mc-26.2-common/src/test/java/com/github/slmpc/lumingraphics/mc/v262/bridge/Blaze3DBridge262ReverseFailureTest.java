package com.github.slmpc.lumingraphics.mc.v262.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.slmpc.lumingraphics.mc.bridge.BridgeCapability;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeContextIdentity;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeLease;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeOwnership;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeResult;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeUnsupportedReason;
import com.github.slmpc.lumingraphics.mc.v262.access.GlObjectFactory262;
import com.github.slmpc.prismrhi.RhiResourceClosedException;
import com.github.slmpc.prismrhi.backend.BackendApi;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlAdoptedResource;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlBufferAdoption;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalContext;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalDevice;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlImageAdoption;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlImageViewAdoption;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlNativeObjectTypes;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlPipelineAdoption;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlSamplerAdoption;
import com.github.slmpc.prismrhi.command.RhiCommandPool;
import com.github.slmpc.prismrhi.command.RhiCommandPoolCreateInfo;
import com.github.slmpc.prismrhi.context.RhiContextIdentity;
import com.github.slmpc.prismrhi.context.RhiInvalidationToken;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSet;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSetAllocateInfo;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSetLayout;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSetLayoutCreateInfo;
import com.github.slmpc.prismrhi.format.RhiExtent3D;
import com.github.slmpc.prismrhi.format.RhiFormat;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipeline;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipelineCreateInfo;
import com.github.slmpc.prismrhi.queue.RhiQueue;
import com.github.slmpc.prismrhi.queue.RhiQueueType;
import com.github.slmpc.prismrhi.resource.RhiBuffer;
import com.github.slmpc.prismrhi.resource.RhiBufferCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiImage;
import com.github.slmpc.prismrhi.resource.RhiImageAspect;
import com.github.slmpc.prismrhi.resource.RhiImageCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiImageView;
import com.github.slmpc.prismrhi.resource.RhiImageViewCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiNativeObject;
import com.github.slmpc.prismrhi.resource.RhiNativeObjectType;
import com.github.slmpc.prismrhi.resource.RhiOwnership;
import com.github.slmpc.prismrhi.resource.RhiSampler;
import com.github.slmpc.prismrhi.resource.RhiSamplerCreateInfo;
import com.github.slmpc.prismrhi.shader.RhiShader;
import com.github.slmpc.prismrhi.shader.RhiShaderBinary;
import com.github.slmpc.prismrhi.shader.RhiShaderBinaryFormat;
import com.github.slmpc.prismrhi.shader.RhiShaderDesc;
import com.github.slmpc.prismrhi.shader.RhiShaderStage;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.FrameBufferCache;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.shaders.ShaderType;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class Blaze3DBridge262ReverseFailureTest {
    @Test void textureHandleOverflowIsTyped() {
        Fixture fixture = new Fixture(new ThrowingFactory());
        BridgeResult<?> result = fixture.bridge.textureToMinecraft(
                new Image(Long.MAX_VALUE), fixture.capability("texture", "RhiImage", Long.MAX_VALUE),
                new Blaze3DBridge262.TextureMetadata262(4, "overflow", 1), new FrameBufferCache());
        assertUnsupported(result, BridgeUnsupportedReason.NO_NATIVE_HANDLE);
    }

    @Test void textureFactoryFailureIsTyped() {
        Fixture fixture = new Fixture(new ThrowingFactory());
        BridgeResult<?> result = fixture.bridge.textureToMinecraft(
                new Image(7), fixture.capability("texture", "RhiImage", 7),
                new Blaze3DBridge262.TextureMetadata262(4, "factory", 1), new FrameBufferCache());
        assertUnsupported(result, BridgeUnsupportedReason.MC_SHAPE_CHANGED);
    }

    @Test void bufferFactoryFailureIsTyped() {
        Fixture fixture = new Fixture(new ThrowingFactory());
        BridgeResult<?> result = fixture.bridge.bufferToMinecraft(
                new Buffer(11), fixture.capability("buffer", "RhiBuffer", 11), 32, null, false);
        assertUnsupported(result, BridgeUnsupportedReason.MC_SHAPE_CHANGED);
    }

    @Test void bufferHandleOverflowIsTyped() {
        Fixture fixture = new Fixture(new ThrowingFactory());
        BridgeResult<?> result = fixture.bridge.bufferToMinecraft(
                new Buffer(Long.MAX_VALUE), fixture.capability("buffer", "RhiBuffer", Long.MAX_VALUE),
                32, null, false);
        assertUnsupported(result, BridgeUnsupportedReason.NO_NATIVE_HANDLE);
    }

    @Test void shaderFactoryFailureIsTyped() {
        Fixture fixture = new Fixture(new ThrowingFactory());
        BridgeResult<?> result = fixture.bridge.shaderToMinecraft(
                new Shader(13), fixture.capability("shader-module", "RhiShader", 13),
                Identifier.withDefaultNamespace("test"));
        assertUnsupported(result, BridgeUnsupportedReason.MC_SHAPE_CHANGED);
    }

    @Test void shaderHandleOverflowIsTyped() {
        Fixture fixture = new Fixture(new ThrowingFactory());
        BridgeResult<?> result = fixture.bridge.shaderToMinecraft(
                new Shader(Long.MAX_VALUE), fixture.capability("shader-module", "RhiShader", Long.MAX_VALUE),
                Identifier.withDefaultNamespace("overflow"));
        assertUnsupported(result, BridgeUnsupportedReason.NO_NATIVE_HANDLE);
    }

    @Test void textureViewFactoryFailureIsTyped() {
        Fixture fixture = new Fixture(new ThrowingFactory());
        GlTexture parent = new TestTexture();
        BridgeResult<?> result = fixture.bridge.textureViewToMinecraft(fixture.view(3),
                fixture.capability("texture", "RhiImageView", 3), parent, 0, 1, new FrameBufferCache());
        assertUnsupported(result, BridgeUnsupportedReason.MC_SHAPE_CHANGED);
    }

    @Test void validTextureViewUsesExactFactoryArgumentsAndBorrowedLease() {
        TestTexture parent = new TestTexture();
        TestTextureView factoryView = new TestTextureView(parent);
        RecordingFactory factory = new RecordingFactory(factoryView);
        Fixture fixture = new Fixture(factory);
        ViewImage backingImage = fixture.image(3);
        RhiImageView source = new View(backingImage);
        FrameBufferCache frameBufferCache = new FrameBufferCache();

        BridgeResult<BridgeLease<GlTextureView>> result = fixture.bridge.textureViewToMinecraft(source,
                fixture.capability("texture", "RhiImageView", 3), parent, 0, 1, frameBufferCache);

        assertInstanceOf(BridgeResult.Success.class, result);
        BridgeLease<GlTextureView> lease = result.orElseThrow();
        assertSame(factoryView, lease.access(fixture.bridgeContext));
        assertEquals(BridgeOwnership.BORROWED, lease.ownership());
        assertSame(parent, factory.parent);
        assertEquals(0, factory.baseMipLevel);
        assertEquals(1, factory.mipLevels);
        assertSame(frameBufferCache, factory.frameBufferCache);

        lease.close();
        assertTrue(lease.isClosed());
        assertFalse(factoryView.isClosed());
        assertFalse(parent.isClosed());
        assertFalse(backingImage.isClosed());
        factoryView.close();
    }

    @Test void nullTextureViewIsTyped() {
        Fixture fixture = new Fixture(new ThrowingFactory());
        BridgeResult<?> result = fixture.bridge.textureViewToMinecraft(null,
                fixture.capability("texture", "RhiImageView", 3), new TestTexture(), 0, 1,
                new FrameBufferCache());
        assertUnsupported(result, BridgeUnsupportedReason.TYPE_MISMATCH);
    }

    @Test void closedTextureViewBackingImageIsTyped() {
        Fixture fixture = new Fixture(new ThrowingFactory());
        ViewImage image = fixture.image(3);
        image.close();
        BridgeResult<?> result = fixture.bridge.textureViewToMinecraft(new View(image),
                fixture.capability("texture", "RhiImageView", 3), new TestTexture(), 0, 1,
                new FrameBufferCache());
        assertUnsupported(result, BridgeUnsupportedReason.CLOSED);
    }

    @Test void staleTextureViewTokenIsTyped() {
        Fixture fixture = new Fixture(new ThrowingFactory());
        RhiInvalidationToken staleToken = new RhiInvalidationToken();
        staleToken.invalidate();
        BridgeResult<?> result = fixture.bridge.textureViewToMinecraft(
                new View(new ViewImage(3, fixture.rhiContext, staleToken, OpenGlNativeObjectTypes.TEXTURE)),
                fixture.capability("texture", "RhiImageView", 3), new TestTexture(), 0, 1,
                new FrameBufferCache());
        assertUnsupported(result, BridgeUnsupportedReason.TOKEN_INVALIDATED);
    }

    @Test void wrongTextureViewContextIsTyped() {
        Fixture fixture = new Fixture(new ThrowingFactory());
        BridgeResult<?> result = fixture.bridge.textureViewToMinecraft(
                new View(new ViewImage(3, new RhiContextIdentity(99, "other"), fixture.rhiToken,
                        OpenGlNativeObjectTypes.TEXTURE)),
                fixture.capability("texture", "RhiImageView", 3), new TestTexture(), 0, 1,
                new FrameBufferCache());
        assertUnsupported(result, BridgeUnsupportedReason.CONTEXT_MISMATCH);
    }

    @Test void wrongTextureViewNativeTypeIsTyped() {
        Fixture fixture = new Fixture(new ThrowingFactory());
        BridgeResult<?> result = fixture.bridge.textureViewToMinecraft(
                new View(new ViewImage(3, fixture.rhiContext, fixture.rhiToken, OpenGlNativeObjectTypes.BUFFER)),
                fixture.capability("texture", "RhiImageView", 3), new TestTexture(), 0, 1,
                new FrameBufferCache());
        assertUnsupported(result, BridgeUnsupportedReason.TYPE_MISMATCH);
    }

    @Test void textureViewMustMatchMinecraftParentHandle() {
        Fixture fixture = new Fixture(new ThrowingFactory());
        BridgeResult<?> result = fixture.bridge.textureViewToMinecraft(fixture.view(4),
                fixture.capability("texture", "RhiImageView", 4), new TestTexture(), 0, 1,
                new FrameBufferCache());
        assertUnsupported(result, BridgeUnsupportedReason.VIEW_REQUIRES_PARENT);
    }

    @Test void textureViewToLuminLeaseClosesViewBeforeBackingImage() {
        List<String> closes = new ArrayList<>();
        TrackingImage image = new TrackingImage(closes);
        TrackingView view = new TrackingView(image, closes);
        Fixture fixture = new Fixture(new ThrowingFactory(), image, view);
        TestTextureView source = new TestTextureView(new TestTexture());

        BridgeLease<Blaze3DBridge262.RhiTextureView262> lease =
                fixture.bridge.textureViewToLumin(source).orElseThrow();
        lease.close();

        assertEquals(List.of("view", "image"), closes);
        assertFalse(source.isClosed());
        source.close();
    }

    @Test void samplerRebuildCompatibilityFailureIsTyped() {
        Fixture fixture = new Fixture(new ThrowingFactory());
        BridgeResult<?> result = fixture.bridge.samplerToMinecraft(null, ignored -> {
            throw new IllegalStateException("injected sampler rebuild failure");
        });
        assertUnsupported(result, BridgeUnsupportedReason.MC_SHAPE_CHANGED);
    }

    @Test void samplerRebuildProgrammerFailureEscapes() {
        Fixture fixture = new Fixture(new ThrowingFactory());
        assertThrows(NullPointerException.class, () -> fixture.bridge.samplerToMinecraft(null, ignored -> {
            throw new NullPointerException("injected programmer failure");
        }));
    }

    @Test void pipelineRebuildCompatibilityFailureIsTyped() {
        Fixture fixture = new Fixture(new ThrowingFactory());
        BridgeResult<?> result = fixture.bridge.pipelineToMinecraft(null, ignored -> {
            throw new IllegalArgumentException("injected pipeline rebuild failure");
        });
        assertUnsupported(result, BridgeUnsupportedReason.MC_SHAPE_CHANGED);
    }

    @Test void pipelineRebuildProgrammerFailureEscapes() {
        Fixture fixture = new Fixture(new ThrowingFactory());
        assertThrows(NullPointerException.class, () -> fixture.bridge.pipelineToMinecraft(null, ignored -> {
            throw new NullPointerException("injected programmer failure");
        }));
    }

    private static void assertUnsupported(BridgeResult<?> result, BridgeUnsupportedReason reason) {
        BridgeResult.Unsupported<?> unsupported = assertInstanceOf(BridgeResult.Unsupported.class, result);
        assertEquals(reason, unsupported.detail().reason());
    }

    private static final class Fixture {
        private final BridgeContextIdentity bridgeContext = BridgeContextIdentity.create("reverse-failure");
        private final Object capabilities = new Object();
        private final RhiContextIdentity rhiContext = new RhiContextIdentity(41, "reverse-failure");
        private final RhiInvalidationToken rhiToken = new RhiInvalidationToken();
        private final Blaze3DBridge262 bridge;

        private Fixture(GlObjectFactory262 factory) {
            this(factory, null, null);
        }

        private Fixture(GlObjectFactory262 factory, RhiImage adoptedImage, RhiImageView adoptedView) {
            bridge = new Blaze3DBridge262(new Device(rhiContext, adoptedImage, adoptedView), bridgeContext,
                    bridgeContext.newInvalidationToken(), rhiContext, rhiToken,
                    capabilities, () -> capabilities, () -> bridgeContext, factory);
        }

        private ViewImage image(long handle) {
            return new ViewImage(handle, rhiContext, rhiToken, OpenGlNativeObjectTypes.TEXTURE);
        }

        private RhiImageView view(long handle) {
            return new View(image(handle));
        }

        private BridgeCapability capability(String type, String diagnostic, long handle) {
            return BridgeCapability.openGl(type, diagnostic, handle, bridgeContext,
                    bridgeContext.newInvalidationToken(), () -> true, capabilities,
                    () -> capabilities, () -> bridgeContext);
        }
    }

    private static class ThrowingFactory implements GlObjectFactory262 {
        private static IllegalStateException failure() { return new IllegalStateException("injected factory failure"); }
        @Override public GlTexture texture(int usage, String label, GpuFormat format, int width, int height,
                int depthOrLayers, int mipLevels, int handle, FrameBufferCache cache) { throw failure(); }
        @Override public GlTextureView textureView(GlTexture texture, int baseMipLevel, int mipLevels,
                FrameBufferCache cache) { throw failure(); }
        @Override public GlBuffer.Direct buffer(DirectStateAccess dsa, int usage, long size, int handle,
                boolean canPersistentMap) { throw failure(); }
        @Override public GlShaderModule shader(int handle, Identifier id, ShaderType type) { throw failure(); }
        @Override public GlProgram program(int handle, String debugLabel) { throw failure(); }
    }

    private static final class RecordingFactory extends ThrowingFactory {
        private final GlTextureView result;
        private GlTexture parent;
        private int baseMipLevel = -1;
        private int mipLevels = -1;
        private FrameBufferCache frameBufferCache;

        private RecordingFactory(GlTextureView result) {
            this.result = result;
        }

        @Override public GlTextureView textureView(GlTexture texture, int baseMipLevel, int mipLevels,
                FrameBufferCache frameBufferCache) {
            this.parent = texture;
            this.baseMipLevel = baseMipLevel;
            this.mipLevels = mipLevels;
            this.frameBufferCache = frameBufferCache;
            return result;
        }
    }

    private static final class Image implements RhiImage {
        private final long handle;
        private Image(long handle) { this.handle = handle; }
        @Override public BackendApi api() { return BackendApi.OPENGL_DSA; }
        @Override public RhiExtent3D extent() { return new RhiExtent3D(4, 4, 1); }
        @Override public RhiFormat format() { return RhiFormat.RGBA8_UNORM; }
        @Override public Optional<RhiNativeObject> getNativeObject(RhiNativeObjectType type) {
            return Optional.of(new RhiNativeObject(OpenGlNativeObjectTypes.TEXTURE, handle));
        }
        @Override public void close() { }
    }

    private static final class ViewImage implements RhiImage, OpenGlAdoptedResource {
        private final long handle;
        private final RhiContextIdentity context;
        private final RhiInvalidationToken token;
        private final RhiNativeObjectType nativeType;
        private boolean closed;

        private ViewImage(long handle, RhiContextIdentity context, RhiInvalidationToken token,
                RhiNativeObjectType nativeType) {
            this.handle = handle;
            this.context = context;
            this.token = token;
            this.nativeType = nativeType;
        }

        @Override public BackendApi api() { return BackendApi.OPENGL_DSA; }
        @Override public RhiExtent3D extent() { return new RhiExtent3D(4, 4, 1); }
        @Override public RhiFormat format() { return RhiFormat.RGBA8_UNORM; }
        @Override public RhiOwnership ownership() { return RhiOwnership.BORROWED; }
        @Override public RhiContextIdentity contextIdentity() { return context; }
        @Override public RhiInvalidationToken invalidationToken() { return token; }
        @Override public Optional<RhiNativeObject> getNativeObject(RhiNativeObjectType type) {
            if (closed) throw new RhiResourceClosedException("injected closed view image");
            token.requireValid();
            return Optional.of(new RhiNativeObject(nativeType, handle));
        }
        @Override public void close() { closed = true; }
        private boolean isClosed() { return closed; }
    }

    private record View(RhiImage image) implements RhiImageView {
        @Override public BackendApi api() { return image.api(); }
        @Override public RhiFormat format() { return image.format(); }
        @Override public Set<RhiImageAspect> aspects() { return Set.of(RhiImageAspect.COLOR); }
        @Override public void close() { }
    }

    private static final class TestTexture extends GlTexture {
        private TestTexture() {
            super(4, "parent", GpuFormat.RGBA8_UNORM, 4, 4, 1, 1, 3, new FrameBufferCache());
        }
    }

    private static final class TestTextureView extends GlTextureView {
        private TestTextureView(GlTexture parent) {
            super(parent, 0, 1, new FrameBufferCache());
        }
    }

    private static final class TrackingImage implements RhiImage {
        private final List<String> closes;
        private TrackingImage(List<String> closes) { this.closes = closes; }
        @Override public BackendApi api() { return BackendApi.OPENGL_DSA; }
        @Override public RhiExtent3D extent() { return new RhiExtent3D(4, 4, 1); }
        @Override public RhiFormat format() { return RhiFormat.RGBA8_UNORM; }
        @Override public void close() { closes.add("image"); }
    }

    private record TrackingView(RhiImage image, List<String> closes) implements RhiImageView {
        @Override public BackendApi api() { return image.api(); }
        @Override public RhiFormat format() { return image.format(); }
        @Override public Set<RhiImageAspect> aspects() { return Set.of(RhiImageAspect.COLOR); }
        @Override public void close() { closes.add("view"); }
    }

    private static final class Buffer implements RhiBuffer {
        private final long handle;
        private Buffer(long handle) { this.handle = handle; }
        @Override public BackendApi api() { return BackendApi.OPENGL_DSA; }
        @Override public long size() { return 64; }
        @Override public Optional<RhiNativeObject> getNativeObject(RhiNativeObjectType type) {
            return Optional.of(new RhiNativeObject(OpenGlNativeObjectTypes.BUFFER, handle));
        }
        @Override public void close() { }
    }

    private static final class Shader implements RhiShader {
        private final long handle;
        private Shader(long handle) { this.handle = handle; }
        @Override public BackendApi api() { return BackendApi.OPENGL_DSA; }
        @Override public RhiShaderDesc desc() { return new RhiShaderDesc(RhiShaderStage.VERTEX, "main", "test"); }
        @Override public Optional<RhiShaderBinary> binary() { return Optional.empty(); }
        @Override public RhiOwnership ownership() { return RhiOwnership.BORROWED; }
        @Override public RhiContextIdentity contextIdentity() { return new RhiContextIdentity(41, "reverse-failure"); }
        @Override public Optional<RhiInvalidationToken> invalidationToken() { return Optional.empty(); }
        @Override public Optional<RhiNativeObject> getNativeObject(RhiNativeObjectType type) {
            return Optional.of(new RhiNativeObject(com.github.slmpc.prismrhi.shader.RhiShaderNativeObjectTypes.OPENGL_SHADER_OBJECT, handle));
        }
        @Override public void close() { }
    }

    private static final class Device implements OpenGlExternalDevice {
        private final RhiContextIdentity context;
        private final RhiImage adoptedImage;
        private final RhiImageView adoptedView;
        private Device(RhiContextIdentity context, RhiImage adoptedImage, RhiImageView adoptedView) {
            this.context = context;
            this.adoptedImage = adoptedImage;
            this.adoptedView = adoptedView;
        }
        @Override public BackendApi api() { return BackendApi.OPENGL_DSA; }
        @Override public RhiContextIdentity contextIdentity() { return context; }
        @Override public OpenGlExternalContext externalContext() { return null; }
        @Override public RhiQueue queue(RhiQueueType type) { throw unsupported(); }
        @Override public RhiBuffer createBuffer(RhiBufferCreateInfo info) { throw unsupported(); }
        @Override public RhiImage createImage(RhiImageCreateInfo info) { throw unsupported(); }
        @Override public RhiImageView createImageView(RhiImageViewCreateInfo info) { throw unsupported(); }
        @Override public RhiSampler createSampler(RhiSamplerCreateInfo info) { throw unsupported(); }
        @Override public RhiShader createShader(RhiShaderDesc desc, RhiShaderBinaryFormat format, ByteBuffer bytes) { throw unsupported(); }
        @Override public RhiShader adoptShader(RhiShaderDesc desc, RhiNativeObject object, RhiOwnership ownership,
                RhiContextIdentity context, RhiInvalidationToken token) { throw unsupported(); }
        @Override public RhiCommandPool createCommandPool(RhiCommandPoolCreateInfo info) { throw unsupported(); }
        @Override public RhiDescriptorSetLayout createDescriptorSetLayout(RhiDescriptorSetLayoutCreateInfo info) { throw unsupported(); }
        @Override public RhiDescriptorSet allocateDescriptorSet(RhiDescriptorSetAllocateInfo info) { throw unsupported(); }
        @Override public RhiGraphicsPipeline createGraphicsPipeline(RhiGraphicsPipelineCreateInfo info) { throw unsupported(); }
        @Override public RhiBuffer adoptBuffer(OpenGlBufferAdoption adoption) { throw unsupported(); }
        @Override public RhiImage adoptImage(OpenGlImageAdoption adoption) {
            if (adoptedImage == null) throw unsupported();
            return adoptedImage;
        }
        @Override public RhiImageView adoptImageView(OpenGlImageViewAdoption adoption) {
            if (adoptedView == null) throw unsupported();
            return adoptedView;
        }
        @Override public RhiSampler adoptSampler(OpenGlSamplerAdoption adoption) { throw unsupported(); }
        @Override public RhiGraphicsPipeline adoptPipeline(OpenGlPipelineAdoption adoption) { throw unsupported(); }
        @Override public void waitIdle() { }
        @Override public void close() { }
        private static UnsupportedOperationException unsupported() { return new UnsupportedOperationException("unused"); }
    }
}
