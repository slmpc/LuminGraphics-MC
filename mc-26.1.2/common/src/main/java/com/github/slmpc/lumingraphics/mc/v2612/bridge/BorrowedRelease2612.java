package com.github.slmpc.lumingraphics.mc.v2612.bridge;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class BorrowedRelease2612 {
    private final AtomicBoolean marked = new AtomicBoolean();
    private final AtomicBoolean released = new AtomicBoolean();
    private Runnable action = () -> { };

    void mark(Runnable release) {
        if (!marked.compareAndSet(false, true)) throw new IllegalStateException("Resource is already borrowed");
        action = Objects.requireNonNull(release, "release");
    }

    void runOnce() { if (released.compareAndSet(false, true)) action.run(); }
}
