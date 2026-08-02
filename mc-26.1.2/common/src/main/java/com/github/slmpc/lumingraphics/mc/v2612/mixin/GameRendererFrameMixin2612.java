package com.github.slmpc.lumingraphics.mc.v2612.mixin;

import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftGraphicsRuntime2612;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在 Minecraft 将主目标复制到窗口前提交本帧 Lumin 2D 命令。 */
@Mixin(GameRenderer.class)
public final class GameRendererFrameMixin2612 {
    @Inject(method = "render", at = @At("RETURN"))
    private void lumin$submitBeforeMainTargetBlit(DeltaTracker deltaTracker, boolean advanceGameTime,
                                                   CallbackInfo callback) {
        MinecraftGraphicsRuntime2612 runtime = runtimeOrNull();
        if (runtime != null && runtime.frameActive()) runtime.endFrame();
    }

    private static MinecraftGraphicsRuntime2612 runtimeOrNull() {
        try {
            return MinecraftGraphicsRuntime2612.current();
        } catch (IllegalStateException unavailable) {
            return null;
        }
    }
}
