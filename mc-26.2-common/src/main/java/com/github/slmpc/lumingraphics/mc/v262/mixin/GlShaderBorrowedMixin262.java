package com.github.slmpc.lumingraphics.mc.v262.mixin;

import com.github.slmpc.lumingraphics.mc.v262.access.GlShaderModuleAccess262;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.shaders.ShaderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlShaderModule.class)
abstract class GlShaderBorrowedMixin262 implements GlShaderModuleAccess262 {
    @Shadow private int shaderId;
    @Shadow @Final private ShaderType type;
    @Unique private boolean luminGraphics$borrowed262;

    @Override public ShaderType luminGraphics$type262() { return type; }
    @Override public boolean luminGraphics$isBorrowed262() { return luminGraphics$borrowed262; }
    @Override public void luminGraphics$setBorrowed262(boolean borrowed) { luminGraphics$borrowed262 = borrowed; }

    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void luminGraphics$closeBorrowed262(CallbackInfo callback) {
        if (luminGraphics$borrowed262) {
            shaderId = -1;
            callback.cancel();
        }
    }
}
