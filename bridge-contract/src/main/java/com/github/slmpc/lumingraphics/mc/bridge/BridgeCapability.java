package com.github.slmpc.lumingraphics.mc.bridge;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class BridgeCapability {
    private final String typeId;
    private final String diagnosticName;
    private final String backend;
    private final long nativeHandle;
    private final BridgeContextIdentity context;
    private final BridgeInvalidationToken token;
    private final BooleanSupplier resourceLiveCheck;
    private final Object openGlCapabilities;
    private final Supplier<?> currentOpenGlCapabilities;
    private final Supplier<BridgeContextIdentity> currentContextCheck;

    private BridgeCapability(String typeId, String diagnosticName, String backend, long nativeHandle,
                             BridgeContextIdentity context, BridgeInvalidationToken token,
                             BooleanSupplier resourceLiveCheck,
                             Object openGlCapabilities, Supplier<?> currentOpenGlCapabilities,
                             Supplier<BridgeContextIdentity> currentContextCheck) {
        this.typeId = requireText(typeId, "typeId");
        this.diagnosticName = requireText(diagnosticName, "diagnosticName");
        this.backend = requireText(backend, "backend");
        this.nativeHandle = nativeHandle;
        this.context = Objects.requireNonNull(context, "context");
        this.token = Objects.requireNonNull(token, "token");
        if (token.context() != context) throw new IllegalArgumentException("token belongs to a different context");
        this.resourceLiveCheck = Objects.requireNonNull(resourceLiveCheck, "resourceLiveCheck");
        this.openGlCapabilities = openGlCapabilities;
        this.currentOpenGlCapabilities = currentOpenGlCapabilities;
        this.currentContextCheck = currentContextCheck;
    }

    public static BridgeCapability nativeResource(String typeId, String diagnosticName, String backend,
                                                   long nativeHandle, BridgeContextIdentity context,
                                                   BridgeInvalidationToken token,
                                                   BooleanSupplier resourceLiveCheck) {
        return new BridgeCapability(typeId, diagnosticName, backend, nativeHandle, context, token,
                resourceLiveCheck, null, null, null);
    }

    public static BridgeCapability openGl(String typeId, String diagnosticName, long nativeHandle,
                                          BridgeContextIdentity context, BridgeInvalidationToken token,
                                          BooleanSupplier resourceLiveCheck,
                                          Object capabilitiesIdentity, Supplier<?> currentCapabilities,
                                          Supplier<BridgeContextIdentity> currentContextCheck) {
        return new BridgeCapability(typeId, diagnosticName, "opengl", nativeHandle, context, token,
                Objects.requireNonNull(resourceLiveCheck, "resourceLiveCheck"),
                Objects.requireNonNull(capabilitiesIdentity, "capabilitiesIdentity"),
                Objects.requireNonNull(currentCapabilities, "currentCapabilities"),
                Objects.requireNonNull(currentContextCheck, "currentContextCheck"));
    }

    public BridgeCompatibilityAudit audit(String expectedTypeId, String expectedDiagnosticName,
                                          String expectedBackend, BridgeContextIdentity expectedContext) {
        requireText(expectedTypeId, "expectedTypeId");
        requireText(expectedDiagnosticName, "expectedDiagnosticName");
        requireText(expectedBackend, "expectedBackend");
        Objects.requireNonNull(expectedContext, "expectedContext");
        if (nativeHandle == 0) return nativeHandleFailure();
        if (!typeId.equals(expectedTypeId)) return mismatch(BridgeUnsupportedReason.TYPE_MISMATCH,
                "typeId", expectedTypeId, typeId);
        if (!diagnosticName.equals(expectedDiagnosticName)) return mismatch(BridgeUnsupportedReason.TYPE_MISMATCH,
                "diagnosticName", expectedDiagnosticName, diagnosticName);
        if (!backend.equals(expectedBackend)) return mismatch(BridgeUnsupportedReason.BACKEND_MISMATCH,
                "backend", expectedBackend, backend);
        if (context != expectedContext) return mismatch(BridgeUnsupportedReason.CONTEXT_MISMATCH,
                "context", expectedContext.diagnosticId(), context.diagnosticId());
        if (Thread.currentThread() != token.ownerThread()) return mismatch(BridgeUnsupportedReason.THREAD_MISMATCH,
                "thread", token.ownerThread().getName(), Thread.currentThread().getName());
        if (!token.isLive()) return state(BridgeUnsupportedReason.TOKEN_INVALIDATED, "owner token is invalidated");
        if (!resourceLiveCheck.getAsBoolean()) {
            return state(BridgeUnsupportedReason.CLOSED, "native resource is closed");
        }
        if (openGlCapabilities != null) {
            Object currentCapabilities = currentOpenGlCapabilities.get();
            if (currentCapabilities != openGlCapabilities) return state(BridgeUnsupportedReason.CONTEXT_MISMATCH,
                    "current OpenGL capabilities identity differs");
            BridgeContextIdentity currentContext = currentContextCheck.get();
            if (currentContext != context) return state(BridgeUnsupportedReason.CONTEXT_MISMATCH,
                    "current OpenGL context check differs");
        }
        return BridgeCompatibilityAudit.compatible();
    }

    private BridgeCompatibilityAudit nativeHandleFailure() {
        return BridgeCompatibilityAudit.incompatible(new BridgeUnsupportedDetail.NativeHandle(
                BridgeUnsupportedReason.NO_NATIVE_HANDLE, typeId, nativeHandle));
    }

    private static BridgeCompatibilityAudit mismatch(BridgeUnsupportedReason reason, String dimension,
                                                      String expected, String actual) {
        return BridgeCompatibilityAudit.incompatible(
                new BridgeUnsupportedDetail.Mismatch(reason, dimension, expected, actual));
    }

    private static BridgeCompatibilityAudit state(BridgeUnsupportedReason reason, String description) {
        return BridgeCompatibilityAudit.incompatible(new BridgeUnsupportedDetail.State(reason, description));
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
