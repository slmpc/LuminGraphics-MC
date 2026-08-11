package com.github.slmpc.lumingraphics.mc.fabric.v1211.mixin;

import com.mojang.blaze3d.platform.DisplayData;
import com.mojang.blaze3d.platform.ScreenManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.WindowEventHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric 没有 EarlyWindow，在原版创建窗口前请求 Lumin 后端所需的 OpenGL 4.1。 */
@Mixin(Window.class)
final class GlContextVersionMixin1211 {
    @Inject(method = "<init>", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", ordinal = 5, shift = At.Shift.AFTER))
    private void lumin$requireOpenGl41(WindowEventHandler eventHandler, ScreenManager screenManager,
            DisplayData displayData, String preferredFullscreenVideoMode, String title, CallbackInfo callback) {
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 4);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 1);
    }
}
