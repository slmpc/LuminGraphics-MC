package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import java.util.Objects;

final class BorrowedTargetCache2612<S, T extends AutoCloseable> implements AutoCloseable {
    interface Factory<S, T extends AutoCloseable> { T create(S source); }
    record Source<S>(Object identity, int width, int height, S value) {
        Source {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(value, "value");
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Target dimensions must be positive");
        }
    }

    private final Factory<S, T> factory;
    private Object identity;
    private int width;
    private int height;
    private T target;
    private long generation;
    private boolean activeLease;
    private boolean closed;

    BorrowedTargetCache2612(Factory<S, T> factory) { this.factory = Objects.requireNonNull(factory, "factory"); }

    synchronized FrameCoordinator2612.TargetLease acquire(Source<S> source) {
        Objects.requireNonNull(source, "source");
        requireOpen();
        if (activeLease) throw new IllegalStateException("A borrowed render target lease is already active");
        if (target == null || !Objects.equals(identity, source.identity())
                || width != source.width() || height != source.height()) {
            closeTarget();
            target = Objects.requireNonNull(factory.create(source.value()), "borrowed target factory returned null");
            identity = source.identity();
            width = source.width();
            height = source.height();
            generation++;
        }
        activeLease = true;
        long leaseGeneration = generation;
        T leasedTarget = target;
        return new FrameCoordinator2612.TargetLease() {
            private boolean leaseClosed;
            @Override public synchronized T target() {
                synchronized (BorrowedTargetCache2612.this) {
                    if (leaseClosed || leaseGeneration != generation || target != leasedTarget) {
                        throw new IllegalStateException("Borrowed render target lease is stale");
                    }
                    return leasedTarget;
                }
            }
            @Override public synchronized void close() {
                synchronized (BorrowedTargetCache2612.this) {
                    if (leaseClosed) return;
                    leaseClosed = true;
                    activeLease = false;
                }
            }
        };
    }

    synchronized void invalidate(String reason) {
        Objects.requireNonNull(reason, "reason");
        requireOpen();
        if (activeLease) throw new IllegalStateException("Cannot invalidate target during active frame: " + reason);
        closeTarget();
        generation++;
    }

    @Override public synchronized void close() {
        if (closed) return;
        if (activeLease) throw new IllegalStateException("Cannot close target cache with active lease");
        closed = true;
        closeTarget();
        generation++;
    }

    private void closeTarget() {
        T previous = target;
        target = null;
        identity = null;
        width = 0;
        height = 0;
        if (previous == null) return;
        try { previous.close(); }
        catch (RuntimeException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("Failed to close target wrapper", exception); }
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Borrowed render target cache is closed");
    }
}
