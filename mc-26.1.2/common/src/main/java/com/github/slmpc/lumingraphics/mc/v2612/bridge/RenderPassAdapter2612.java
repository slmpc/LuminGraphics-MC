package com.github.slmpc.lumingraphics.mc.v2612.bridge;

import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalContext;
import com.mojang.blaze3d.systems.RenderPass;
import java.util.Objects;

public final class RenderPassAdapter2612 {
    private final RenderPass delegate;
    private final Runnable requireCurrent;

    public RenderPassAdapter2612(RenderPass delegate, OpenGlExternalContext context) {
        this(delegate, Objects.requireNonNull(context, "context")::requireCurrent);
    }

    RenderPassAdapter2612(RenderPass delegate, Runnable requireCurrent) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.requireCurrent = Objects.requireNonNull(requireCurrent, "requireCurrent");
    }

    public RenderPass delegate() { requireCurrent.run(); return delegate; }
    public RenderPass access() { return delegate(); }
}
