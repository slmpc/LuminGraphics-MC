package com.github.slmpc.lumingraphics.mc.v262.bridge;

import com.github.slmpc.prismrhi.command.PRhiPrimitiveTopology;
import com.github.slmpc.prismrhi.format.PRhiFormat;
import com.github.slmpc.prismrhi.resource.PRhiBufferUsage;
import com.github.slmpc.prismrhi.resource.PRhiFilter;
import com.github.slmpc.prismrhi.resource.PRhiImageUsage;
import com.github.slmpc.prismrhi.resource.PRhiMemoryUsage;
import com.github.slmpc.prismrhi.resource.PRhiSamplerAddressMode;
import com.github.slmpc.prismrhi.resource.PRhiSamplerCreateInfo;
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

    public static PRhiFormat format(GpuFormat format) {
        return switch (format) {
            case R8_UNORM -> PRhiFormat.R8_UNORM;
            case RG8_UNORM -> PRhiFormat.RG8_UNORM;
            case RGB8_UNORM -> PRhiFormat.RGB8_UNORM;
            case RGBA8_UNORM -> PRhiFormat.RGBA8_UNORM;
            case R32_FLOAT -> PRhiFormat.R32_FLOAT;
            case RG32_FLOAT -> PRhiFormat.RG32_FLOAT;
            case RGB32_FLOAT -> PRhiFormat.RGB32_FLOAT;
            case RGBA16_FLOAT -> PRhiFormat.RGBA16_FLOAT;
            case RGBA32_FLOAT -> PRhiFormat.RGBA32_FLOAT;
            case D24_UNORM_S8_UINT -> PRhiFormat.D24_UNORM_S8_UINT;
            case D32_FLOAT -> PRhiFormat.D32_FLOAT;
            default -> throw new IllegalArgumentException("Unsupported Minecraft 26.2 GpuFormat: " + format);
        };
    }

    public static GpuFormat format(PRhiFormat format) {
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

    public static Set<PRhiImageUsage> imageUsage(int usage, GpuFormat format) {
        int known = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING
                | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_CUBEMAP_COMPATIBLE;
        if ((usage & ~known) != 0) throw new IllegalArgumentException("Unknown Minecraft 26.2 texture usage bits: " + usage);
        EnumSet<PRhiImageUsage> translated = EnumSet.noneOf(PRhiImageUsage.class);
        if ((usage & GpuTexture.USAGE_COPY_DST) != 0) translated.add(PRhiImageUsage.TRANSFER_DST);
        if ((usage & GpuTexture.USAGE_COPY_SRC) != 0) translated.add(PRhiImageUsage.TRANSFER_SRC);
        if ((usage & GpuTexture.USAGE_TEXTURE_BINDING) != 0) translated.add(PRhiImageUsage.SAMPLED);
        if ((usage & GpuTexture.USAGE_RENDER_ATTACHMENT) != 0) {
            translated.add(format.hasDepthAspect() || format.hasStencilAspect()
                    ? PRhiImageUsage.DEPTH_STENCIL_ATTACHMENT : PRhiImageUsage.COLOR_ATTACHMENT);
        }
        if ((usage & GpuTexture.USAGE_CUBEMAP_COMPATIBLE) != 0) {
            throw new IllegalArgumentException("Prism 0.1.0 cannot preserve Minecraft 26.2 cubemap compatibility");
        }
        if (translated.isEmpty()) throw new IllegalArgumentException("Texture usage must not be empty");
        return Set.copyOf(translated);
    }

    public static Set<PRhiBufferUsage> bufferUsage(int usage) {
        int known = GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_HINT_CLIENT_STORAGE
                | GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_COPY_SRC | GpuBuffer.USAGE_VERTEX
                | GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER
                | GpuBuffer.USAGE_INDIRECT_PARAMETERS;
        if ((usage & ~known) != 0) throw new IllegalArgumentException("Unknown Minecraft 26.2 buffer usage bits: " + usage);
        EnumSet<PRhiBufferUsage> translated = EnumSet.noneOf(PRhiBufferUsage.class);
        if ((usage & GpuBuffer.USAGE_COPY_DST) != 0) translated.add(PRhiBufferUsage.TRANSFER_DST);
        if ((usage & GpuBuffer.USAGE_COPY_SRC) != 0) translated.add(PRhiBufferUsage.TRANSFER_SRC);
        if ((usage & GpuBuffer.USAGE_VERTEX) != 0) translated.add(PRhiBufferUsage.VERTEX_BUFFER);
        if ((usage & GpuBuffer.USAGE_INDEX) != 0) translated.add(PRhiBufferUsage.INDEX_BUFFER);
        if ((usage & GpuBuffer.USAGE_UNIFORM) != 0) translated.add(PRhiBufferUsage.UNIFORM_BUFFER);
        if ((usage & GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER) != 0) translated.add(PRhiBufferUsage.STORAGE_BUFFER);
        if ((usage & GpuBuffer.USAGE_INDIRECT_PARAMETERS) != 0) translated.add(PRhiBufferUsage.INDIRECT_BUFFER);
        if (translated.isEmpty()) throw new IllegalArgumentException("Buffer usage has no Prism-preservable role");
        return Set.copyOf(translated);
    }

    public static PRhiMemoryUsage memoryUsage(int usage) {
        boolean read = (usage & GpuBuffer.USAGE_MAP_READ) != 0;
        boolean write = (usage & GpuBuffer.USAGE_MAP_WRITE) != 0;
        if (read && write) throw new IllegalArgumentException("Prism 0.1.0 cannot preserve bidirectional mapped memory usage");
        if (read) return PRhiMemoryUsage.GPU_TO_CPU;
        if (write || (usage & GpuBuffer.USAGE_HINT_CLIENT_STORAGE) != 0) return PRhiMemoryUsage.CPU_TO_GPU;
        return PRhiMemoryUsage.GPU_ONLY;
    }

    public static PRhiSamplerCreateInfo sampler(GpuSampler sampler) {
        return new PRhiSamplerCreateInfo(filter(sampler.getMinFilter()), filter(sampler.getMagFilter()),
                address(sampler.getAddressModeU()), address(sampler.getAddressModeV()),
                address(sampler.getAddressModeV()), sampler.getMaxAnisotropy());
    }

    public static PRhiPrimitiveTopology topology(PrimitiveTopology topology) {
        return switch (topology) {
            case POINTS -> PRhiPrimitiveTopology.POINT_LIST;
            case LINES, DEBUG_LINES -> PRhiPrimitiveTopology.LINE_LIST;
            case DEBUG_LINE_STRIP -> PRhiPrimitiveTopology.LINE_STRIP;
            case TRIANGLES, QUADS -> PRhiPrimitiveTopology.TRIANGLE_LIST;
            case TRIANGLE_STRIP -> PRhiPrimitiveTopology.TRIANGLE_STRIP;
            case TRIANGLE_FAN -> PRhiPrimitiveTopology.TRIANGLE_FAN;
        };
    }

    private static PRhiFilter filter(FilterMode filter) {
        return filter == FilterMode.NEAREST ? PRhiFilter.NEAREST : PRhiFilter.LINEAR;
    }

    private static PRhiSamplerAddressMode address(AddressMode address) {
        return address == AddressMode.REPEAT ? PRhiSamplerAddressMode.REPEAT : PRhiSamplerAddressMode.CLAMP_TO_EDGE;
    }
}
