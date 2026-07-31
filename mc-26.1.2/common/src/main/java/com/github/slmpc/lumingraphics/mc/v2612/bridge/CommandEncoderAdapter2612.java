package com.github.slmpc.lumingraphics.mc.v2612.bridge;

import com.github.slmpc.prismrhi.backend.opengl.OpenGlExternalContext;
import com.mojang.blaze3d.systems.CommandEncoder;
import java.util.Objects;

public final class CommandEncoderAdapter2612 {
    private final CommandEncoder delegate;
    private final Runnable requireCurrent;

    public CommandEncoderAdapter2612(CommandEncoder delegate, OpenGlExternalContext context) {
        this(delegate, Objects.requireNonNull(context, "context")::requireCurrent);
    }

    CommandEncoderAdapter2612(CommandEncoder delegate, Runnable requireCurrent) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.requireCurrent = Objects.requireNonNull(requireCurrent, "requireCurrent");
    }

    public CommandEncoder delegate() { requireCurrent.run(); return delegate; }
    public CommandEncoder access() { return delegate(); }
}
