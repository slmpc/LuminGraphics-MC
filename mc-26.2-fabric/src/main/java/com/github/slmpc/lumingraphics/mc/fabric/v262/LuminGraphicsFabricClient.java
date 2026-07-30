package com.github.slmpc.lumingraphics.mc.fabric.v262;

import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalContext;
import com.github.slmpc.prismrhi.context.RhiContextIdentity;
import com.github.slmpc.prismrhi.context.RhiInvalidationToken;
import com.github.slmpc.lumingraphics.mc.v262.smoke.RealClientBridgeSmoke262;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.lwjgl.opengl.GL;

public final class LuminGraphicsFabricClient implements ClientModInitializer {
    private static OpenGlExternalContext context;
    private static RhiInvalidationToken invalidation;

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
                RealClientBridgeSmoke262.runIfEnabled(client, bindCurrentContext(), "fabric"));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> dispose());
    }

    public static synchronized OpenGlExternalContext bindCurrentContext() {
        RenderSystem.assertOnRenderThread();
        if (context != null) context.requireCurrent();
        if (context != null) return context;
        invalidation = new RhiInvalidationToken();
        var capabilities = GL.getCapabilities();
        var identity = new RhiContextIdentity(
                Integer.toUnsignedLong(System.identityHashCode(capabilities)) + 1L,
                "minecraft-26.2-render-context");
        context = new OpenGlExternalContext(capabilities, Thread.currentThread(), identity, invalidation,
                expected -> GL.getCapabilities() == capabilities && identity.equals(expected));
        return context;
    }

    public static synchronized void dispose() {
        RenderSystem.assertOnRenderThread();
        if (invalidation != null) invalidation.close();
        invalidation = null;
        context = null;
    }
}
