package com.github.slmpc.lumingraphics.mc.bridge;

public abstract class BridgeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    protected BridgeException(String message) { super(message); }
    protected BridgeException(String message, Throwable cause) { super(message, cause); }
}
