package com.github.slmpc.lumingraphics.mc.v2612.bridge;

import com.github.slmpc.lumingraphics.mc.v2612.access.BorrowedBlazeResource2612;
import com.mojang.blaze3d.opengl.GlBuffer;

final class BorrowedGlBuffer2612 extends GlBuffer implements BorrowedBlazeResource2612 {
    private final BorrowedRelease2612 release = new BorrowedRelease2612();

    BorrowedGlBuffer2612(int usage, long size, int handle) {
        super(() -> "borrowed Prism buffer", null, usage, size, handle, null);
    }

    int nativeHandle() { return handle; }
    @Override public void lumin$markBorrowed(Runnable action) { release.mark(action); }
    @Override public boolean lumin$isBorrowed() { return true; }

    @Override public void close() {
        closed = true;
        release.runOnce();
    }
}
