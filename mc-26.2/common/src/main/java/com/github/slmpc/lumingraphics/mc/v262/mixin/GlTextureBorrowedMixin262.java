package com.github.slmpc.lumingraphics.mc.v262.mixin;

import com.github.slmpc.lumingraphics.mc.v262.access.BorrowedGlObject262;
import com.mojang.blaze3d.opengl.GlTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlTexture.class)
abstract class GlTextureBorrowedMixin262 implements BorrowedGlObject262 {
    @Shadow protected boolean closed;
    @Unique private boolean luminGraphics$borrowed262;

    @Override public boolean luminGraphics$isBorrowed262() { return luminGraphics$borrowed262; }
    @Override public void luminGraphics$setBorrowed262(boolean borrowed) { luminGraphics$borrowed262 = borrowed; }

    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void luminGraphics$closeBorrowed262(CallbackInfo callback) {
        if (luminGraphics$borrowed262) {
            closed = true;
            callback.cancel();
        }
    }
}
