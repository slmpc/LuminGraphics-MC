package com.github.slmpc.lumingraphics.mc.v1211.mixin;

import com.github.slmpc.lumingraphics.mc.v1211.runtime.MinecraftUiRuntime1211;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将 Lumin 命令缓冲生命周期绑定到 Minecraft 1.21.1 的完整渲染帧。 */
@Mixin(GameRenderer.class)
public final class GameRendererFrameMixin1211 {
    @Inject(method = "render", at = @At("HEAD"))
    private void lumin$beginFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        MinecraftUiRuntime1211 runtime = MinecraftUiRuntime1211.currentOrNull();
        if (runtime == null) runtime = MinecraftUiRuntime1211.bindCurrent(Minecraft.getInstance());
        if (runtime.graphicsRuntime().frameActive()) runtime.graphicsRuntime().endFrame();
        runtime.graphicsRuntime().beginFrame();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void lumin$endFrame(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        MinecraftUiRuntime1211 runtime = MinecraftUiRuntime1211.currentOrNull();
        if (runtime != null && runtime.graphicsRuntime().frameActive()) runtime.graphicsRuntime().endFrame();
    }
}
