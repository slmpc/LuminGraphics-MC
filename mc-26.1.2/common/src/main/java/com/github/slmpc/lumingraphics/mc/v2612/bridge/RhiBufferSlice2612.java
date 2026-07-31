package com.github.slmpc.lumingraphics.mc.v2612.bridge;

import com.github.slmpc.prismrhi.resource.RhiBuffer;

public record RhiBufferSlice2612(RhiBuffer buffer, long offset, long length) {
    public RhiBufferSlice2612 {
        if (buffer == null || offset < 0 || length < 0 || offset + length > buffer.size()) {
            throw new IllegalArgumentException("invalid RHI buffer slice");
        }
    }
}
