package com.github.slmpc.lumingraphics.mc.fabric.v2612;

import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.mc.v2612.smoke.RealClientBridgeSmoke2612;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;

public final class LuminGraphicsFabricClient implements ClientModInitializer {
    private static MinecraftUiRuntime2612 runtime;

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            runtime = MinecraftUiRuntime2612.bindCurrent(client);
            RealClientBridgeSmoke2612.runIfEnabled(
                    client, runtime.graphicsRuntime().externalContext(), "fabric");
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> dispose());
    }

    public static synchronized MinecraftUiRuntime2612 runtime() {
        if (runtime == null) throw new IllegalStateException("LuminGraphics-MC runtime is not bound");
        return runtime;
    }

    public static synchronized void dispose() {
        if (runtime != null) runtime.close();
        runtime = null;
    }
}
