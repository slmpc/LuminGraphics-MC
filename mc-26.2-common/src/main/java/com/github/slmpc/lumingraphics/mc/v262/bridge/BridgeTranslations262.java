package com.github.slmpc.lumingraphics.mc.v262.bridge;

import com.github.slmpc.prismrhi.command.RhiPrimitiveTopology;
import com.github.slmpc.prismrhi.format.RhiFormat;
import com.github.slmpc.prismrhi.resource.RhiBufferUsage;
import com.github.slmpc.prismrhi.resource.RhiFilter;
import com.github.slmpc.prismrhi.resource.RhiImageUsage;
import com.github.slmpc.prismrhi.resource.RhiMemoryUsage;
import com.github.slmpc.prismrhi.resource.RhiSamplerAddressMode;
import com.github.slmpc.prismrhi.resource.RhiSamplerCreateInfo;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import java.util.EnumSet;
import java.util.Set;

public final class BridgeTranslations262 {
    private BridgeTranslations262() { }

    public static RhiFormat format(GpuFormat format) {
        return switch (format) {
            case R8_UNORM -> RhiFormat.R8_UNORM;
            case RG8_UNORM -> RhiFormat.RG8_UNORM;
            case RGB8_UNORM -> RhiFormat.RGB8_UNORM;
            case RGBA8_UNORM -> RhiFormat.RGBA8_UNORM;
            case R32_FLOAT -> RhiFormat.R32_FLOAT;
            case RG32_FLOAT -> RhiFormat.RG32_FLOAT;
            case RGB32_FLOAT -> RhiFormat.RGB32_FLOAT;
            case RGBA16_FLOAT -> RhiFormat.RGBA16_FLOAT;
            case RGBA32_FLOAT -> RhiFormat.RGBA32_FLOAT;
            case D24_UNORM_S8_UINT -> RhiFormat.D24_UNORM_S8_UINT;
            case D32_FLOAT -> RhiFormat.D32_FLOAT;
            default -> throw new IllegalArgumentException("Unsupported Minecraft 26.2 GpuFormat: " + format);
        };
    }

    public static GpuFormat format(RhiFormat format) {
        return switch (format) {
            case R8_UNORM -> GpuFormat.R8_UNORM;
            case RG8_UNORM -> GpuFormat.RG8_UNORM;
            case RGB8_UNORM -> GpuFormat.RGB8_UNORM;
            case RGBA8_UNORM -> GpuFormat.RGBA8_UNORM;
            case R32_FLOAT -> GpuFormat.R32_FLOAT;
            case RG32_FLOAT -> GpuFormat.RG32_FLOAT;
            case RGB32_FLOAT -> GpuFormat.RGB32_FLOAT;
            case RGBA16_FLOAT -> GpuFormat.RGBA16_FLOAT;
            case RGBA32_FLOAT -> GpuFormat.RGBA32_FLOAT;
            case D24_UNORM_S8_UINT -> GpuFormat.D24_UNORM_S8_UINT;
            case D32_FLOAT -> GpuFormat.D32_FLOAT;
            default -> throw new IllegalArgumentException("Unsupported Prism image format for Minecraft 26.2: " + format);
        };
    }

    public static Set<RhiImageUsage> imageUsage(int usage, GpuFormat format) {
        int known = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING
                | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_CUBEMAP_COMPATIBLE;
        if ((usage & ~known) != 0) throw new IllegalArgumentException("Unknown Minecraft 26.2 texture usage bits: " + usage);
        EnumSet<RhiImageUsage> translated = EnumSet.noneOf(RhiImageUsage.class);
        if ((usage & GpuTexture.USAGE_COPY_DST) != 0) translated.add(RhiImageUsage.TRANSFER_DST);
        if ((usage & GpuTexture.USAGE_COPY_SRC) != 0) translated.add(RhiImageUsage.TRANSFER_SRC);
        if ((usage & GpuTexture.USAGE_TEXTURE_BINDING) != 0) translated.add(RhiImageUsage.SAMPLED);
        if ((usage & GpuTexture.USAGE_RENDER_ATTACHMENT) != 0) {
            translated.add(format.hasDepthAspect() || format.hasStencilAspect()
                    ? RhiImageUsage.DEPTH_STENCIL_ATTACHMENT : RhiImageUsage.COLOR_ATTACHMENT);
        }
        if ((usage & GpuTexture.USAGE_CUBEMAP_COMPATIBLE) != 0) {
            throw new IllegalArgumentException("Prism 0.1.0 cannot preserve Minecraft 26.2 cubemap compatibility");
        }
        if (translated.isEmpty()) throw new IllegalArgumentException("Texture usage must not be empty");
        return Set.copyOf(translated);
    }

