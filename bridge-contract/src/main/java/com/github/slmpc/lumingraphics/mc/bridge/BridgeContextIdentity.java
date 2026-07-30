package com.github.slmpc.lumingraphics.mc.bridge;

import java.util.Objects;
import java.util.UUID;

public final class BridgeContextIdentity {
    private final String diagnosticName;
    private final String diagnosticId;

    private BridgeContextIdentity(String diagnosticName) {
        this.diagnosticName = diagnosticName;
        this.diagnosticId = diagnosticName + "@" + UUID.randomUUID();
    }

    public static BridgeContextIdentity create(String diagnosticName) {
        Objects.requireNonNull(diagnosticName, "diagnosticName");
        if (diagnosticName.isBlank()) throw new IllegalArgumentException("diagnosticName must not be blank");
        return new BridgeContextIdentity(diagnosticName);
    }

    public String diagnosticName() { return diagnosticName; }
    public String diagnosticId() { return diagnosticId; }
    public BridgeInvalidationToken newInvalidationToken() { return new BridgeInvalidationToken(this, Thread.currentThread()); }

    @Override public String toString() { return diagnosticId; }
}
