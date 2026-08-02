package com.github.slmpc.lumingraphics.mc.v2612.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.slmpc.lumingraphics.mc.bridge.BridgeResult;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeUnsupportedReason;
import com.github.slmpc.lumingraphics.mc.v2612.access.BorrowedBlazeResource2612;
import com.github.slmpc.prismrhi.RhiResourceInvalidatedException;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlNativeObjectTypes;
import com.github.slmpc.prismrhi.context.RhiContextIdentity;
import com.github.slmpc.prismrhi.context.RhiInvalidationToken;
import com.github.slmpc.prismrhi.format.RhiExtent3D;
import com.github.slmpc.prismrhi.format.RhiFormat;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipeline;
import com.github.slmpc.prismrhi.resource.RhiBuffer;
import com.github.slmpc.prismrhi.resource.RhiFilter;
import com.github.slmpc.prismrhi.resource.RhiImage;
import com.github.slmpc.prismrhi.resource.RhiImageView;
import com.github.slmpc.prismrhi.resource.RhiNativeObjects;
import com.github.slmpc.prismrhi.resource.RhiSampler;
import com.github.slmpc.prismrhi.resource.RhiSamplerAddressMode;
import com.github.slmpc.prismrhi.resource.RhiSamplerCreateInfo;
import com.github.slmpc.prismrhi.shader.RhiShader;
import com.github.slmpc.prismrhi.shader.RhiShaderDesc;
import com.github.slmpc.prismrhi.shader.RhiShaderStage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassBackend;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.lang.reflect.Proxy;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class BridgeMatrix2612Test {
    @Test
    void blazeResourcesExecuteAllImportsThroughActualMinecraftTypes() {
        Fixture fixture = new Fixture();
        BorrowedGlTexture2612 texture = new BorrowedGlTexture2612(
                GpuTexture.USAGE_TEXTURE_BINDING, "texture", TextureFormat.RGBA8, 8, 4, 1, 2, 101);
        GlTextureView view = new BorrowedGlTextureView2612(texture, 1, 1);
        BorrowedGlBuffer2612 buffer = new BorrowedGlBuffer2612(GpuBuffer.USAGE_VERTEX, 64, 102);
        GlShaderModule shader = new BorrowedGlShaderModule2612(103,
                Identifier.fromNamespaceAndPath("test", "vertex"), ShaderType.VERTEX);
        GpuSampler sampler = new TestSampler(RhiSamplerCreateInfo.linearRepeat());

        RhiImage image = fixture.bridge.fromBlazeTexture(texture).orElseThrow();
        RhiImageView importedView = fixture.bridge.fromBlazeTextureView(view).orElseThrow();
        RhiBufferSlice2612 importedSlice = fixture.bridge.fromBlazeBuffer(
                new GpuBufferSlice(buffer, 8, 16)).orElseThrow();
        RhiShader importedShader = fixture.bridge.fromBlazeShader(shader).orElseThrow();
        RhiSampler importedSampler = fixture.bridge.fromBlazeSampler(sampler).orElseThrow();

        assertEquals(101, fixture.device.imageAdoption.nativeObject().value());
        assertSame(fixture.device.viewAdoption.createInfo().image(), importedView.image());
        assertEquals(101, RhiNativeObjects.requireValue(image, OpenGlNativeObjectTypes.TEXTURE));
        assertEquals(101, RhiNativeObjects.requireValue(importedView, OpenGlNativeObjectTypes.TEXTURE));
        assertEquals(101, RhiNativeObjects.requireValue(importedView.image(), OpenGlNativeObjectTypes.TEXTURE));
        assertEquals(102, fixture.device.bufferAdoption.nativeObject().value());
        assertEquals(8, importedSlice.offset());
        assertEquals(16, importedSlice.length());
        assertEquals(RhiShaderStage.VERTEX, importedShader.desc().stage());
        assertEquals(103, fixture.device.shaderAdoption.nativeObject().value());
        assertInstanceOf(RhiSampler.class, importedSampler);
        assertEquals(RhiFilter.LINEAR, fixture.device.samplerInfo.minFilter());

        view.close();
        texture.close();
        buffer.close();
        shader.close();
    }

    @Test
    void prismResourcesExecuteAllExportsAsBorrowedMinecraftWrappers() {
        Fixture fixture = new Fixture();
        RhiImage image = BridgeTestFixtures2612.image(201, new RhiExtent3D(8, 4, 1),
                RhiFormat.RGBA8_UNORM, fixture.context.identity, fixture.context.token);
        RhiImageView view = BridgeTestFixtures2612.imageView(image, fixture.context.identity, fixture.context.token);
        RhiBuffer buffer = BridgeTestFixtures2612.buffer(202, 64, fixture.context.identity, fixture.context.token);
        RhiShader shader = BridgeTestFixtures2612.shader(203,
                new RhiShaderDesc(RhiShaderStage.FRAGMENT, "main", "fragment"),
                fixture.context.identity, fixture.context.token);
        RhiSampler sampler = BridgeTestFixtures2612.sampler(204, fixture.context.identity, fixture.context.token);
        RhiSamplerCreateInfo samplerInfo = RhiSamplerCreateInfo.linearRepeat();

        GpuTexture texture = fixture.bridge.toBlazeTexture(image, 2, "texture").orElseThrow();
        GlTextureView exportedView = fixture.bridge.toBlazeTextureView(view, 1, 1, "view").orElseThrow();
        GpuBuffer exportedBuffer = fixture.bridge.toBlazeBuffer(buffer, GpuBuffer.USAGE_VERTEX).orElseThrow();
        GlShaderModule exportedShader = fixture.bridge.toBlazeShader(shader).orElseThrow();
        GpuSampler exportedSampler = fixture.bridge.toBlazeSampler(sampler, samplerInfo).orElseThrow();

        assertEquals(201, assertInstanceOf(GlTexture.class, texture).glId());
        assertTrue(assertInstanceOf(BorrowedBlazeResource2612.class, texture).lumin$isBorrowed());
        assertTrue(assertInstanceOf(BorrowedBlazeResource2612.class, exportedBuffer).lumin$isBorrowed());
        assertTrue(assertInstanceOf(BorrowedBlazeResource2612.class, exportedShader).lumin$isBorrowed());
        assertEquals(1, exportedView.baseMipLevel());
        assertEquals(FilterMode.LINEAR, exportedSampler.getMinFilter());
        assertEquals(AddressMode.REPEAT, exportedSampler.getAddressModeU());

        exportedView.close();
        texture.close();
        exportedBuffer.close();
        exportedShader.close();
        exportedSampler.close();
    }

    @Test
    void invalidStateMetadataAndTypesFailBeforeNativeAccess() {
        Fixture fixture = new Fixture();
        RhiContextIdentity otherIdentity = new RhiContextIdentity(2613, "other");
        RhiImage wrongContext = BridgeTestFixtures2612.image(301, new RhiExtent3D(4, 4, 1),
                RhiFormat.RGBA8_UNORM, otherIdentity, fixture.context.token);
        RhiInvalidationToken staleToken = new RhiInvalidationToken();
        RhiImage stale = BridgeTestFixtures2612.image(302, new RhiExtent3D(4, 4, 1),
                RhiFormat.RGBA8_UNORM, fixture.context.identity, staleToken);
        staleToken.invalidate();
        BorrowedGlTexture2612 closedTexture = texture(303);
        closedTexture.close();
        BorrowedGlBuffer2612 buffer = new BorrowedGlBuffer2612(GpuBuffer.USAGE_VERTEX, 32, 304);

        assertEquals(BridgeUnsupportedReason.CONTEXT_MISMATCH,
                reason(fixture.bridge.toBlazeTexture(wrongContext, 1, "wrong")));
        assertEquals(BridgeUnsupportedReason.TOKEN_INVALIDATED,
                reason(fixture.bridge.toBlazeTexture(stale, 1, "stale")));
        assertEquals(BridgeUnsupportedReason.TYPE_MISMATCH,
                reason(fixture.bridge.fromBlazeTexture(new OtherTexture())));
        assertEquals(BridgeUnsupportedReason.CLOSED,
                reason(fixture.bridge.fromBlazeTexture(closedTexture)));
        assertEquals(BridgeUnsupportedReason.MC_SHAPE_CHANGED,
                reason(fixture.bridge.fromBlazeBuffer(new GpuBufferSlice(buffer, 24, 16))));
        assertThrows(IllegalArgumentException.class, () -> fixture.bridge.toBlazeTexture(
                BridgeTestFixtures2612.image(305, new RhiExtent3D(4, 4, 1), RhiFormat.BGRA8_UNORM,
                        fixture.context.identity, fixture.context.token), 1, "format"));

        RhiSamplerCreateInfo unsupportedSampler = new RhiSamplerCreateInfo(RhiFilter.LINEAR, RhiFilter.LINEAR,
                RhiSamplerAddressMode.MIRRORED_REPEAT, RhiSamplerAddressMode.REPEAT,
                RhiSamplerAddressMode.REPEAT, 0);
        assertEquals(BridgeUnsupportedReason.MC_SHAPE_CHANGED, reason(fixture.bridge.toBlazeSampler(
                BridgeTestFixtures2612.sampler(306, fixture.context.identity, fixture.context.token),
                unsupportedSampler)));

        fixture.context.current = false;
        assertThrows(IllegalStateException.class, () -> fixture.bridge.fromBlazeTexture(texture(307)));
        buffer.close();
    }

    @Test
    void pipelineAndCommandAdaptersExecuteMetadataStalenessAndContextGuards() {
        Fixture fixture = new Fixture();
        RenderPipeline pipeline = pipeline();
        RhiGraphicsPipeline rebuilt = BridgeTestFixtures2612.pipeline(
                401, fixture.context.identity, fixture.context.token);
        AtomicReference<PipelineMetadata2612> metadata = new AtomicReference<>();
        AtomicInteger rebuilds = new AtomicInteger();

        assertSame(rebuilt, fixture.bridge.rebuildPipeline(pipeline, (source, value) -> {
            assertSame(pipeline, source);
            metadata.set(value);
            rebuilds.incrementAndGet();
            return rebuilt;
        }).orElseThrow());
        fixture.bridge.rebuildPipeline(pipeline, (source, value) -> {
            rebuilds.incrementAndGet();
            return rebuilt;
        }).orElseThrow();
        assertEquals(2, rebuilds.get());
        assertEquals(PipelineMetadata2612.Primitive.TRIANGLES, metadata.get().primitive());
        assertEquals(12, metadata.get().vertexStride());
        assertEquals(1, metadata.get().samplers().size());
        assertSame(pipeline, fixture.bridge.rebuildPipeline(rebuilt, ignored -> pipeline).orElseThrow());

        CompiledRenderPipeline stale = new GlRenderPipeline(pipeline, GlProgram.INVALID_PROGRAM);
        assertEquals(BridgeUnsupportedReason.CLOSED,
                reason(fixture.bridge.rebuildCompiledPipeline(stale, ignored -> rebuilt)));
        assertEquals(BridgeUnsupportedReason.TYPE_MISMATCH,
                reason(fixture.bridge.rebuildCompiledPipeline(() -> true, ignored -> rebuilt)));

        CommandEncoder encoder = new CommandEncoder(proxy(GpuDeviceBackend.class), proxy(CommandEncoderBackend.class));
        RenderPass pass = new RenderPass(proxy(RenderPassBackend.class), proxy(GpuDeviceBackend.class));
        CommandEncoderAdapter2612 encoderAdapter = fixture.bridge.adaptCommandEncoder(encoder).orElseThrow();
        RenderPassAdapter2612 passAdapter = fixture.bridge.adaptRenderPass(pass).orElseThrow();
        assertSame(encoder, encoderAdapter.access());
        assertSame(pass, passAdapter.access());
        fixture.context.current = false;
        assertThrows(IllegalStateException.class, encoderAdapter::delegate);
        assertThrows(IllegalStateException.class, passAdapter::delegate);
        assertThrows(IllegalStateException.class, encoderAdapter::access);
        assertThrows(IllegalStateException.class, passAdapter::access);
        fixture.context.current = true;
        fixture.context.token.invalidate();
        assertThrows(RhiResourceInvalidatedException.class, encoderAdapter::delegate);
        assertThrows(RhiResourceInvalidatedException.class, passAdapter::delegate);
        assertThrows(RhiResourceInvalidatedException.class, encoderAdapter::access);
        assertThrows(RhiResourceInvalidatedException.class, passAdapter::access);
    }

    private static RenderPipeline pipeline() {
        VertexFormat format = VertexFormat.builder().add("Position", VertexFormatElement.POSITION).build();
        return RenderPipeline.builder()
                .withLocation("test/pipeline")
                .withVertexShader("test/vertex")
                .withFragmentShader("test/fragment")
                .withSampler("DiffuseSampler")
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(format, VertexFormat.Mode.TRIANGLES)
                .build();
    }

    private static BorrowedGlTexture2612 texture(int handle) {
        return new BorrowedGlTexture2612(GpuTexture.USAGE_TEXTURE_BINDING, "texture", TextureFormat.RGBA8,
                4, 4, 1, 1, handle);
    }

    private static BridgeUnsupportedReason reason(BridgeResult<?> result) {
        return result.unsupportedDetail().orElseThrow().reason();
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
    }

    private static final class Fixture {
        private final BridgeTestFixtures2612.Context context = new BridgeTestFixtures2612.Context();
        private final BridgeTestFixtures2612.Device device = new BridgeTestFixtures2612.Device(context);
        private final Blaze3DBridge2612 bridge = new Blaze3DBridge2612(
                device.proxy(), context, TestSampler::new);
    }

    private static final class TestSampler extends GpuSampler {
        private final RhiSamplerCreateInfo info;
        private boolean closed;

        private TestSampler(RhiSamplerCreateInfo info) { this.info = info; }
        @Override public AddressMode getAddressModeU() { return address(info.addressModeU()); }
        @Override public AddressMode getAddressModeV() { return address(info.addressModeV()); }
        @Override public FilterMode getMinFilter() { return filter(info.minFilter()); }
        @Override public FilterMode getMagFilter() { return filter(info.magFilter()); }
        @Override public int getMaxAnisotropy() { return Math.round(info.maxAnisotropy()); }
        @Override public OptionalDouble getMaxLod() { return OptionalDouble.empty(); }
        @Override public void close() { closed = true; }

        private static AddressMode address(RhiSamplerAddressMode mode) {
            return mode == RhiSamplerAddressMode.REPEAT ? AddressMode.REPEAT : AddressMode.CLAMP_TO_EDGE;
        }
        private static FilterMode filter(RhiFilter filter) {
            return filter == RhiFilter.NEAREST ? FilterMode.NEAREST : FilterMode.LINEAR;
        }
    }

    private static final class OtherTexture extends GpuTexture {
        private boolean closed;

        private OtherTexture() {
            super(GpuTexture.USAGE_TEXTURE_BINDING, "other", TextureFormat.RGBA8, 1, 1, 1, 1);
        }
        @Override public void close() { closed = true; }
        @Override public boolean isClosed() { return closed; }
    }
}
