package com.github.slmpc.lumingraphics.mc.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class BridgeCapabilityTest {
    @Test
    void nativeCompatibilityChecksEveryRequiredDimension() throws Exception {
        BridgeContextIdentity context = BridgeContextIdentity.create("render");
        BridgeInvalidationToken token = context.newInvalidationToken();
        Object capabilities = new Object();
        AtomicReference<Object> currentCapabilities = new AtomicReference<>(capabilities);
        AtomicReference<BridgeContextIdentity> currentContext = new AtomicReference<>(context);
        BridgeCapability capability = BridgeCapability.openGl("texture", "Minecraft GL texture", 42L,
                context, token, () -> true, capabilities, currentCapabilities::get, currentContext::get);

        assertTrue(capability.audit("texture", "Minecraft GL texture", "opengl", context).isCompatible());
        assertEquals(BridgeUnsupportedReason.TYPE_MISMATCH,
                capability.audit("buffer", "Minecraft GL texture", "opengl", context).reason().orElseThrow());
        assertEquals(BridgeUnsupportedReason.TYPE_MISMATCH,
                capability.audit("texture", "other", "opengl", context).reason().orElseThrow());
        assertEquals(BridgeUnsupportedReason.BACKEND_MISMATCH,
                capability.audit("texture", "Minecraft GL texture", "vulkan", context).reason().orElseThrow());
        assertEquals(BridgeUnsupportedReason.CONTEXT_MISMATCH,
                capability.audit("texture", "Minecraft GL texture", "opengl", BridgeContextIdentity.create("x"))
                        .reason().orElseThrow());

        currentCapabilities.set(new Object());
        assertEquals(BridgeUnsupportedReason.CONTEXT_MISMATCH,
                capability.audit("texture", "Minecraft GL texture", "opengl", context).reason().orElseThrow());
        currentCapabilities.set(capabilities);
        currentContext.set(BridgeContextIdentity.create("replacement"));
        assertEquals(BridgeUnsupportedReason.CONTEXT_MISMATCH,
                capability.audit("texture", "Minecraft GL texture", "opengl", context).reason().orElseThrow());
        currentContext.set(context);

        AtomicReference<BridgeUnsupportedReason> wrongThread = new AtomicReference<>();
        Thread thread = new Thread(() -> wrongThread.set(capability
                .audit("texture", "Minecraft GL texture", "opengl", context).reason().orElseThrow()));
        thread.start(); thread.join();
        assertEquals(BridgeUnsupportedReason.THREAD_MISMATCH, wrongThread.get());
        token.invalidate();
        assertEquals(BridgeUnsupportedReason.TOKEN_INVALIDATED,
                capability.audit("texture", "Minecraft GL texture", "opengl", context).reason().orElseThrow());
    }

    @Test
    void invalidCapabilityArgumentsAndNoHandleAreTyped() {
        BridgeContextIdentity context = BridgeContextIdentity.create("render");
        BridgeCapability noHandle = BridgeCapability.nativeResource("texture", "texture", "vulkan", 0,
                context, context.newInvalidationToken(), () -> true);
        assertEquals(BridgeUnsupportedReason.NO_NATIVE_HANDLE,
                noHandle.audit("texture", "texture", "vulkan", context).reason().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> BridgeCapability.nativeResource("", "x", "opengl", 1,
                context, context.newInvalidationToken(), () -> true));
        assertThrows(NullPointerException.class, () -> noHandle.audit(null, "texture", "vulkan", context));
        assertThrows(NullPointerException.class, () -> BridgeCapability.nativeResource("texture", "texture",
                "vulkan", 1, context, context.newInvalidationToken(), null));
    }

    @Test
    void closedNativeResourceIsTypedIndependentlyFromTokenInvalidation() {
        BridgeContextIdentity context = BridgeContextIdentity.create("render");
        BridgeInvalidationToken token = context.newInvalidationToken();
        java.util.concurrent.atomic.AtomicBoolean resourceLive = new java.util.concurrent.atomic.AtomicBoolean(true);
        BridgeCapability capability = BridgeCapability.nativeResource("buffer", "native buffer", "vulkan", 7,
                context, token, resourceLive::get);
        assertTrue(capability.audit("buffer", "native buffer", "vulkan", context).isCompatible());
        resourceLive.set(false);
        BridgeCompatibilityAudit audit = capability.audit("buffer", "native buffer", "vulkan", context);
        assertEquals(BridgeUnsupportedReason.CLOSED, audit.reason().orElseThrow());
        assertInstanceOf(BridgeUnsupportedDetail.State.class, audit.detail());
        assertTrue(token.isLive(), "resource CLOSED must not be represented by token invalidation");
    }
}
