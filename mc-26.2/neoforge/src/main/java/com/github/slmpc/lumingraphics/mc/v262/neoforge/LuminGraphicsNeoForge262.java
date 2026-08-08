package com.github.slmpc.lumingraphics.mc.v262.neoforge;

import com.github.slmpc.lumingraphics.mc.bridge.BridgeContextIdentity;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeInvalidationToken;
import com.github.slmpc.lumingraphics.mc.v262.smoke.RealClientBridgeSmoke262;
import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalContext;
import com.github.slmpc.prismrhi.context.PRhiContextIdentity;
import com.github.slmpc.prismrhi.context.PRhiInvalidationToken;
import com.mojang.blaze3d.systems.RenderSystem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.opengl.GL;
import net.minecraft.client.Minecraft;

@Mod(value = LuminGraphicsNeoForge262.MOD_ID, dist = Dist.CLIENT)
public final class LuminGraphicsNeoForge262 {
    public static final String MOD_ID = "lumin_graphics_mc";

    private BorrowedBridgeState state;
    private OpenGlExternalContext smokeContext;
    private PRhiInvalidationToken smokeInvalidation;

    public LuminGraphicsNeoForge262() {
        NeoForge.EVENT_BUS.addListener(this::bindOnFirstClientTick);
        NeoForge.EVENT_BUS.addListener(this::disposeOnClientStopping);
    }

    private void bindOnFirstClientTick(ClientTickEvent.Post event) {
        if (state == null) {
            state = BorrowedBridgeState.bindCurrentMinecraftContext();
        } else {
            state.requireCurrentMinecraftContext();
        }
        RealClientBridgeSmoke262.runIfEnabled(Minecraft.getInstance(), smokeContext(), "neoforge");
    }

    private void disposeOnClientStopping(ClientStoppingEvent event) {
        if (state != null) {
            state.dispose();
            state = null;
        }
        if (smokeInvalidation != null) smokeInvalidation.close();
        smokeInvalidation = null;
        smokeContext = null;
    }

    private OpenGlExternalContext smokeContext() {
        if (smokeContext != null) { smokeContext.requireCurrent(); return smokeContext; }
        var capabilities = GL.getCapabilities();
        smokeInvalidation = new PRhiInvalidationToken();
        var identity = new PRhiContextIdentity(Integer.toUnsignedLong(System.identityHashCode(capabilities)) + 1L,
                "minecraft-neoforge-26.2-render-context");
        smokeContext = new OpenGlExternalContext(capabilities, Thread.currentThread(), identity, smokeInvalidation,
                expected -> GL.getCapabilities() == capabilities && identity.equals(expected));
        return smokeContext;
    }

    private record BorrowedBridgeState(BridgeContextIdentity context, BridgeInvalidationToken token,
                                       Thread renderThread, Object glCapabilities) {
        private static BorrowedBridgeState bindCurrentMinecraftContext() {
            RenderSystem.assertOnRenderThread();
            Object capabilities = GL.getCapabilities();
            BridgeContextIdentity identity = BridgeContextIdentity.create("minecraft-neoforge-26.2");
            return new BorrowedBridgeState(identity, identity.newInvalidationToken(),
                    Thread.currentThread(), capabilities);
        }

        private void requireCurrentMinecraftContext() {
            RenderSystem.assertOnRenderThread();
            if (Thread.currentThread() != renderThread || GL.getCapabilities() != glCapabilities) {
                throw new IllegalStateException("Minecraft OpenGL context changed while the bridge was bound");
            }
        }

        private void dispose() {
            token.invalidate();
        }
    }
}
