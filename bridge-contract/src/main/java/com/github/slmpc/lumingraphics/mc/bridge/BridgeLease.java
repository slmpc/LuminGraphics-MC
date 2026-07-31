package com.github.slmpc.lumingraphics.mc.bridge;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BridgeLease<T> implements AutoCloseable {
    @FunctionalInterface
    public interface CloseAction { void run() throws Exception; }

    private static final CloseAction NO_OP = () -> { };
    private final T value;
    private final BridgeContextIdentity context;
    private final BridgeInvalidationToken token;
    private final BridgeOwnership ownership;
    private final CloseAction destroyAction;
    private final CloseAction releaseAction;
    private final AtomicBoolean closed = new AtomicBoolean();
    private boolean destroyed;
    private boolean released;

    private BridgeLease(T value, BridgeContextIdentity context, BridgeInvalidationToken token,
                        BridgeOwnership ownership, CloseAction destroyAction, CloseAction releaseAction) {
        this.value = Objects.requireNonNull(value, "value");
        this.context = Objects.requireNonNull(context, "context");
        this.token = Objects.requireNonNull(token, "token");
        if (token.context() != context) throw new IllegalArgumentException("token belongs to a different context");
        this.ownership = ownership;
        this.destroyAction = destroyAction;
        this.releaseAction = releaseAction;
    }

    public static <T> BridgeLease<T> borrowed(T value, BridgeContextIdentity context,
                                               BridgeInvalidationToken token) {
        return new BridgeLease<>(value, context, token, BridgeOwnership.BORROWED, NO_OP, NO_OP);
    }

    public static <T> BridgeLease<T> owned(T value, BridgeContextIdentity context,
                                            BridgeInvalidationToken token, CloseAction destroyAction) {
        return owned(value, context, token, destroyAction, NO_OP);
    }

    public static <T> BridgeLease<T> owned(T value, BridgeContextIdentity context,
                                            BridgeInvalidationToken token, CloseAction destroyAction,
                                            CloseAction releaseAction) {
        return new BridgeLease<>(value, context, token, BridgeOwnership.OWNED,
                Objects.requireNonNull(destroyAction, "destroyAction"),
                Objects.requireNonNull(releaseAction, "releaseAction"));
    }

    public T access(BridgeContextIdentity currentContext) {
        Objects.requireNonNull(currentContext, "currentContext");
        if (closed.get()) throw new BridgeClosedException();
        if (currentContext != context) {
            throw new BridgeWrongContextException(context.diagnosticId(), currentContext.diagnosticId());
        }
        if (Thread.currentThread() != token.ownerThread()) {
            throw new BridgeWrongThreadException(token.ownerThread().getName(), Thread.currentThread().getName());
        }
        if (!token.isLive()) throw new BridgeInvalidatedException();
        if (closed.get()) throw new BridgeClosedException();
        return value;
    }

    public BridgeOwnership ownership() { return ownership; }
    public boolean isClosed() { return closed.get(); }

    @Override
    public synchronized void close() throws BridgeDestroyException {
        if (closed.get()) return;
        if (ownership == BridgeOwnership.BORROWED) {
            closed.set(true);
            return;
        }
        if (Thread.currentThread() != token.ownerThread()) {
            throw new BridgeWrongThreadException(token.ownerThread().getName(), Thread.currentThread().getName());
        }
        if (!token.isLive()) throw new BridgeInvalidatedException();

        BridgeDestroyException failure = destroyed ? null : runCloseAction("destroy", destroyAction);
        if (failure == null) destroyed = true;
        BridgeDestroyException releaseFailure = released ? null : runCloseAction("release", releaseAction);
        if (releaseFailure == null) released = true;
        if (destroyed && released) closed.set(true);
        if (failure != null && releaseFailure != null) failure.addSuppressed(releaseFailure);
        if (failure != null) throw failure;
        if (releaseFailure != null) throw releaseFailure;
    }

    private static BridgeDestroyException runCloseAction(String phase, CloseAction action) {
        try { action.run(); return null; }
        catch (Exception error) { return new BridgeDestroyException(phase, error); }
    }
}
