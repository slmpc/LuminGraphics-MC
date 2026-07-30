package com.github.slmpc.lumingraphics.mc.bridge;

import java.util.concurrent.atomic.AtomicBoolean;

public final class BridgeInvalidationToken {
    private final BridgeContextIdentity context;
    private final Thread ownerThread;
    private final AtomicBoolean live = new AtomicBoolean(true);

    BridgeInvalidationToken(BridgeContextIdentity context, Thread ownerThread) {
        this.context = context;
        this.ownerThread = ownerThread;
    }

    public BridgeContextIdentity context() { return context; }
    public Thread ownerThread() { return ownerThread; }
    public boolean isLive() { return live.get(); }
    public boolean invalidate() { return live.compareAndSet(true, false); }
}
