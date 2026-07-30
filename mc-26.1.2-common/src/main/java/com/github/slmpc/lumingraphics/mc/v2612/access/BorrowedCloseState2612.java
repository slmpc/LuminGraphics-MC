package com.github.slmpc.lumingraphics.mc.v2612.access;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BorrowedCloseState2612 {
    private boolean borrowed;
    private Runnable release = () -> { };
    private final AtomicBoolean released = new AtomicBoolean();

    public void mark(Runnable action) {
        if (borrowed) throw new IllegalStateException("Blaze resource is already borrowed");
        borrowed = true;
        release = Objects.requireNonNull(action, "release");
    }

    public boolean borrowed() { return borrowed; }
    public void releaseOnce() { if (released.compareAndSet(false, true)) release.run(); }
}
