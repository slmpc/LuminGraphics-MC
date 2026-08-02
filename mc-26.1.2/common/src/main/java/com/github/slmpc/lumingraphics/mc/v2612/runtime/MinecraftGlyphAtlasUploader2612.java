package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import com.github.slmpc.lumingraphics.render.resource.DefaultRenderResources;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DTexture;
import com.github.slmpc.lumingraphics.mc.v2612.text.MinecraftFontAdapter2612;
import com.github.slmpc.lumingraphics.text.atlas.AtlasPixels;
import com.github.slmpc.lumingraphics.text.atlas.GlyphAtlasUpload;
import com.github.slmpc.lumingraphics.text.atlas.GlyphAtlasUploader;
import com.github.slmpc.prismrhi.barrier.RhiImageBarrier;
import com.github.slmpc.prismrhi.barrier.RhiPipelineBarrier;
import com.github.slmpc.prismrhi.barrier.RhiResourceState;
import com.github.slmpc.prismrhi.descriptor.RhiDescriptorSet;
import com.github.slmpc.prismrhi.format.RhiExtent3D;
import com.github.slmpc.prismrhi.format.RhiFormat;
import com.github.slmpc.prismrhi.resource.RhiBuffer;
import com.github.slmpc.prismrhi.resource.RhiBufferCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiBufferUsage;
import com.github.slmpc.prismrhi.resource.RhiImage;
import com.github.slmpc.prismrhi.resource.RhiImageCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiImageUsage;
import com.github.slmpc.prismrhi.resource.RhiImageView;
import com.github.slmpc.prismrhi.resource.RhiImageViewCreateInfo;
import com.github.slmpc.prismrhi.resource.RhiMemoryUsage;
import com.github.slmpc.prismrhi.resource.RhiSampler;
import com.github.slmpc.prismrhi.resource.RhiSamplerCreateInfo;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** 在 Minecraft 当前命令缓冲中上传并持有 Lumin TTF glyph atlas。 */
final class MinecraftGlyphAtlasUploader2612 implements GlyphAtlasUploader, AutoCloseable {
    private static final AtomicLong NEXT_ID = new AtomicLong();

    private final MinecraftGraphicsRuntime2612 runtime;
    private final DefaultRenderResources resources;
    private final Minecraft client;
    private boolean closed;

    MinecraftGlyphAtlasUploader2612(Minecraft client, MinecraftGraphicsRuntime2612 runtime,
                                    DefaultRenderResources resources) {
        this.client = client;
        this.runtime = runtime;
        this.resources = resources;
    }

    @Override
    public synchronized GlyphAtlasUpload upload(AtlasPixels pixels) {
        if (closed) throw new IllegalStateException("Minecraft glyph atlas uploader is closed");
        runtime.luminContext().requireRenderThread();
        RhiBuffer staging = null;
        RhiImage image = null;
        RhiImageView view = null;
        RhiSampler sampler = null;
        RhiDescriptorSet descriptor = null;
        GlTextureView minecraftView = null;
        Identifier minecraftId = Identifier.fromNamespaceAndPath("lumin_graphics_mc",
                "glyph-atlas/" + NEXT_ID.incrementAndGet());
        boolean minecraftRegistered = false;
        Render2DTexture texture = new Render2DTexture.Resource(
                minecraftId.toString());
        try {
            byte[] data = pixels.data();
            RhiFormat format = switch (pixels.format()) {
                case ALPHA8 -> RhiFormat.R8_UNORM;
                case RGBA8 -> RhiFormat.RGBA8_UNORM;
            };
            staging = runtime.device().createBuffer(RhiBufferCreateInfo.builder(data.length)
                    .usage(RhiBufferUsage.TRANSFER_SRC).memoryUsage(RhiMemoryUsage.CPU_TO_GPU).build());
            staging.write(stagingBytes(data));
            image = runtime.device().createImage(RhiImageCreateInfo.builder(
                            RhiExtent3D.of2D(pixels.width(), pixels.height()))
                    .format(format).usage(RhiImageUsage.TRANSFER_DST).usage(RhiImageUsage.SAMPLED).build());
            view = runtime.device().createImageView(RhiImageViewCreateInfo.of(image));
            sampler = runtime.device().createSampler(RhiSamplerCreateInfo.linearRepeat());
            minecraftView = runtime.blazeBridge().toBlazeTextureView(view, 0, 1,
                    "lumin-graphics-mc-glyph-atlas").orElseThrow();
            descriptor = resources.createTextureDescriptor(view, sampler);
            resources.registerTextureDescriptor(texture, descriptor, runtime.device().contextIdentity());
            runtime.commandBuffer().pipelineBarrier(RhiPipelineBarrier.builder().image(RhiImageBarrier.of(
                    image, RhiResourceState.UNDEFINED, RhiResourceState.TRANSFER_DST)).build());
            runtime.commandBuffer().copyBufferToImage(staging, image);
            runtime.commandBuffer().pipelineBarrier(RhiPipelineBarrier.builder().image(RhiImageBarrier.of(
                    image, RhiResourceState.TRANSFER_DST, RhiResourceState.SAMPLED_IMAGE)).build());
            // staging 只服务本帧上传，submit 执行完 copy 后即可释放，无需跟随整张 atlas 的寿命。
            runtime.retireAfterFrame(staging);
            staging = null;
            var minecraftSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            client.getTextureManager().register(minecraftId, new BorrowedGlyphAtlasTexture2612(
                    minecraftView.texture(), minecraftView, minecraftSampler));
            minecraftRegistered = true;
            RhiImage ownedImage = image;
            RhiImageView ownedView = view;
            RhiSampler ownedSampler = sampler;
            RhiDescriptorSet ownedDescriptor = descriptor;
            GlTextureView ownedMinecraftView = minecraftView;
            return new GlyphAtlasUpload(new MinecraftGlyphAtlasTexture2612(
                    texture, minecraftView, minecraftId, minecraftSampler), () -> {
                // 旧 revision 可能仍被当前帧的 Minecraft/Lumin draw 引用，统一在 submit 后退休。
                runtime.retireAfterFrame(() -> {
                    MinecraftFontAdapter2612.releaseTexture(minecraftId);
                    client.getTextureManager().release(minecraftId);
                    resources.unregisterTextureDescriptor(texture, ownedDescriptor);
                    RuntimeException failure = GraphicsResourceCloser2612.closeReverse(
                            ownedDescriptor, ownedSampler, ownedMinecraftView, ownedView, ownedImage);
                    if (failure != null) throw failure;
                });
            });
        } catch (RuntimeException failure) {
            if (minecraftRegistered) client.getTextureManager().release(minecraftId);
            RuntimeException cleanup = GraphicsResourceCloser2612.closeReverse(
                    descriptor, sampler, minecraftView, view, image, staging);
            if (cleanup != null) failure.addSuppressed(cleanup);
            throw failure;
        }
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
    }

    private static ByteBuffer stagingBytes(byte[] data) {
        return ByteBuffer.allocateDirect(data.length).put(data).flip();
    }
}
