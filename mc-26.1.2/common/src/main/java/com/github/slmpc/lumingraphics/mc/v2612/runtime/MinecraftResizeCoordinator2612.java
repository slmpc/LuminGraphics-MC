package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import java.util.Objects;
import java.util.function.BiConsumer;
import net.minecraft.client.renderer.state.WindowRenderState;

/** 在 GUI 提取前消费 Minecraft 26.1.2 的待处理 framebuffer resize。 */
public final class MinecraftResizeCoordinator2612 {
    private MinecraftResizeCoordinator2612() {
    }

    public static boolean applyIfPending(WindowRenderState state, BiConsumer<Integer, Integer> resize,
                                         Runnable resetWindowResize) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(resize, "resize");
        Objects.requireNonNull(resetWindowResize, "resetWindowResize");
        if (!state.isResized) return false;

        resize.accept(state.width, state.height);
        state.isResized = false;
        resetWindowResize.run();
        return true;
    }
}
