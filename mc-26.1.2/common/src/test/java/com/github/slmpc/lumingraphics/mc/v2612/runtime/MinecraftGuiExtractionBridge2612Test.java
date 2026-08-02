package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.junit.jupiter.api.Test;

class MinecraftGuiExtractionBridge2612Test {
    @Test
    void submitsEmptyStatesInOrderAcrossRepeatedFrames() {
        List<String> events = new ArrayList<>();
        MinecraftGuiExtractionBridge2612 bridge = new MinecraftGuiExtractionBridge2612(
                new GuiRenderState(), fog -> events.add("render"), () -> events.add("end"),
                () -> events.add("close"), () -> { });

        bridge.submit(new GpuBufferSlice(null, 0, 0));
        bridge.submit(new GpuBufferSlice(null, 0, 0));

        assertEquals(List.of("render", "end", "render", "end"), events);
    }

    @Test
    void repeatedCloseIsIdempotentAndRejectsStaleSubmissions() {
        List<String> events = new ArrayList<>();
        MinecraftGuiExtractionBridge2612 bridge = new MinecraftGuiExtractionBridge2612(
                new GuiRenderState(), fog -> events.add("render"), () -> events.add("end"),
                () -> events.add("close"), () -> { });

        bridge.close();
        bridge.close();

        assertEquals(List.of("close"), events);
        assertThrows(IllegalStateException.class,
                () -> bridge.submit(new GpuBufferSlice(null, 0, 0)));
    }

    @Test
    void rejectsMalformedFogSliceBeforeRendering() {
        List<String> events = new ArrayList<>();
        MinecraftGuiExtractionBridge2612 bridge = new MinecraftGuiExtractionBridge2612(
                new GuiRenderState(), fog -> events.add("render"), () -> events.add("end"),
                () -> events.add("close"), () -> { });

        assertThrows(NullPointerException.class, () -> bridge.submit(null));

        assertEquals(List.of(), events);
    }

    @Test
    void ignoresSubmitReentryFromTheNestedNativeGuiRenderer() {
        List<String> events = new ArrayList<>();
        AtomicReference<MinecraftGuiExtractionBridge2612> reference = new AtomicReference<>();
        MinecraftGuiExtractionBridge2612 bridge = new MinecraftGuiExtractionBridge2612(
                new GuiRenderState(), fog -> {
                    events.add("render");
                    reference.get().submit(fog);
                }, () -> events.add("end"), () -> events.add("close"), () -> { });
        reference.set(bridge);

        bridge.submit(new GpuBufferSlice(null, 0, 0));

        assertEquals(List.of("render", "end"), events);
    }

    @Test
    void exposesNativeSubmissionStateOnlyWhileRendering() {
        MinecraftGuiExtractionBridge2612 bridge = new MinecraftGuiExtractionBridge2612(
                new GuiRenderState(), fog -> assertTrue(MinecraftGuiExtractionBridge2612.isNativeSubmissionActive()),
                () -> assertTrue(MinecraftGuiExtractionBridge2612.isNativeSubmissionActive()),
                () -> { }, () -> { });

        assertFalse(MinecraftGuiExtractionBridge2612.isNativeSubmissionActive());
        bridge.submit(new GpuBufferSlice(null, 0, 0));
        assertFalse(MinecraftGuiExtractionBridge2612.isNativeSubmissionActive());
    }

    @Test
    void ignoresSubmitReentryAcrossBridgeInstances() {
        List<String> events = new ArrayList<>();
        AtomicReference<MinecraftGuiExtractionBridge2612> nestedReference = new AtomicReference<>();
        MinecraftGuiExtractionBridge2612 outerBridge = new MinecraftGuiExtractionBridge2612(
                new GuiRenderState(), fog -> {
                    events.add("outer");
                    nestedReference.get().submit(fog);
                }, () -> events.add("end"), () -> events.add("close"), () -> { });
        MinecraftGuiExtractionBridge2612 nestedBridge = new MinecraftGuiExtractionBridge2612(
                new GuiRenderState(), fog -> events.add("nested"), () -> events.add("nested-end"),
                () -> events.add("nested-close"), () -> { });
        nestedReference.set(nestedBridge);

        outerBridge.submit(new GpuBufferSlice(null, 0, 0));

        assertEquals(List.of("outer", "end"), events);
    }

    @Test
    void clearsSubmitReentryGuardWhenNativeRenderThrows() {
        List<String> events = new ArrayList<>();
        AtomicBoolean failRender = new AtomicBoolean(true);
        AtomicReference<MinecraftGuiExtractionBridge2612> reference = new AtomicReference<>();
        MinecraftGuiExtractionBridge2612 bridge = new MinecraftGuiExtractionBridge2612(
                new GuiRenderState(), fog -> {
                    events.add("render");
                    reference.get().submit(fog);
                    if (failRender.getAndSet(false)) throw new IllegalStateException("render");
                }, () -> events.add("end"), () -> events.add("close"), () -> { });
        reference.set(bridge);

        assertThrows(IllegalStateException.class, () -> bridge.submit(new GpuBufferSlice(null, 0, 0)));
        bridge.submit(new GpuBufferSlice(null, 0, 0));

        assertEquals(List.of("render", "render", "end"), events);
    }
}
