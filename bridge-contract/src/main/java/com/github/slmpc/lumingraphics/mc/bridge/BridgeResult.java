package com.github.slmpc.lumingraphics.mc.bridge;

import java.util.Objects;
import java.util.Optional;

public sealed interface BridgeResult<T> permits BridgeResult.Success, BridgeResult.Unsupported {
    static <T> BridgeResult<T> success(T value) {
        return new Success<>(value);
    }

    static <T> BridgeResult<T> unsupported(BridgeUnsupportedDetail detail) {
        return new Unsupported<>(detail);
    }

    T orElseThrow();

    Optional<BridgeUnsupportedDetail> unsupportedDetail();

    record Success<T>(T value) implements BridgeResult<T> {
        public Success { Objects.requireNonNull(value, "value"); }
        @Override public T orElseThrow() { return value; }
        @Override public Optional<BridgeUnsupportedDetail> unsupportedDetail() { return Optional.empty(); }
    }

    record Unsupported<T>(BridgeUnsupportedDetail detail) implements BridgeResult<T> {
        public Unsupported { Objects.requireNonNull(detail, "detail"); }
        @Override public T orElseThrow() { throw new BridgeUnsupportedException(detail); }
        @Override public Optional<BridgeUnsupportedDetail> unsupportedDetail() { return Optional.of(detail); }
    }
}
