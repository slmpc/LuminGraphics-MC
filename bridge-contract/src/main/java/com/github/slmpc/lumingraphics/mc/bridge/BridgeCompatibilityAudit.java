package com.github.slmpc.lumingraphics.mc.bridge;

import java.util.Objects;
import java.util.Optional;

public record BridgeCompatibilityAudit(boolean isCompatible, BridgeUnsupportedDetail detail) {
    public BridgeCompatibilityAudit {
        if (isCompatible && detail != null) throw new IllegalArgumentException("compatible audit cannot have detail");
        if (!isCompatible) Objects.requireNonNull(detail, "detail");
    }

    public static BridgeCompatibilityAudit compatible() { return new BridgeCompatibilityAudit(true, null); }
    public static BridgeCompatibilityAudit incompatible(BridgeUnsupportedDetail detail) {
        return new BridgeCompatibilityAudit(false, detail);
    }
    public Optional<BridgeUnsupportedReason> reason() {
        return detail == null ? Optional.empty() : Optional.of(detail.reason());
    }
}
