package com.github.slmpc.lumingraphics.mc.bridge;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BridgeLease<T> implements AutoCloseable {
    @FunctionalInterface
    public interface CloseAction { void run() throws Exception; }

    private static final CloseAction NO_OP = () -> { };
    private static final Runnable NO_OP_GUARD = () -> { };
    private final T value;
    private final BridgeContextIdentity context;
    private final BridgeInvalidationToken token;
    private final BridgeOwnership ownership;
    private final Runnable closeGuard;
    private final CloseAction destroyAction;
    private final CloseAction releaseAction;
    private final AtomicBoolean closed = new AtomicBoolean();
    private boolean destroyed;
    private boolean released;
    private boolean closing;

    private BridgeLease(T value, BridgeContextIdentity context, BridgeInvalidationToken token,
                        BridgeOwnership ownership, Runnable closeGuard, CloseAction destroyAction,
                        CloseAction releaseAction) {
        this.value = Objects.requireNonNull(value, "value");
        this.context = Objects.requireNonNull(context, "context");
        this.token = Objects.requireNonNull(token, "token");
        if (token.context() != context) throw new IllegalArgumentException("token belongs to a different context");
        this.ownership = ownership;
        this.closeGuard = Objects.requireNonNull(closeGuard, "closeGuard");
        this.destroyAction = destroyAction;
        this.releaseAction = releaseAction;
    }

    public static <T> BridgeLease<T> borrowed(T value, BridgeContextIdentity context,
                                               BridgeInvalidationToken token) {
        return new BridgeLease<>(value, context, token, BridgeOwnership.BORROWED, NO_OP_GUARD, NO_OP, NO_OP);
    }

    public static <T> BridgeLease<T> owned(T value, BridgeContextIdentity context,
                                            BridgeInvalidationToken token, Runnable closeGuard,
                                            CloseAction destroyAction) {
        return owned(value, context, token, closeGuard, destroyAction, NO_OP);
    }

    public static <T> BridgeLease<T> owned(T value, BridgeContextIdentity context,
                                            BridgeInvalidationToken token, Runnable closeGuard,
                                            CloseAction destroyAction, CloseAction releaseAction) {
        return new BridgeLease<>(value, context, token, BridgeOwnership.OWNED, closeGuard,
                Objects.requireNonNull(destroyAction, "destroyAction"),
                Objects.requireNonNull(releaseAction, "releaseAction"));
    }

    public T access(BridgeContextIdentity currentContext) {
        Objects.requireNonNull(currentContext, "currentContext");
        synchronized (this) {
            if (closed.get() || closing) throw new BridgeClosedException();
            if (currentContext != context) {
                throw new BridgeWrongContextException(context.diagnosticId(), currentContext.diagnosticId());
            }
            if (Thread.currentThread() != token.ownerThread()) {
                throw new BridgeWrongThreadException(token.ownerThread().getName(), Thread.currentThread().getName());
            }
            if (!token.isLive()) throw new BridgeInvalidatedException();
            if (closed.get() || closing) throw new BridgeClosedException();
            return value;
        }
    }

    public BridgeOwnership ownership() { return ownership; }
    public boolean isClosed() { return closed.get(); }

    @Override
    public void close() throws BridgeDestroyException {
        boolean runDestroy;
        boolean runRelease;
        synchronized (this) {
            if (closed.get() || closing) return;
            if (ownership == BridgeOwnership.BORROWED) {
                closed.set(true);
                return;
            }
            if (Thread.currentThread() != token.ownerThread()) {
                throw new BridgeWrongThreadException(token.ownerThread().getName(), Thread.currentThread().getName());
            }
            closing = true;
            runDestroy = !destroyed;
            runRelease = !released;
        }
        try {
            closeGuard.run();
            BridgeDestroyException failure = runDestroy ? runCloseAction("destroy", destroyAction) : null;
            if (failure == null && runDestroy) markDestroyed();
            BridgeDestroyException releaseFailure = runRelease ? runCloseAction("release", releaseAction) : null;
            if (releaseFailure == null && runRelease) markReleased();
            if (failure != null && releaseFailure != null) failure.addSuppressed(releaseFailure);
            if (failure != null) throw failure;
            if (releaseFailure != null) throw releaseFailure;
        } finally {
            finishClose();
        }
    }

    private synchronized void markDestroyed() {
        destroyed = true;
    }

    private synchronized void markReleased() {
        released = true;
    }

    private synchronized void finishClose() {
        if (destroyed && released) closed.set(true);
        closing = false;
    }

    private static BridgeDestroyException runCloseAction(String phase, CloseAction action) {
        try { action.run(); return null; }
        catch (Exception error) { return new BridgeDestroyException(phase, error); }
    }
}
