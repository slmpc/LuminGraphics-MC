package com.github.slmpc.lumingraphics.mc.bridge;

public final class BridgeDestroyException extends BridgeException {
    private static final long serialVersionUID = 1L;
    public BridgeDestroyException(String phase, Throwable cause) { super("Bridge " + phase + " failed", cause); }
}
