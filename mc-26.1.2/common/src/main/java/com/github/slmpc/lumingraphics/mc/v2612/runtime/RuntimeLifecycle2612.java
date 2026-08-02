package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

final class RuntimeLifecycle2612 {
    private final Runnable currentContextCheck;
    private final Deque<AutoCloseable> resources = new ArrayDeque<>();
    private boolean acceptingSubmissions = true;
    private boolean closed;

    RuntimeLifecycle2612(Runnable currentContextCheck) {
        this.currentContextCheck = Objects.requireNonNull(currentContextCheck, "currentContextCheck");
    }

    synchronized void requireAccess() {
        if (!acceptingSubmissions || closed) {
            throw new IllegalStateException("Minecraft graphics runtime is closed or stopping submissions");
        }
        currentContextCheck.run();
    }

    synchronized boolean acceptingSubmissions() {
        return acceptingSubmissions && !closed;
    }

    synchronized <T extends AutoCloseable> T register(T resource) {
        Objects.requireNonNull(resource, "resource");
        requireAccess();
        resources.addFirst(resource);
        return resource;
    }

    synchronized void close(AutoCloseable luminContext, AutoCloseable device,
                            AutoCloseable instance, AutoCloseable ownedToken) {
        if (closed) return;
        acceptingSubmissions = false;
        closed = true;

        RuntimeException failure = null;
        try {
            currentContextCheck.run();
        } catch (RuntimeException exception) {
            failure = exception;
        }
        failure = GraphicsResourceCloser2612.merge(failure,
                GraphicsResourceCloser2612.closeReverse(luminContext));
        failure = GraphicsResourceCloser2612.merge(failure,
                GraphicsResourceCloser2612.closeReverse(resources));
        failure = GraphicsResourceCloser2612.merge(failure,
                GraphicsResourceCloser2612.closeReverse(device, instance, ownedToken));
        if (failure != null) throw failure;
    }
}
