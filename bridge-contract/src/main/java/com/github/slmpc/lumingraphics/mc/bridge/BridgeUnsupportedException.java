package com.github.slmpc.lumingraphics.mc.bridge;

import java.util.Objects;

public final class BridgeUnsupportedException extends BridgeException {
    private static final long serialVersionUID = 1L;
    private final BridgeUnsupportedDetail detail;

    public BridgeUnsupportedException(BridgeUnsupportedDetail detail) {
        super(Objects.requireNonNull(detail, "detail").reason() + ": " + detail);
        this.detail = detail;
    }

    public BridgeUnsupportedDetail detail() { return detail; }
}
