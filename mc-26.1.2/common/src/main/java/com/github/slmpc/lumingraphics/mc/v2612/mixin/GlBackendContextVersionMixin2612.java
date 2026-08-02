package com.github.slmpc.lumingraphics.mc.v2612.mixin;

import com.mojang.blaze3d.opengl.GlBackend;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将原版默认的 3.3 context 请求提升至 Lumin OpenGL 后端的最低版本。 */
@Mixin(GlBackend.class)
final class GlBackendContextVersionMixin2612 {
    @Inject(method = "setWindowHints", at = @At("TAIL"))
    private void lumin$requireOpenGl41(CallbackInfo callback) {
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 1);
    }
}
