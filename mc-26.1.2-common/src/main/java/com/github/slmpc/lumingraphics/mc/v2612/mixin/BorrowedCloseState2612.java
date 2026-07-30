package com.github.slmpc.lumingraphics.mc.v2612.mixin;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class BorrowedCloseState2612 {
    private boolean borrowed;
    private Runnable release = () -> { };
    private final AtomicBoolean released = new AtomicBoolean();

    void mark(Runnable action) {
        if (borrowed) throw new IllegalStateException("Blaze resource is already borrowed");
        borrowed = true;
        release = Objects.requireNonNull(action, "release");
    }

    boolean borrowed() { return borrowed; }
    void releaseOnce() { if (released.compareAndSet(false, true)) release.run(); }
}
