package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.state.WindowRenderState;
import org.junit.jupiter.api.Test;

class MinecraftResizeCoordinator2612Test {
    @Test
    void consumesPendingResizeBeforeGuiExtractionCanBorrowTheOldTarget() {
        WindowRenderState state = new WindowRenderState();
        state.width = 1920;
        state.height = 1080;
        state.isResized = true;
        List<String> events = new ArrayList<>();

        assertTrue(MinecraftResizeCoordinator2612.applyIfPending(
                state,
                (width, height) -> events.add("resize:" + width + "x" + height),
                () -> events.add("reset-window-resized")));

        assertEquals(List.of("resize:1920x1080", "reset-window-resized"), events);
        assertFalse(state.isResized);
    }
}
