package com.github.slmpc.lumingraphics.mc.v2612.bridge;

import com.github.slmpc.lumingraphics.mc.bridge.BridgeResult;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeUnsupportedDetail;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeUnsupportedReason;
import com.github.slmpc.lumingraphics.mc.v2612.access.BorrowedBlazeResource2612;
import com.github.slmpc.lumingraphics.mc.v2612.mixin.GlAccess2612;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlAdoptedResource;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlBufferAdoption;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalDevice;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlImageAdoption;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlImageViewAdoption;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlNativeObjectTypes;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlShaderAdoption;
import com.github.slmpc.prismrhi.format.RhiExtent3D;
import com.github.slmpc.prismrhi.format.RhiFormat;
import com.github.slmpc.prismrhi.pipeline.RhiGraphicsPipeline;
import com.github.slmpc.prismrhi.resource.RhiBuffer;
import com.github.slmpc.prismrhi.resource.RhiBufferCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiBufferUsage;
import com.github.slmpc.prismrhi.resource.RhiImage;
import com.github.slmpc.prismrhi.resource.RhiImageCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiImageUsage;
import com.github.slmpc.prismrhi.resource.RhiMemoryUsage;
import com.github.slmpc.prismrhi.resource.RhiNativeObject;
import com.github.slmpc.prismrhi.resource.RhiNativeObjects;
import com.github.slmpc.prismrhi.resource.RhiOwnership;
import com.github.slmpc.prismrhi.resource.RhiFilter;
import com.github.slmpc.prismrhi.resource.RhiSampler;
import com.github.slmpc.prismrhi.resource.RhiSamplerAddressMode;
import com.github.slmpc.prismrhi.resource.RhiSamplerCreateInfo;
import com.github.slmpc.prismrhi.shader.RhiShader;
import com.github.slmpc.prismrhi.shader.RhiShaderDesc;
import com.github.slmpc.prismrhi.shader.RhiShaderStage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlBuffer;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.BiFunction;
import net.minecraft.resources.Identifier;

public final class Blaze3DBridge2612 {
    private final OpenGlExternalDevice device;
    private final BridgeContext2612 context;
    private final Function<RhiSamplerCreateInfo, GpuSampler> samplerFactory;

    public Blaze3DBridge2612(OpenGlExternalDevice device) {
        this.device = Objects.requireNonNull(device, "device");
        this.context = new BridgeContext2612() {
            @Override public void requireCurrent() { device.externalContext().requireCurrent(); }
            @Override public com.github.slmpc.prismrhi.context.RhiContextIdentity identity() {
                return device.externalContext().contextIdentity();
            }
            @Override public com.github.slmpc.prismrhi.context.RhiInvalidationToken invalidation() {
                return device.externalContext().invalidation();
            }
        };
        this.samplerFactory = Blaze3DBridge2612::createGlSampler;
    }

    Blaze3DBridge2612(OpenGlExternalDevice device, BridgeContext2612 context,
                      Function<RhiSamplerCreateInfo, GpuSampler> samplerFactory) {
        this.device = Objects.requireNonNull(device, "device");
        this.context = Objects.requireNonNull(context, "context");
        this.samplerFactory = Objects.requireNonNull(samplerFactory, "samplerFactory");
    }

    public BridgeResult<RhiImage> fromBlazeTexture(GpuTexture texture) {
        if (!(texture instanceof GlTexture glTexture)) return mismatch("GlTexture", texture);
        if (glTexture.isClosed()) return closed("texture");
        context.requireCurrent();
        var info = new RhiImageCreateInfo(
                new RhiExtent3D(glTexture.getWidth(0), glTexture.getHeight(0), glTexture.getDepthOrLayers()),
                toRhiFormat(glTexture.getFormat()), imageUsage(glTexture.usage(), glTexture.getFormat()),
                RhiMemoryUsage.GPU_ONLY);
        return BridgeResult.success(device.adoptImage(new OpenGlImageAdoption(
                new RhiNativeObject(OpenGlNativeObjectTypes.TEXTURE, glTexture.glId()), info,
                RhiOwnership.BORROWED, context.identity(), context.invalidation())));
    }

