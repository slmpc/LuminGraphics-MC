package com.github.slmpc.lumingraphics.mc.v262.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.github.slmpc.lumingraphics.mc.bridge.BridgeCapability;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeContextIdentity;
import com.github.slmpc.lumingraphics.mc.bridge.BridgeUnsupportedReason;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class Blaze3DBridge262ContextTest {
    @Test void exactCurrentContextIsRequired() {
        BridgeContextIdentity owner = BridgeContextIdentity.create("mc262-owner");
        BridgeContextIdentity other = BridgeContextIdentity.create("mc262-other");
        var token = owner.newInvalidationToken();
        Object capabilities = new Object();
        var capability = BridgeCapability.openGl("texture", "RhiImage", 17, owner, token,
                () -> true, capabilities, () -> capabilities, () -> other);
        var audit = Blaze3DBridge262.audit262(capability, "texture", "RhiImage", owner);
        assertFalse(audit.isCompatible());
        assertEquals(BridgeUnsupportedReason.CONTEXT_MISMATCH, audit.reason().orElseThrow());
    }

    @Test void staleAndClosedCapabilitiesFailClosed() {
        BridgeContextIdentity owner = BridgeContextIdentity.create("mc262-owner");
        var token = owner.newInvalidationToken();
        Object capabilities = new Object();
        AtomicBoolean live = new AtomicBoolean(true);
        var capability = BridgeCapability.openGl("buffer", "RhiBuffer", 23, owner, token,
                live::get, capabilities, () -> capabilities, () -> owner);
        token.invalidate();
        assertEquals(BridgeUnsupportedReason.TOKEN_INVALIDATED,
                Blaze3DBridge262.audit262(capability, "buffer", "RhiBuffer", owner).reason().orElseThrow());

        var liveToken = owner.newInvalidationToken();
        var closed = BridgeCapability.openGl("buffer", "RhiBuffer", 23, owner, liveToken,
                () -> false, capabilities, () -> capabilities, () -> owner);
        assertEquals(BridgeUnsupportedReason.CLOSED,
                Blaze3DBridge262.audit262(closed, "buffer", "RhiBuffer", owner).reason().orElseThrow());
    }

    @Test void wrongThreadAndSubtypeAreTyped() throws Exception {
        BridgeContextIdentity owner = BridgeContextIdentity.create("mc262-owner");
        var token = owner.newInvalidationToken();
        Object capabilities = new Object();
        var capability = BridgeCapability.openGl("shader-module", "RhiShader", 31, owner, token,
                () -> true, capabilities, () -> capabilities, () -> owner);
        AtomicReference<BridgeUnsupportedReason> reason = new AtomicReference<>();
        Thread thread = new Thread(() -> reason.set(Blaze3DBridge262.audit262(capability,
                "shader-module", "RhiShader", owner).reason().orElseThrow()), "mc262-wrong-thread");
        thread.start();
        thread.join();
        assertEquals(BridgeUnsupportedReason.THREAD_MISMATCH, reason.get());
        assertEquals(BridgeUnsupportedReason.TYPE_MISMATCH,
                Blaze3DBridge262.audit262(capability, "texture", "RhiShader", owner).reason().orElseThrow());
    }
}
