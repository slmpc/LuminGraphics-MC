package com.github.slmpc.lumingraphics.mc.bridge;

public final class BridgeWrongContextException extends BridgeException {
    private static final long serialVersionUID = 1L;
    public BridgeWrongContextException(String expected, String actual) {
        super("Wrong bridge context: expected " + expected + ", got " + actual);
    }
}