    public BridgeResult<com.github.slmpc.prismrhi.resource.RhiImageView> fromBlazeTextureView(GlTextureView view) {
        if (view.isClosed()) return closed("texture-view");
        BridgeResult<RhiImage> image = fromBlazeTexture(view.texture());
        if (image.unsupportedDetail().isPresent()) return BridgeResult.unsupported(image.unsupportedDetail().orElseThrow());
        var createInfo = com.github.slmpc.prismrhi.resource.RhiImageViewCreateInfo.builder(image.orElseThrow())
                .mipRange(view.baseMipLevel(), view.mipLevels()).build();
        return BridgeResult.success(device.adoptImageView(new OpenGlImageViewAdoption(createInfo)));
    }

    public BridgeResult<GpuTexture> toBlazeTexture(RhiImage image, int mipLevels, String label) {
        return toBlazeTexture(image, GpuTexture.USAGE_TEXTURE_BINDING, mipLevels, label);
    }

    public BridgeResult<GpuTexture> toBlazeTexture(RhiImage image, int blazeUsage, int mipLevels, String label) {
        BridgeUnsupportedDetail failure = exportFailure(image, "texture");
        if (failure != null) return BridgeResult.unsupported(failure);
        imageUsage(blazeUsage);
        int handle = checkedHandle(RhiNativeObjects.requireValue(image, OpenGlNativeObjectTypes.TEXTURE));
        GpuTexture texture = new BorrowedGlTexture2612(blazeUsage, label,
                toBlazeFormat(image.format()), image.extent().width(), image.extent().height(),
                image.extent().depth(), mipLevels, handle);
        ((BorrowedBlazeResource2612) texture).lumin$markBorrowed(() -> { });
        return BridgeResult.success(texture);
    }

    public BridgeResult<GlTextureView> toBlazeTextureView(
            com.github.slmpc.prismrhi.resource.RhiImageView view, int baseMipLevel, int mipLevels, String label) {
        BridgeResult<GpuTexture> texture = toBlazeTexture(view.image(), baseMipLevel + mipLevels, label);
        if (texture.unsupportedDetail().isPresent()) return BridgeResult.unsupported(texture.unsupportedDetail().orElseThrow());
        return BridgeResult.success(new BorrowedGlTextureView2612(
                (GlTexture) texture.orElseThrow(), baseMipLevel, mipLevels));
    }

    public BridgeResult<RhiBufferSlice2612> fromBlazeBuffer(GpuBufferSlice slice) {
        Objects.requireNonNull(slice, "slice");
        if (!(slice.buffer() instanceof GlBuffer glBuffer)) return mismatch("GlBuffer", slice.buffer());
        if (glBuffer.isClosed()) return closed("buffer");
        if (slice.offset() < 0 || slice.length() < 0 || slice.offset() + slice.length() > glBuffer.size()) {
            return BridgeResult.unsupported(new BridgeUnsupportedDetail.State(
                    BridgeUnsupportedReason.MC_SHAPE_CHANGED, "buffer slice is outside its source"));
        }
        context.requireCurrent();
        int handle = glBuffer instanceof BorrowedGlBuffer2612 borrowed
                ? borrowed.nativeHandle() : ((GlAccess2612.Buffer) glBuffer).lumin$handle();
        var info = new RhiBufferCreateInfo(glBuffer.size(), bufferUsage(glBuffer.usage()),
                bufferMemoryUsage(glBuffer.usage()));
        RhiBuffer adopted = device.adoptBuffer(new OpenGlBufferAdoption(
                new RhiNativeObject(OpenGlNativeObjectTypes.BUFFER, handle), info, RhiOwnership.BORROWED,
                context.identity(), context.invalidation()));
        return BridgeResult.success(new RhiBufferSlice2612(adopted, slice.offset(), slice.length()));
    }

    public BridgeResult<RhiBuffer> fromBlazeBuffer(GpuBuffer buffer) {
        BridgeResult<RhiBufferSlice2612> result = fromBlazeBuffer(buffer.slice());
        return result.unsupportedDetail().isPresent()
                ? BridgeResult.unsupported(result.unsupportedDetail().orElseThrow())
                : BridgeResult.success(result.orElseThrow().buffer());
    }

    public BridgeResult<GpuBuffer> toBlazeBuffer(RhiBuffer buffer, int blazeUsage) {
        BridgeUnsupportedDetail failure = exportFailure(buffer, "buffer");
        if (failure != null) return BridgeResult.unsupported(failure);
        bufferUsage(blazeUsage);
        int handle = checkedHandle(RhiNativeObjects.requireValue(buffer, OpenGlNativeObjectTypes.BUFFER));
        GlBuffer result = new BorrowedGlBuffer2612(blazeUsage, buffer.size(), handle);
        ((BorrowedBlazeResource2612) result).lumin$markBorrowed(() -> { });
        return BridgeResult.success(result);
    }

