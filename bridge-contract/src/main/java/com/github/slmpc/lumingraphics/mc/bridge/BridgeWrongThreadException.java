package com.github.slmpc.lumingraphics.mc.bridge;

public final class BridgeWrongThreadException extends BridgeException {
    private static final long serialVersionUID = 1L;
    public BridgeWrongThreadException(String expected, String actual) {
        super("Wrong bridge thread: expected " + expected + ", got " + actual);
    }
}
