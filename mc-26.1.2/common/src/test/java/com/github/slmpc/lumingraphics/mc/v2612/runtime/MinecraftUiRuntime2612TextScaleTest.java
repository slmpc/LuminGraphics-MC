package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MinecraftUiRuntime2612TextScaleTest {
    @Test
    void compensatesForThe48PixelStbRasterizationHeight() {
        assertEquals(0.30f, MinecraftUiRuntime2612.UI_TEXT_SCALE, 0.0001f,
                "48px rasterization must preserve the previous 40px * 0.36 UI size");
        assertEquals(14.4f,
                MinecraftUiRuntime2612.MAX_FONT_PIXEL_HEIGHT * MinecraftUiRuntime2612.UI_TEXT_SCALE,
                0.0001f);
    }

    @Test
    void appliesAndValidatesTheAdditionalUiTextScaleMultiplier() {
        assertEquals(0.30f, MinecraftUiRuntime2612.effectiveUiTextScale(1.0f), 0.0001f);
        assertEquals(0.45f, MinecraftUiRuntime2612.effectiveUiTextScale(1.5f), 0.0001f);
        assertThrows(IllegalArgumentException.class,
                () -> MinecraftUiRuntime2612.effectiveUiTextScale(0.0f));
        assertThrows(IllegalArgumentException.class,
                () -> MinecraftUiRuntime2612.effectiveUiTextScale(Float.NaN));
    }
}
