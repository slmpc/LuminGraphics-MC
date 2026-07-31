package com.github.slmpc.lumingraphics.mc.v2612.bridge;

import com.github.slmpc.lumingraphics.mc.v2612.access.BorrowedBlazeResource2612;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.TextureFormat;

final class BorrowedGlTexture2612 extends GlTexture implements BorrowedBlazeResource2612 {
    private final BorrowedRelease2612 release = new BorrowedRelease2612();
    private int borrowedViews;

    BorrowedGlTexture2612(int usage, String label, TextureFormat format, int width, int height,
                          int depthOrLayers, int mipLevels, int id) {
        super(usage, label, format, width, height, depthOrLayers, mipLevels, id);
    }

    @Override public void lumin$markBorrowed(Runnable action) { release.mark(action); }
    @Override public boolean lumin$isBorrowed() { return true; }

    @Override
    public void close() {
        closed = true;
        releaseIfUnused();
    }

    @Override
    public void addViews() {
        if (closed) throw new IllegalStateException("Cannot add a view to a closed borrowed texture");
        borrowedViews++;
    }

    @Override
    public void removeViews() {
        if (borrowedViews <= 0) throw new IllegalStateException("Borrowed texture view count underflow");
        borrowedViews--;
        releaseIfUnused();
    }

    private void releaseIfUnused() {
        if (closed && borrowedViews == 0) release.runOnce();
    }
}
