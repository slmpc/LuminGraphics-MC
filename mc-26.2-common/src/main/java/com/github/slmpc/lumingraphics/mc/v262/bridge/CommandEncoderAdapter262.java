package com.github.slmpc.lumingraphics.mc.v262.bridge;

import com.mojang.blaze3d.systems.CommandEncoder;
import java.util.Objects;

public final class CommandEncoderAdapter262 {
    private final CommandEncoder encoder;
    public CommandEncoderAdapter262(CommandEncoder encoder) { this.encoder = Objects.requireNonNull(encoder, "encoder"); }
    public CommandEncoder encoder() { return encoder; }
    public void submit() { encoder.submit(); }
    public Object transientMemory() { return encoder.transientMemory(); }
}
