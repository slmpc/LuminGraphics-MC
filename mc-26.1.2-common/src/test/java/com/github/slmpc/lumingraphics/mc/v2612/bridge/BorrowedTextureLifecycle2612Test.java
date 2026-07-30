package com.github.slmpc.lumingraphics.mc.v2612.bridge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;

class BorrowedTextureLifecycle2612Test {
    @Test
    void textureFirstThenViewCloseNeverReachesCallerNativeTextureDeletion() {
        BorrowedGlTexture2612 texture = borrowedTexture();
        AtomicInteger releases = new AtomicInteger();
        texture.lumin$markBorrowed(releases::incrementAndGet);
        BorrowedGlTextureView2612 view = new BorrowedGlTextureView2612(texture, 0, 1);
        texture.close();
        assertTrue(texture.isClosed());
        assertEquals(0, releases.get(), "texture bookkeeping released while a borrowed view remained live");
        assertDoesNotThrow(view::close,
                "borrowed view close reached GlTexture.destroyImmediately and attempted glDeleteTextures");
        assertEquals(1, releases.get());
        view.close();
        texture.close();
        assertEquals(1, releases.get());
    }

    @Test
    void viewFirstAndRepeatedBorrowedCloseAreIdempotent() {
        BorrowedGlTexture2612 texture = borrowedTexture();
        BorrowedGlTextureView2612 view = new BorrowedGlTextureView2612(texture, 0, 1);
        assertDoesNotThrow(view::close);
        assertDoesNotThrow(view::close);
        assertDoesNotThrow(texture::close);
        assertDoesNotThrow(texture::close);
    }

    @Test
    void ordinaryOwnedTextureStillUsesVanillaDeletionPath() {
        GlTexture owned = new OwnedProbeTexture();
        assertThrows(Throwable.class, owned::close,
                "owned texture unexpectedly skipped the vanilla native deletion path");
    }

    private static BorrowedGlTexture2612 borrowedTexture() {
        return new BorrowedGlTexture2612(GpuTexture.USAGE_TEXTURE_BINDING, "borrowed", TextureFormat.RGBA8,
                4, 4, 1, 1, 91);
    }

    private static final class OwnedProbeTexture extends GlTexture {
        private OwnedProbeTexture() {
            super(GpuTexture.USAGE_TEXTURE_BINDING, "owned", TextureFormat.RGBA8, 4, 4, 1, 1, 92);
        }
    }
}