    public static Set<RhiBufferUsage> bufferUsage(int usage) {
        int known = GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_HINT_CLIENT_STORAGE
                | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_COPY_SRC | GpuBuffer.USAGE_VERTEX
                | GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER
                | GpuBuffer.USAGE_INDIRECT_PARAMETERS;
        if ((usage & ~known) != 0) throw new IllegalArgumentException("Unknown Minecraft 26.2 buffer usage bits: " + usage);
        EnumSet<RhiBufferUsage> translated = EnumSet.noneOf(RhiBufferUsage.class);
        if ((usage & GpuBuffer.USAGE_COPY_DST) != 0) translated.add(RhiBufferUsage.TRANSFER_DST);
        if ((usage & GpuBuffer.USAGE_COPY_SRC) != 0) translated.add(RhiBufferUsage.TRANSFER_SRC);
        if ((usage & GpuBuffer.USAGE_VERTEX) != 0) translated.add(RhiBufferUsage.VERTEX_BUFFER);
        if ((usage & GpuBuffer.USAGE_INDEX) != 0) translated.add(RhiBufferUsage.INDEX_BUFFER);
        if ((usage & GpuBuffer.USAGE_UNIFORM) != 0) translated.add(RhiBufferUsage.UNIFORM_BUFFER);
        if ((usage & GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER) != 0) translated.add(RhiBufferUsage.STORAGE_BUFFER);
        if ((usage & GpuBuffer.USAGE_INDIRECT_PARAMETERS) != 0) translated.add(RhiBufferUsage.INDIRECT_BUFFER);
        if (translated.isEmpty()) throw new IllegalArgumentException("Buffer usage has no Prism-preservable role");
        return Set.copyOf(translated);
    }

    public static RhiMemoryUsage memoryUsage(int usage) {
        boolean read = (usage & GpuBuffer.USAGE_MAP_READ) != 0;
        boolean write = (usage & GpuBuffer.USAGE_MAP_WRITE) != 0;
        if (read && write) throw new IllegalArgumentException("Prism 0.1.0 cannot preserve bidirectional mapped memory usage");
        if (read) return RhiMemoryUsage.GPU_TO_CPU;
        if (write || (usage & GpuBuffer.USAGE_HINT_CLIENT_STORAGE) != 0) return RhiMemoryUsage.CPU_TO_GPU;
        return RhiMemoryUsage.GPU_ONLY;
    }

    public static RhiSamplerCreateInfo sampler(GpuSampler sampler) {
        return new RhiSamplerCreateInfo(filter(sampler.getMinFilter()), filter(sampler.getMagFilter()),
                address(sampler.getAddressModeU()), address(sampler.getAddressModeV()),
                address(sampler.getAddressModeV()), sampler.getMaxAnisotropy());
    }

    public static RhiPrimitiveTopology topology(PrimitiveTopology topology) {
        return switch (topology) {
            case POINTS -> RhiPrimitiveTopology.POINT_LIST;
            case LINES, DEBUG_LINES -> RhiPrimitiveTopology.LINE_LIST;
            case DEBUG_LINE_STRIP -> RhiPrimitiveTopology.LINE_STRIP;
            case TRIANGLES, QUADS -> RhiPrimitiveTopology.TRIANGLE_LIST;
            case TRIANGLE_STRIP -> RhiPrimitiveTopology.TRIANGLE_STRIP;
            case TRIANGLE_FAN -> RhiPrimitiveTopology.TRIANGLE_FAN;
        };
    }

    private static RhiFilter filter(FilterMode filter) {
        return filter == FilterMode.NEAREST ? RhiFilter.NEAREST : RhiFilter.LINEAR;
    }

    private static RhiSamplerAddressMode address(AddressMode address) {
        return address == AddressMode.REPEAT ? RhiSamplerAddressMode.REPEAT : RhiSamplerAddressMode.CLAMP_TO_EDGE;
    }
}
