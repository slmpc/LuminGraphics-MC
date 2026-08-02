package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import java.util.Deque;

final class GraphicsResourceCloser2612 {
    private GraphicsResourceCloser2612() {
    }

    static RuntimeException closeReverse(AutoCloseable... resources) {
        RuntimeException failure = null;
        for (AutoCloseable resource : resources) failure = merge(failure, close(resource));
        return failure;
    }

    static RuntimeException closeReverse(Deque<? extends AutoCloseable> resources) {
        RuntimeException failure = null;
        while (!resources.isEmpty()) failure = merge(failure, close(resources.removeFirst()));
        return failure;
    }

    static RuntimeException merge(RuntimeException accumulated, RuntimeException next) {
        if (accumulated == null) return next;
        if (next != null) accumulated.addSuppressed(next);
        return accumulated;
    }

    private static RuntimeException close(AutoCloseable resource) {
        if (resource == null) return null;
        try {
            resource.close();
            return null;
        } catch (Exception exception) {
            return new IllegalStateException("Failed to close graphics resource "
                    + resource.getClass().getName(), exception);
        }
    }
}
