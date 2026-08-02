package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MinecraftGraphicsRuntime2612ApiTest {
    @Test
    void exposesTheVersionedLifecycleAndOwnedPrismSurface() {
        Set<String> publicMethods = java.util.Arrays.stream(MinecraftGraphicsRuntime2612.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertTrue(publicMethods.containsAll(Set.of(
                "bindCurrent", "current", "externalContext", "instance", "device", "graphicsQueue", "commandPool",
                "commandBuffer", "blazeBridge", "luminContext", "currentRenderTarget", "beginFrame",
                "endFrame", "abortFrame", "invalidateRenderTargets", "invalidateContext", "close")));
    }
}
