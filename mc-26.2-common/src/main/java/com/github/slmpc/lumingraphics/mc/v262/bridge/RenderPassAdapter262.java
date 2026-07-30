package com.github.slmpc.lumingraphics.mc.v262.bridge;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import java.util.Objects;

public final class RenderPassAdapter262 implements AutoCloseable {
    private final RenderPass pass;
    public RenderPassAdapter262(RenderPass pass) { this.pass = Objects.requireNonNull(pass, "pass"); }
    public RenderPass pass() { return pass; }
    public void setVertexBuffer(int slot, GpuBufferSlice slice) { pass.setVertexBuffer(slot, slice); }
    public void drawIndexed(int count, int instances, int firstIndex, int vertexOffset, int firstInstance) {
        pass.drawIndexed(count, instances, firstIndex, vertexOffset, firstInstance);
    }
    @Override public void close() { pass.close(); }
}
