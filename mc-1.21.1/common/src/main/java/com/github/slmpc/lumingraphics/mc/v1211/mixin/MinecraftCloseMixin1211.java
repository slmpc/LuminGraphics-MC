package com.github.slmpc.lumingraphics.mc.v1211.mixin;

import com.github.slmpc.lumingraphics.mc.v1211.runtime.MinecraftUiRuntime1211;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftCloseMixin1211 {
    @Inject(method = "close", at = @At("HEAD"))
    private void lumin$closeRuntime(CallbackInfo ci) {
        MinecraftUiRuntime1211 runtime = MinecraftUiRuntime1211.currentOrNull();
        if (runtime != null) runtime.close();
    }
}
