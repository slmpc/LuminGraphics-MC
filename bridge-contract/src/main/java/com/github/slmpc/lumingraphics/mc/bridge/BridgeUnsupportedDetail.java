package com.github.slmpc.lumingraphics.mc.bridge;

import java.io.Serializable;
import java.util.Objects;

public sealed interface BridgeUnsupportedDetail extends Serializable permits BridgeUnsupportedDetail.State,
        BridgeUnsupportedDetail.Mismatch, BridgeUnsupportedDetail.NativeHandle {
    BridgeUnsupportedReason reason();

    record State(BridgeUnsupportedReason reason, String description) implements BridgeUnsupportedDetail {
        public State {
            Objects.requireNonNull(reason, "reason");
            requireText(description, "description");
        }
    }

    record Mismatch(BridgeUnsupportedReason reason, String dimension, String expected, String actual)
            implements BridgeUnsupportedDetail {
        public Mismatch {
            Objects.requireNonNull(reason, "reason");
            requireText(dimension, "dimension");
            requireText(expected, "expected");
            requireText(actual, "actual");
        }
    }

    record NativeHandle(BridgeUnsupportedReason reason, String typeId, long handle)
            implements BridgeUnsupportedDetail {
        public NativeHandle {
            Objects.requireNonNull(reason, "reason");
            requireText(typeId, "typeId");
        }
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
