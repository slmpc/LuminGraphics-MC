package com.github.slmpc.lumingraphics.mc.v262.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.slmpc.prismrhi.command.PRhiPrimitiveTopology;
import com.github.slmpc.prismrhi.format.PRhiFormat;
import com.github.slmpc.prismrhi.resource.PRhiBufferUsage;
import com.github.slmpc.prismrhi.resource.PRhiImageUsage;
import com.github.slmpc.prismrhi.resource.PRhiMemoryUsage;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.textures.GpuTexture;
import org.junit.jupiter.api.Test;

class BridgeTranslations262Test {
    @Test void translatesExact262FormatsAndFailsClosed() {
        assertEquals(PRhiFormat.RGBA8_UNORM, BridgeTranslations262.format(GpuFormat.RGBA8_UNORM));
        assertEquals(GpuFormat.D24_UNORM_S8_UINT, BridgeTranslations262.format(PRhiFormat.D24_UNORM_S8_UINT));
        assertThrows(IllegalArgumentException.class, () -> BridgeTranslations262.format(GpuFormat.RGBA8_SNORM));
        assertThrows(IllegalArgumentException.class, () -> BridgeTranslations262.format(PRhiFormat.BGRA8_UNORM));
    }

    @Test void translatesTextureUsageAndRejectsCubemapDowngrade() {
        var usage = BridgeTranslations262.imageUsage(GpuTexture.USAGE_COPY_DST
                | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.RGBA8_UNORM);
        assertEquals(java.util.Set.of(PRhiImageUsage.TRANSFER_DST, PRhiImageUsage.SAMPLED,
                PRhiImageUsage.COLOR_ATTACHMENT), usage);
        assertThrows(IllegalArgumentException.class, () -> BridgeTranslations262.imageUsage(
                GpuTexture.USAGE_CUBEMAP_COMPATIBLE, GpuFormat.RGBA8_UNORM));
    }

    @Test void translatesIndirectAndMappedBufferMetadata() {
        var usage = BridgeTranslations262.bufferUsage(GpuBuffer.USAGE_COPY_DST
                | GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_INDIRECT_PARAMETERS);
        assertTrue(usage.containsAll(java.util.Set.of(PRhiBufferUsage.TRANSFER_DST,
                PRhiBufferUsage.VERTEX_BUFFER, PRhiBufferUsage.INDIRECT_BUFFER)));
        assertEquals(PRhiMemoryUsage.CPU_TO_GPU,
                BridgeTranslations262.memoryUsage(GpuBuffer.USAGE_MAP_WRITE));
        assertThrows(IllegalArgumentException.class, () -> BridgeTranslations262.memoryUsage(
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_MAP_WRITE));
    }

    @Test void translatesAll262PrimitiveTopologyRoles() {
        assertEquals(PRhiPrimitiveTopology.TRIANGLE_LIST, BridgeTranslations262.topology(PrimitiveTopology.QUADS));
        assertEquals(PRhiPrimitiveTopology.TRIANGLE_FAN, BridgeTranslations262.topology(PrimitiveTopology.TRIANGLE_FAN));
    }
}
