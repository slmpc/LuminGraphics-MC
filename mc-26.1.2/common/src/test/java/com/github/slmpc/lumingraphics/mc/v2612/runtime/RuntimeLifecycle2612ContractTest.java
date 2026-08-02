package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RuntimeLifecycle2612ContractTest {
    @Test
    void rejectsWrongThreadAndStaleContext() throws Exception {
        Thread owner = Thread.currentThread();
        AtomicBoolean current = new AtomicBoolean(true);
        RuntimeLifecycle2612 lifecycle = new RuntimeLifecycle2612(() -> {
            if (Thread.currentThread() != owner) throw new IllegalStateException("wrong thread");
            if (!current.get()) throw new IllegalStateException("stale context");
        });

        AtomicReference<RuntimeException> wrongThread = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try { lifecycle.requireAccess(); }
            catch (RuntimeException failure) { wrongThread.set(failure); }
        }, "wrong-render-thread");
        worker.start();
        worker.join();
        assertEquals("wrong thread", wrongThread.get().getMessage());

        current.set(false);
        assertEquals("stale context", assertThrows(IllegalStateException.class, lifecycle::requireAccess).getMessage());
    }

    @Test
    void abortDoesNotSubmitAndTargetReplacementClosesOnlyWrappers() {
        List<String> events = new ArrayList<>();
        AtomicInteger borrowedOwnerCloses = new AtomicInteger();
        AtomicBoolean replacement = new AtomicBoolean();
        BorrowedTargetCache2612<String, AutoCloseable> targets = new BorrowedTargetCache2612<>(source ->
                () -> events.add("wrapper-close:" + source));
        FrameCoordinator2612 frames = new FrameCoordinator2612(new FrameCoordinator2612.Driver() {
            @Override public FrameCoordinator2612.TargetLease acquireTarget() {
                String source = replacement.get() ? "second" : "first";
                return targets.acquire(new BorrowedTargetCache2612.Source<>(source, 4, 4, source));
            }
            @Override public void resetCommandBuffer() { events.add("reset"); }
            @Override public void beginCommandBuffer() { events.add("begin"); }
            @Override public void endCommandBuffer() { events.add("end"); }
            @Override public void submitCommandBuffer() { events.add("submit"); }
        });

        frames.beginFrame(1);
        assertFalse(frames.currentTarget() == null);
        assertEquals(1, frames.abortFrame());
        replacement.set(true);
        frames.beginFrame(2);
        assertFalse(frames.currentTarget() == null);
        assertEquals(2, frames.endFrame());
        targets.close();

        assertEquals(List.of("reset", "begin", "end", "reset", "begin", "wrapper-close:first",
                "end", "submit", "wrapper-close:second"), events);
        assertEquals(0, borrowedOwnerCloses.get(), "runtime must not close the borrowed Minecraft owner");
    }

    @Test
    void targetIsAcquiredAfterResizeWhenGuiFirstUsesTheFrame() {
        AtomicReference<String> currentTarget = new AtomicReference<>("before-resize");
        FrameCoordinator2612 frames = new FrameCoordinator2612(new FrameCoordinator2612.Driver() {
            @Override public FrameCoordinator2612.TargetLease acquireTarget() {
                String acquired = currentTarget.get();
                return new FrameCoordinator2612.TargetLease() {
                    @Override public Object target() { return acquired; }
                    @Override public void close() { }
                };
            }
            @Override public void resetCommandBuffer() { }
            @Override public void beginCommandBuffer() { }
            @Override public void endCommandBuffer() { }
            @Override public void submitCommandBuffer() { }
        });

        frames.beginFrame(1);
        currentTarget.set("after-resize");

        assertEquals("after-resize", frames.currentTarget());
        frames.endFrame();
    }

    @Test
    void repeatedCloseIsIdempotentAndNeverClosesBorrowedOwner() {
        List<String> closed = new ArrayList<>();
        RuntimeLifecycle2612 lifecycle = new RuntimeLifecycle2612(() -> { });
        lifecycle.register(() -> closed.add("wrapper"));

        lifecycle.close(() -> closed.add("lumin"), () -> closed.add("device"),
                () -> closed.add("instance"), () -> closed.add("token"));
        lifecycle.close(() -> closed.add("unexpected"), null, null, null);

        assertEquals(List.of("lumin", "wrapper", "device", "instance", "token"), closed);
        assertFalse(lifecycle.acceptingSubmissions());
    }
}
