package com.github.slmpc.lumingraphics.mc.bridge;

public final class BridgeClosedException extends BridgeException {
    private static final long serialVersionUID = 1L;
    public BridgeClosedException() { super("Bridge lease is closed"); }
}
