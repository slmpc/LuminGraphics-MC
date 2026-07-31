package com.github.slmpc.lumingraphics.mc.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class BridgeContractTest {
    @Test
    void enumsAndUnsupportedReasonsAreExhaustive() {
        assertEquals(EnumSet.of(BridgeMode.BORROWED_ZERO_COPY, BridgeMode.COPIED, BridgeMode.REBUILT,
                BridgeMode.IN_PLACE_ADAPTER, BridgeMode.UNSUPPORTED), EnumSet.allOf(BridgeMode.class));
        assertEquals(EnumSet.of(BridgeDirection.MINECRAFT_TO_LUMIN, BridgeDirection.LUMIN_TO_MINECRAFT),
                EnumSet.allOf(BridgeDirection.class));
        assertEquals(EnumSet.of(BridgeOwnership.BORROWED, BridgeOwnership.OWNED),
                EnumSet.allOf(BridgeOwnership.class));
        assertEquals(EnumSet.of(BridgeUnsupportedReason.NO_NATIVE_HANDLE, BridgeUnsupportedReason.TYPE_MISMATCH,
                BridgeUnsupportedReason.BACKEND_MISMATCH, BridgeUnsupportedReason.CONTEXT_MISMATCH,
                BridgeUnsupportedReason.THREAD_MISMATCH, BridgeUnsupportedReason.TOKEN_INVALIDATED,
                BridgeUnsupportedReason.CLOSED, BridgeUnsupportedReason.OWNERSHIP_FORBIDDEN,
                BridgeUnsupportedReason.MC_SHAPE_CHANGED, BridgeUnsupportedReason.ZERO_COPY_UNSAFE,
                BridgeUnsupportedReason.VIEW_REQUIRES_PARENT, BridgeUnsupportedReason.COMMAND_OBJECT_NOT_RESOURCE),
                EnumSet.allOf(BridgeUnsupportedReason.class));
    }

    @Test
    void resultHasExhaustiveNonNullSuccessOrUnsupportedBranches() {
        BridgeResult<String> success = BridgeResult.success("ok");
        assertEquals("ok", success.orElseThrow());
        BridgeUnsupportedDetail detail = new BridgeUnsupportedDetail.State(
                BridgeUnsupportedReason.ZERO_COPY_UNSAFE, "copy required");
        BridgeResult<String> unsupported = BridgeResult.unsupported(detail);
        assertTrue(unsupported instanceof BridgeResult.Unsupported<String>);
        assertSame(detail, unsupported.unsupportedDetail().orElseThrow());
        assertThrows(BridgeUnsupportedException.class, unsupported::orElseThrow);
        assertThrows(NullPointerException.class, () -> BridgeResult.success(null));
        assertThrows(NullPointerException.class, () -> BridgeResult.unsupported(null));
    }

    @Test
    void identityIsNonforgeableAndDiagnosticIdIsStable() {
        BridgeContextIdentity first = BridgeContextIdentity.create("render");
        BridgeContextIdentity second = BridgeContextIdentity.create("render");
        assertNotEquals(first, second);
        assertNotEquals(first.diagnosticId(), second.diagnosticId());
        assertEquals(first.diagnosticId(), first.diagnosticId());
        assertEquals("render", first.diagnosticName());
        assertThrows(IllegalArgumentException.class, () -> BridgeContextIdentity.create(" "));
    }

    @Test
    void borrowedLeaseChecksContextThreadTokenAndCloseWithoutDestroying() throws Exception {
        BridgeContextIdentity context = BridgeContextIdentity.create("render");
        BridgeInvalidationToken token = context.newInvalidationToken();
        BridgeLease<String> lease = BridgeLease.borrowed("texture", context, token);
        assertEquals(BridgeOwnership.BORROWED, lease.ownership());
        assertEquals("texture", lease.access(context));
        assertThrows(BridgeWrongContextException.class,
                () -> lease.access(BridgeContextIdentity.create("other")));

        AtomicReference<Throwable> wrongThread = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try { lease.access(context); } catch (Throwable error) { wrongThread.set(error); }
        }, "wrong-thread");
        thread.start();
        thread.join();
        assertInstanceOf(BridgeWrongThreadException.class, wrongThread.get());

        token.invalidate();
        assertThrows(BridgeInvalidatedException.class, () -> lease.access(context));
        lease.close();
        lease.close();
        assertThrows(BridgeClosedException.class, () -> lease.access(context));
    }

    @Test
    void invalidLeaseArgumentsAreRejected() {
        BridgeContextIdentity context = BridgeContextIdentity.create("render");
        BridgeContextIdentity other = BridgeContextIdentity.create("other");
        BridgeInvalidationToken token = context.newInvalidationToken();
        assertThrows(NullPointerException.class, () -> BridgeLease.borrowed(null, context, token));
        assertThrows(NullPointerException.class, () -> BridgeLease.borrowed("x", null, token));
        assertThrows(NullPointerException.class, () -> BridgeLease.borrowed("x", context, null));
        assertThrows(IllegalArgumentException.class,
                () -> BridgeLease.borrowed("x", other, token));
        assertThrows(NullPointerException.class,
                () -> BridgeLease.owned("x", context, token, null));
        BridgeLease<String> lease = BridgeLease.borrowed("x", context, token);
        assertThrows(NullPointerException.class, () -> lease.access(null));
    }

    @Test
    void ownedCloseRejectsWrongThreadBeforeLifecycleActionsAndOwnerCanClose() throws Exception {
        BridgeContextIdentity context = BridgeContextIdentity.create("render");
        AtomicInteger destroys = new AtomicInteger();
        AtomicInteger releases = new AtomicInteger();
        BridgeLease<String> lease = BridgeLease.owned("buffer", context, context.newInvalidationToken(),
                destroys::incrementAndGet, releases::incrementAndGet);

        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread wrongThread = new Thread(() -> {
            try { lease.close(); } catch (Throwable error) { failure.set(error); }
        }, "wrong-thread");
        wrongThread.start();
        wrongThread.join();

        assertInstanceOf(BridgeWrongThreadException.class, failure.get());
        assertEquals(0, destroys.get());
        assertEquals(0, releases.get());
        assertFalse(lease.isClosed());
        lease.close();
        assertEquals(1, destroys.get());
        assertEquals(1, releases.get());
        assertTrue(lease.isClosed());
        lease.close();
        assertEquals(1, destroys.get());
        assertEquals(1, releases.get());
    }

    @Test
    void ownedCloseRejectsInvalidatedTokenBeforeLifecycleActions() {
        BridgeContextIdentity context = BridgeContextIdentity.create("render");
        BridgeInvalidationToken token = context.newInvalidationToken();
        AtomicInteger destroys = new AtomicInteger();
        AtomicInteger releases = new AtomicInteger();
        BridgeLease<String> lease = BridgeLease.owned("pipeline", context, token,
                destroys::incrementAndGet, releases::incrementAndGet);
        token.invalidate();

        assertThrows(BridgeInvalidatedException.class, lease::close);
        assertEquals(0, destroys.get());
        assertEquals(0, releases.get());
        assertFalse(lease.isClosed());
    }

    @Test
    void ownedCloseRetriesOnlyFailedPhasesAndAggregatesFailures() throws Exception {
        BridgeContextIdentity context = BridgeContextIdentity.create("render");
        AtomicInteger destroyAttempts = new AtomicInteger();
        AtomicInteger releaseAttempts = new AtomicInteger();
        BridgeLease<String> bothFail = BridgeLease.owned("buffer", context, context.newInvalidationToken(), () -> {
            if (destroyAttempts.incrementAndGet() == 1) throw new IllegalStateException("destroy");
        }, () -> {
            if (releaseAttempts.incrementAndGet() == 1) throw new IllegalArgumentException("release");
        });

        BridgeDestroyException firstFailure = assertThrows(BridgeDestroyException.class, bothFail::close);
        assertEquals("destroy", firstFailure.getCause().getMessage());
        assertEquals(1, firstFailure.getSuppressed().length);
        assertEquals("release", firstFailure.getSuppressed()[0].getCause().getMessage());
        assertFalse(bothFail.isClosed());
        bothFail.close();
        assertEquals(2, destroyAttempts.get());
        assertEquals(2, releaseAttempts.get());
        assertTrue(bothFail.isClosed());
        bothFail.close();
        assertEquals(2, destroyAttempts.get());
        assertEquals(2, releaseAttempts.get());

        AtomicInteger successfulDestroyAttempts = new AtomicInteger();
        AtomicInteger failedReleaseAttempts = new AtomicInteger();
        BridgeLease<String> releaseRetry = BridgeLease.owned("pipeline", context, context.newInvalidationToken(),
                successfulDestroyAttempts::incrementAndGet, () -> {
                    if (failedReleaseAttempts.incrementAndGet() == 1) throw new IllegalStateException("release");
                });

        assertThrows(BridgeDestroyException.class, releaseRetry::close);
        assertFalse(releaseRetry.isClosed());
        releaseRetry.close();
        assertEquals(1, successfulDestroyAttempts.get());
        assertEquals(2, failedReleaseAttempts.get());
        assertTrue(releaseRetry.isClosed());
        releaseRetry.close();
        assertEquals(1, successfulDestroyAttempts.get());
        assertEquals(2, failedReleaseAttempts.get());
    }

}
