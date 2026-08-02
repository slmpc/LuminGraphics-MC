package com.github.slmpc.lumingraphics.mc.v2612.mixin;

import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftGraphicsRuntime2612;
import com.mojang.blaze3d.TracyFrameCapture;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.concurrent.atomic.AtomicLong;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 由 LuminGraphics-MC 统一维护 Minecraft 2D 渲染帧。 */
@Mixin(RenderSystem.class)
public final class RenderSystemFrameMixin2612 {
    private static final AtomicLong LUMIN_MC_FRAME_ID = new AtomicLong();

    @Inject(method = "flipFrame", at = @At("HEAD"))
    private static void luminMcEndFrame(TracyFrameCapture capture, CallbackInfo callback) {
        MinecraftGraphicsRuntime2612 runtime = runtimeOrNull();
        if (runtime != null && runtime.frameActive()) runtime.endFrame();
    }

    @Inject(method = "flipFrame", at = @At("RETURN"))
    private static void luminMcBeginFrame(TracyFrameCapture capture, CallbackInfo callback) {
        MinecraftGraphicsRuntime2612 runtime = runtimeOrNull();
        if (runtime != null && !runtime.frameActive()) {
            runtime.beginFrame(LUMIN_MC_FRAME_ID.incrementAndGet());
        }
    }

    private static MinecraftGraphicsRuntime2612 runtimeOrNull() {
        try {
            return MinecraftGraphicsRuntime2612.current();
        } catch (IllegalStateException unavailable) {
            return null;
        }
    }
}
