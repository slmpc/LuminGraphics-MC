package com.github.slmpc.lumingraphics.mc.bridge;

public final class BridgeInvalidatedException extends BridgeException {
    private static final long serialVersionUID = 1L;
    public BridgeInvalidatedException() { super("Bridge owner invalidated the resource"); }
}
