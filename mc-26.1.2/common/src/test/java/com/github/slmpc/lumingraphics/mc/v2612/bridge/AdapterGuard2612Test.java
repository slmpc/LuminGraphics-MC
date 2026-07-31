package com.github.slmpc.lumingraphics.mc.v2612.bridge;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.slmpc.prismrhi.RhiResourceClosedException;
import com.github.slmpc.prismrhi.RhiResourceInvalidatedException;
import com.github.slmpc.prismrhi.context.RhiInvalidationToken;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassBackend;
import java.lang.reflect.Proxy;
import java.util.concurrent.FutureTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class AdapterGuard2612Test {
    @Test
    void everyPublicDelegateExposureReturnsOnTheExactValidOwnerContext() {
        Guard guard = new Guard();
        Adapters adapters = adapters(guard);

        assertSame(adapters.encoder, adapters.encoderAdapter.delegate());
        assertSame(adapters.encoder, adapters.encoderAdapter.access());
        assertSame(adapters.pass, adapters.passAdapter.delegate());
        assertSame(adapters.pass, adapters.passAdapter.access());
    }

    @Test
    void delegateExposureRejectsTheWrongCurrentContext() {
        Guard guard = new Guard();
        Adapters adapters = adapters(guard);
        guard.current = false;

        assertThrows(IllegalStateException.class, adapters.encoderAdapter::delegate);
        assertThrows(IllegalStateException.class, adapters.passAdapter::delegate);
    }

    @Test
    void delegateExposureRejectsTheWrongOwnerThread() throws Exception {
        Guard guard = new Guard();
        Adapters adapters = adapters(guard);

        assertInstanceOf(IllegalStateException.class, offThread(adapters.encoderAdapter::delegate));
        assertInstanceOf(IllegalStateException.class, offThread(adapters.passAdapter::delegate));
    }

    @Test
    void delegateExposureRejectsAnInvalidatedOwnerToken() {
        Guard guard = new Guard();
        Adapters adapters = adapters(guard);
        guard.token.invalidate();

        assertThrows(RhiResourceInvalidatedException.class, adapters.encoderAdapter::delegate);
        assertThrows(RhiResourceInvalidatedException.class, adapters.passAdapter::delegate);
    }

    @Test
    void delegateExposureRejectsAClosedOwnerToken() {
        Guard guard = new Guard();
        Adapters adapters = adapters(guard);
        guard.token.close();

        assertThrows(RhiResourceClosedException.class, adapters.encoderAdapter::delegate);
        assertThrows(RhiResourceClosedException.class, adapters.passAdapter::delegate);
    }

    private static Adapters adapters(Guard guard) {
        CommandEncoder encoder = new CommandEncoder(proxy(GpuDeviceBackend.class),
                proxy(CommandEncoderBackend.class));
        RenderPass pass = new RenderPass(proxy(RenderPassBackend.class), proxy(GpuDeviceBackend.class));
        return new Adapters(encoder, pass,
                new CommandEncoderAdapter2612(encoder, guard::requireCurrent),
                new RenderPassAdapter2612(pass, guard::requireCurrent));
    }

    private static Throwable offThread(Executable executable) throws Exception {
        FutureTask<Throwable> task = new FutureTask<>(() -> {
            try {
                executable.execute();
                return null;
            } catch (Throwable failure) {
                return failure;
            }
        });
        Thread thread = new Thread(task, "adapter-wrong-owner");
        thread.start();
        return task.get();
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
    }

    private static final class Guard {
        private final Thread owner = Thread.currentThread();
        private final RhiInvalidationToken token = new RhiInvalidationToken();
        private boolean current = true;

        private void requireCurrent() {
            token.requireValid();
            if (Thread.currentThread() != owner || !current) {
                throw new IllegalStateException("exact owner context is not current");
            }
        }
    }

    private record Adapters(CommandEncoder encoder, RenderPass pass,
                            CommandEncoderAdapter2612 encoderAdapter,
                            RenderPassAdapter2612 passAdapter) { }
}
