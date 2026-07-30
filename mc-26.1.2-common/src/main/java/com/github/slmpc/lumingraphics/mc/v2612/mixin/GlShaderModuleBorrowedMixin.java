package com.github.slmpc.lumingraphics.mc.v2612.mixin;

import com.github.slmpc.lumingraphics.mc.v2612.access.BorrowedCloseState2612;

import com.github.slmpc.lumingraphics.mc.v2612.access.BorrowedBlazeResource2612;
import com.mojang.blaze3d.opengl.GlShaderModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlShaderModule.class)
abstract class GlShaderModuleBorrowedMixin implements BorrowedBlazeResource2612 {
    @Shadow private int shaderId;
    @Unique private final BorrowedCloseState2612 lumin$borrowed = new BorrowedCloseState2612();
    @Override public void lumin$markBorrowed(Runnable release) { lumin$borrowed.mark(release); }
    @Override public boolean lumin$isBorrowed() { return lumin$borrowed.borrowed(); }
    @Inject(method = "close()V", at = @At("HEAD"), cancellable = true)
    private void lumin$closeBorrowed(CallbackInfo ci) {
        if (!lumin$borrowed.borrowed()) return;
        shaderId = -1;
        lumin$borrowed.releaseOnce();
        ci.cancel();
    }
}
