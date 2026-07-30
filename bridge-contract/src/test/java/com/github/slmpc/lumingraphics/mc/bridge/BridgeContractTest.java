package com.github.slmpc.lumingraphics.mc.bridge;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.RepeatedTest;
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
    void ownedCloseDestroysExactlyOnceAndAggregatesFailures() {
        BridgeContextIdentity context = BridgeContextIdentity.create("render");
        AtomicInteger destroys = new AtomicInteger();
        BridgeLease<String> lease = BridgeLease.owned("buffer", context, context.newInvalidationToken(), () -> {
            destroys.incrementAndGet();
            throw new IllegalStateException("destroy");
        }, () -> { throw new IllegalArgumentException("release"); });

        BridgeDestroyException failure = assertThrows(BridgeDestroyException.class, lease::close);
        assertEquals("destroy", failure.getCause().getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("release", failure.getSuppressed()[0].getCause().getMessage());
        assertDoesNotThrow(lease::close);
        assertEquals(1, destroys.get());
        assertThrows(BridgeClosedException.class, () -> lease.access(context));
    }

    @RepeatedTest(20)
    void concurrentInvalidationAndCloseRemainDeterministic() throws Exception {
        BridgeContextIdentity context = BridgeContextIdentity.create("render");
        BridgeInvalidationToken token = context.newInvalidationToken();
        AtomicInteger destroys = new AtomicInteger();
        BridgeLease<String> lease = BridgeLease.owned("pipeline", context, token, destroys::incrementAndGet);
        CountDownLatch start = new CountDownLatch(1);
        Thread invalidate = new Thread(() -> awaitAndRun(start, token::invalidate));
        Thread closeOne = new Thread(() -> awaitAndRun(start, () -> closeUnchecked(lease)));
        Thread closeTwo = new Thread(() -> awaitAndRun(start, () -> closeUnchecked(lease)));
        invalidate.start(); closeOne.start(); closeTwo.start(); start.countDown();
        invalidate.join(); closeOne.join(); closeTwo.join();
        assertEquals(1, destroys.get());
        assertFalse(token.isLive());
        assertThrows(BridgeClosedException.class, () -> lease.access(context));
    }

    private static void awaitAndRun(CountDownLatch start, Runnable action) {
        try { start.await(); action.run(); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }

    private static void closeUnchecked(BridgeLease<?> lease) {
        try { lease.close(); } catch (BridgeDestroyException error) { throw new AssertionError(error); }
    }
}
