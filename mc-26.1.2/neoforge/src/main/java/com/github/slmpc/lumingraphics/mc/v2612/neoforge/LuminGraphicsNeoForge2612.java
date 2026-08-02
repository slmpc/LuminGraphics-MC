package com.github.slmpc.lumingraphics.mc.v2612.neoforge;

import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.mc.v2612.smoke.RealClientBridgeSmoke2612;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.client.Minecraft;

@Mod(value = LuminGraphicsNeoForge2612.MOD_ID, dist = Dist.CLIENT)
public final class LuminGraphicsNeoForge2612 {
    public static final String MOD_ID = "lumin_graphics_mc";

    private MinecraftUiRuntime2612 runtime;

    public LuminGraphicsNeoForge2612() {
        NeoForge.EVENT_BUS.addListener(this::bindOnFirstClientTick);
        NeoForge.EVENT_BUS.addListener(this::disposeOnClientStopping);
    }

    private void bindOnFirstClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (runtime == null) {
            runtime = MinecraftUiRuntime2612.bindCurrent(client);
        }
        RealClientBridgeSmoke2612.runIfEnabled(
                client, runtime.graphicsRuntime().externalContext(), "neoforge");
    }

    private void disposeOnClientStopping(ClientStoppingEvent event) {
        if (runtime != null) runtime.close();
        runtime = null;
    }
}