    public BridgeResult<RhiShader> fromBlazeShader(GlShaderModule shader) {
        if (shader.getShaderId() == -1) return closed("shader-module");
        context.requireCurrent();
        ShaderType type = shader instanceof BorrowedGlShaderModule2612 borrowed
                ? borrowed.shaderType() : ((GlAccess2612.Shader) shader).lumin$type();
        RhiShaderStage stage = type == ShaderType.VERTEX ? RhiShaderStage.VERTEX : RhiShaderStage.FRAGMENT;
        return BridgeResult.success(device.adoptShader(new OpenGlShaderAdoption(
                new RhiShaderDesc(stage, "main", shader.getDebugLabel()),
                new RhiNativeObject(OpenGlNativeObjectTypes.SHADER, shader.getShaderId()), RhiOwnership.BORROWED,
                context.identity(), context.invalidation())));
    }

    public BridgeResult<GlShaderModule> toBlazeShader(RhiShader shader) {
        BridgeUnsupportedDetail failure = exportShaderFailure(shader);
        if (failure != null) return BridgeResult.unsupported(failure);
        ShaderType type = switch (shader.desc().stage()) {
            case VERTEX -> ShaderType.VERTEX;
            case FRAGMENT -> ShaderType.FRAGMENT;
            case COMPUTE -> throw new IllegalArgumentException("Minecraft 26.1.2 has no compute shader module");
        };
        int handle = checkedHandle(RhiNativeObjects.requireValue(shader, OpenGlNativeObjectTypes.SHADER));
        GlShaderModule result = new BorrowedGlShaderModule2612(handle,
                Identifier.fromNamespaceAndPath("lumin_graphics_mc", "borrowed/" + type.getName()), type);
        ((BorrowedBlazeResource2612) result).lumin$markBorrowed(() -> { });
        return BridgeResult.success(result);
    }

    public BridgeResult<RhiSampler> fromBlazeSampler(GpuSampler sampler) {
        Objects.requireNonNull(sampler, "sampler");
        if (sampler instanceof GlSampler glSampler && glSampler.isClosed()) return closed("sampler");
        context.requireCurrent();
        return BridgeResult.success(device.createSampler(new RhiSamplerCreateInfo(
                sampler.getMinFilter() == FilterMode.NEAREST ? RhiFilter.NEAREST : RhiFilter.LINEAR,
                sampler.getMagFilter() == FilterMode.NEAREST ? RhiFilter.NEAREST : RhiFilter.LINEAR,
                toRhiAddress(sampler.getAddressModeU()), toRhiAddress(sampler.getAddressModeV()),
                toRhiAddress(sampler.getAddressModeV()), sampler.getMaxAnisotropy())));
    }

    public BridgeResult<GpuSampler> toBlazeSampler(RhiSampler sampler, RhiSamplerCreateInfo info) {
        BridgeUnsupportedDetail failure = exportFailure(sampler, "sampler");
        if (failure != null) return BridgeResult.unsupported(failure);
        Objects.requireNonNull(info, "info");
        if (info.addressModeU() == RhiSamplerAddressMode.MIRRORED_REPEAT
                || info.addressModeU() == RhiSamplerAddressMode.CLAMP_TO_BORDER
                || info.addressModeV() == RhiSamplerAddressMode.MIRRORED_REPEAT
                || info.addressModeV() == RhiSamplerAddressMode.CLAMP_TO_BORDER) {
            return BridgeResult.unsupported(new BridgeUnsupportedDetail.State(
                    BridgeUnsupportedReason.MC_SHAPE_CHANGED, "Minecraft 26.1.2 cannot represent the sampler address mode"));
        }
        return BridgeResult.success(samplerFactory.apply(info));
    }

    public BridgeResult<RhiGraphicsPipeline> rebuildPipeline(
            RenderPipeline pipeline, Function<RenderPipeline, RhiGraphicsPipeline> rebuilder) {
        context.requireCurrent();
        return BridgeResult.success(Objects.requireNonNull(rebuilder.apply(pipeline), "rebuilt pipeline"));
    }

    public BridgeResult<RhiGraphicsPipeline> rebuildPipeline(
            RenderPipeline pipeline,
            BiFunction<RenderPipeline, PipelineMetadata2612, RhiGraphicsPipeline> rebuilder) {
        context.requireCurrent();
        return BridgeResult.success(Objects.requireNonNull(
                rebuilder.apply(pipeline, PipelineMetadata2612.from(pipeline)), "rebuilt pipeline"));
    }

