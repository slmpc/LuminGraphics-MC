package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MinecraftUiRuntime2612TextScaleTest {
    @Test
    void keepsLegacyUiTextBaselineAtTheMinecraftRuntimeBoundary() {
        assertEquals(1.0f, MinecraftUiRuntime2612.UI_TEXT_SCALE, 0.0001f,
                "Minecraft UI must preserve the original caller-provided text scale");
    }
}
