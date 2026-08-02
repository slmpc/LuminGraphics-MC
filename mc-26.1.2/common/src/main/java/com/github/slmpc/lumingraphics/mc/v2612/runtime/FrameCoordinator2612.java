package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class FrameCoordinator2612 implements AutoCloseable {
    interface TargetLease extends AutoCloseable {
        Object target();
        @Override void close();
    }

    interface Driver {
        TargetLease acquireTarget();
        void resetCommandBuffer();
        void beginCommandBuffer();
        void endCommandBuffer();
        void submitCommandBuffer();
    }

    private final Driver driver;
    private final List<AutoCloseable> retiredAfterFrame = new ArrayList<>();
    private TargetLease targetLease;
    private long activeFrameId = -1;
    private long lastStartedFrameId = -1;
    private long lastEndedFrameId = -1;
    private boolean closed;

    FrameCoordinator2612(Driver driver) {
        this.driver = Objects.requireNonNull(driver, "driver");
    }

    synchronized void beginFrame(long frameId) {
        requireOpen();
        if (frameId < 0 || frameId <= lastStartedFrameId) {
            throw new IllegalArgumentException("Frame id must be non-negative and strictly increasing");
        }
        if (activeFrameId >= 0) throw new IllegalStateException("Frame " + activeFrameId + " is already active");
        try {
            driver.resetCommandBuffer();
            driver.beginCommandBuffer();
        } catch (RuntimeException failure) {
            throw failure;
        }
        activeFrameId = frameId;
        lastStartedFrameId = frameId;
    }

    synchronized long endFrame() { return finish(true); }
    synchronized long abortFrame() { return finish(false); }
    synchronized boolean frameActive() { return activeFrameId >= 0; }
    synchronized long activeFrameId() {
        if (activeFrameId < 0) throw new IllegalStateException("No graphics frame is active");
        return activeFrameId;
    }
    synchronized long lastEndedFrameId() { return lastEndedFrameId; }
    /**
     * 将录制命令引用的资源延迟到当前帧提交或放弃后释放。
     *
     * <p>OpenGL 后端在 submit 时才执行已录制命令，因此录制期间不能立即删除 buffer、image 或 descriptor。</p>
     */
    synchronized void retireAfterFrame(AutoCloseable resource) {
        requireOpen();
        AutoCloseable value = Objects.requireNonNull(resource, "resource");
        if (activeFrameId >= 0) {
            retiredAfterFrame.add(value);
            return;
        }
        RuntimeException failure = GraphicsResourceCloser2612.closeReverse(value);
        if (failure != null) throw failure;
    }
    synchronized Object currentTarget() {
        requireOpen();
        if (activeFrameId < 0) throw new IllegalStateException("No graphics frame is active");
        if (targetLease == null) {
            targetLease = Objects.requireNonNull(driver.acquireTarget(), "target lease");
        }
        return targetLease.target();
    }

    @Override public synchronized void close() {
        if (closed) return;
        RuntimeException failure = null;
        if (activeFrameId >= 0) {
            try { finish(false); } catch (RuntimeException exception) { failure = exception; }
        }
        failure = GraphicsResourceCloser2612.merge(failure, closeRetiredResources());
        closed = true;
        if (failure != null) throw failure;
    }

    private long finish(boolean submit) {
        requireOpen();
        if (activeFrameId < 0) throw new IllegalStateException("No graphics frame is active");
        RuntimeException failure = null;
        try {
            driver.endCommandBuffer();
            if (submit) driver.submitCommandBuffer();
        } catch (RuntimeException exception) { failure = exception; }
        try {
            if (targetLease != null) targetLease.close();
        } catch (RuntimeException cleanup) {
            failure = GraphicsResourceCloser2612.merge(failure, cleanup);
        }
        finally {
            targetLease = null;
            lastEndedFrameId = activeFrameId;
            activeFrameId = -1;
        }
        failure = GraphicsResourceCloser2612.merge(failure, closeRetiredResources());
        if (failure != null) throw failure;
        return lastEndedFrameId;
    }

    private RuntimeException closeRetiredResources() {
        RuntimeException failure = GraphicsResourceCloser2612.closeReverse(
                retiredAfterFrame.toArray(AutoCloseable[]::new));
        retiredAfterFrame.clear();
        return failure;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Minecraft graphics frame coordinator is closed");
    }
}
