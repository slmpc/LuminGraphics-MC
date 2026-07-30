package com.github.slmpc.lumingraphics.mc.v2612.bridge;

import com.github.slmpc.lumingraphics.mc.v2612.access.BorrowedBlazeResource2612;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.Identifier;

final class BorrowedGlShaderModule2612 extends GlShaderModule implements BorrowedBlazeResource2612 {
    private final BorrowedRelease2612 release = new BorrowedRelease2612();
    private final ShaderType shaderType;
    private boolean closed;

    BorrowedGlShaderModule2612(int shaderId, Identifier id, ShaderType type) {
        super(shaderId, id, type);
        shaderType = type;
    }

    ShaderType shaderType() { return shaderType; }
    @Override public int getShaderId() { return closed ? -1 : super.getShaderId(); }
    @Override public void lumin$markBorrowed(Runnable action) { release.mark(action); }
    @Override public boolean lumin$isBorrowed() { return true; }
    @Override public void close() { closed = true; release.runOnce(); }
}