    public BridgeResult<RhiGraphicsPipeline> rebuildCompiledPipeline(
            CompiledRenderPipeline pipeline, Function<GlRenderPipeline, RhiGraphicsPipeline> rebuilder) {
        if (!(pipeline instanceof GlRenderPipeline glPipeline)) return mismatch("GlRenderPipeline", pipeline);
        if (!glPipeline.isValid()) return closed("compiled-pipeline");
        context.requireCurrent();
        return BridgeResult.success(Objects.requireNonNull(rebuilder.apply(glPipeline), "rebuilt compiled pipeline"));
    }

    public BridgeResult<RenderPipeline> rebuildPipeline(
            RhiGraphicsPipeline pipeline, Function<RhiGraphicsPipeline, RenderPipeline> rebuilder) {
        BridgeUnsupportedDetail failure = exportFailure(pipeline, "pipeline");
        if (failure != null) return BridgeResult.unsupported(failure);
        return BridgeResult.success(Objects.requireNonNull(rebuilder.apply(pipeline), "rebuilt pipeline"));
    }

    public BridgeResult<CommandEncoderAdapter2612> adaptCommandEncoder(CommandEncoder encoder) {
        context.requireCurrent();
        return BridgeResult.success(new CommandEncoderAdapter2612(encoder, context::requireCurrent));
    }

    public BridgeResult<RenderPassAdapter2612> adaptRenderPass(RenderPass pass) {
        context.requireCurrent();
        return BridgeResult.success(new RenderPassAdapter2612(pass, context::requireCurrent));
    }

    public <T> BridgeResult<T> unsupportedBackend(String backend, String role) {
        return BridgeResult.unsupported(new BridgeUnsupportedDetail.Mismatch(
                BridgeUnsupportedReason.BACKEND_MISMATCH, "backend", "opengl", backend + ":" + role));
    }

    private BridgeUnsupportedDetail exportFailure(Object resource, String role) {
        if (!(resource instanceof OpenGlAdoptedResource adopted)) {
            return new BridgeUnsupportedDetail.State(BridgeUnsupportedReason.TYPE_MISMATCH,
                    role + " is not a typed Prism OpenGL adopted resource");
        }
        if (!adopted.contextIdentity().equals(context.identity())) {
            return new BridgeUnsupportedDetail.State(BridgeUnsupportedReason.CONTEXT_MISMATCH,
                    role + " belongs to a different OpenGL context");
        }
        if (!adopted.invalidationToken().isValid()) {
            return new BridgeUnsupportedDetail.State(BridgeUnsupportedReason.TOKEN_INVALIDATED,
                    role + " invalidation token is stale");
        }
        context.requireCurrent();
        return null;
    }

    private BridgeUnsupportedDetail exportShaderFailure(RhiShader shader) {
        if (!shader.contextIdentity().equals(context.identity())) {
            return new BridgeUnsupportedDetail.State(BridgeUnsupportedReason.CONTEXT_MISMATCH,
                    "shader-module belongs to a different OpenGL context");
        }
        var invalidation = shader.invalidationToken();
        if (invalidation.isEmpty()) {
            return new BridgeUnsupportedDetail.State(BridgeUnsupportedReason.TYPE_MISMATCH,
                    "shader-module is not an adopted OpenGL shader");
        }
        if (!invalidation.orElseThrow().isValid()) {
            return new BridgeUnsupportedDetail.State(BridgeUnsupportedReason.TOKEN_INVALIDATED,
                    "shader-module invalidation token is stale");
        }
        context.requireCurrent();
        return null;
    }

    private static int checkedHandle(long handle) {
        if (handle <= 0 || handle > Integer.MAX_VALUE) throw new IllegalArgumentException("invalid OpenGL object name");
        return (int) handle;
    }

    private static <T> BridgeResult<T> closed(String role) {
        return BridgeResult.unsupported(new BridgeUnsupportedDetail.State(
                BridgeUnsupportedReason.CLOSED, role + " is closed"));
    }

    private static <T> BridgeResult<T> mismatch(String expected, Object actual) {
        return BridgeResult.unsupported(new BridgeUnsupportedDetail.Mismatch(
                BridgeUnsupportedReason.TYPE_MISMATCH, "class", expected, actual.getClass().getName()));
    }

