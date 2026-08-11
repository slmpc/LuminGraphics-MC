package com.github.slmpc.lumingraphics.mc.v1211.neoforge;

import com.github.slmpc.lumingraphics.mc.v1211.runtime.MinecraftUiRuntime1211;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = LuminGraphicsNeoForge1211.MOD_ID, dist = Dist.CLIENT)
public final class LuminGraphicsNeoForge1211 {
    public static final String MOD_ID = "lumin_graphics_mc";
    private MinecraftUiRuntime1211 runtime;

    public LuminGraphicsNeoForge1211() {
        NeoForge.EVENT_BUS.addListener(this::bindOnFirstClientTick);
    }

    private void bindOnFirstClientTick(ClientTickEvent.Post event) {
        if (runtime == null) runtime = MinecraftUiRuntime1211.bindCurrent(Minecraft.getInstance());
    }
}