    public static RhiFormat toRhiFormat(TextureFormat format) {
        return switch (format) {
            case RGBA8 -> RhiFormat.RGBA8_UNORM;
            case RED8 -> RhiFormat.R8_UNORM;
            case DEPTH32 -> RhiFormat.D32_FLOAT;
            case RED8I -> throw new IllegalArgumentException("Prism 0.1.0 has no signed R8 integer format");
        };
    }

    public static TextureFormat toBlazeFormat(RhiFormat format) {
        return switch (format) {
            case RGBA8_UNORM -> TextureFormat.RGBA8;
            case R8_UNORM -> TextureFormat.RED8;
            case D32_FLOAT -> TextureFormat.DEPTH32;
            default -> throw new IllegalArgumentException("Minecraft 26.1.2 cannot represent " + format);
        };
    }

    private static EnumSet<RhiImageUsage> imageUsage(int usage) {
        return imageUsage(usage, null);
    }

    private static EnumSet<RhiImageUsage> imageUsage(int usage, TextureFormat format) {
        var result = EnumSet.noneOf(RhiImageUsage.class);
        if ((usage & GpuTexture.USAGE_COPY_SRC) != 0) result.add(RhiImageUsage.TRANSFER_SRC);
        if ((usage & GpuTexture.USAGE_COPY_DST) != 0) result.add(RhiImageUsage.TRANSFER_DST);
        if ((usage & GpuTexture.USAGE_TEXTURE_BINDING) != 0) result.add(RhiImageUsage.SAMPLED);
        if ((usage & GpuTexture.USAGE_RENDER_ATTACHMENT) != 0) {
            result.add(format == TextureFormat.DEPTH32
                    ? RhiImageUsage.DEPTH_STENCIL_ATTACHMENT : RhiImageUsage.COLOR_ATTACHMENT);
        }
        if (result.isEmpty()) throw new IllegalArgumentException("texture has no compatible usage");
        return result;
    }

    private static EnumSet<RhiBufferUsage> bufferUsage(int usage) {
        var result = EnumSet.noneOf(RhiBufferUsage.class);
        if ((usage & GpuBuffer.USAGE_COPY_SRC) != 0) result.add(RhiBufferUsage.TRANSFER_SRC);
        if ((usage & GpuBuffer.USAGE_COPY_DST) != 0) result.add(RhiBufferUsage.TRANSFER_DST);
        if ((usage & GpuBuffer.USAGE_VERTEX) != 0) result.add(RhiBufferUsage.VERTEX_BUFFER);
        if ((usage & GpuBuffer.USAGE_INDEX) != 0) result.add(RhiBufferUsage.INDEX_BUFFER);
        if ((usage & GpuBuffer.USAGE_UNIFORM) != 0) result.add(RhiBufferUsage.UNIFORM_BUFFER);
        if ((usage & GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER) != 0) result.add(RhiBufferUsage.STORAGE_BUFFER);
        if (result.isEmpty()) result.add(RhiBufferUsage.TRANSFER_DST);
        return result;
    }

    private static RhiMemoryUsage bufferMemoryUsage(int usage) {
        if ((usage & GpuBuffer.USAGE_MAP_READ) != 0) return RhiMemoryUsage.GPU_TO_CPU;
        if ((usage & GpuBuffer.USAGE_MAP_WRITE) != 0) return RhiMemoryUsage.CPU_TO_GPU;
        return RhiMemoryUsage.GPU_ONLY;
    }

    private static RhiSamplerAddressMode toRhiAddress(AddressMode mode) {
        return mode == AddressMode.REPEAT ? RhiSamplerAddressMode.REPEAT : RhiSamplerAddressMode.CLAMP_TO_EDGE;
    }

    private static AddressMode toBlazeAddress(RhiSamplerAddressMode mode) {
        return switch (mode) {
            case REPEAT -> AddressMode.REPEAT;
            case CLAMP_TO_EDGE -> AddressMode.CLAMP_TO_EDGE;
            case MIRRORED_REPEAT, CLAMP_TO_BORDER ->
                    throw new IllegalArgumentException("Minecraft 26.1.2 cannot represent " + mode);
        };
    }

    private static GpuSampler createGlSampler(RhiSamplerCreateInfo info) {
        return new GlSampler(
                toBlazeAddress(info.addressModeU()), toBlazeAddress(info.addressModeV()),
                info.minFilter() == RhiFilter.NEAREST ? FilterMode.NEAREST : FilterMode.LINEAR,
                info.magFilter() == RhiFilter.NEAREST ? FilterMode.NEAREST : FilterMode.LINEAR,
                Math.round(info.maxAnisotropy()), java.util.OptionalDouble.empty());
    }

}
