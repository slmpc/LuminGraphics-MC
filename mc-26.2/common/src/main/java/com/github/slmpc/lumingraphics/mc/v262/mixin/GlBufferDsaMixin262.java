package com.github.slmpc.lumingraphics.mc.v262.mixin;

import com.github.slmpc.lumingraphics.mc.v262.access.GlBufferDsaAccess262;
import com.mojang.blaze3d.opengl.DirectStateAccess;
import com.mojang.blaze3d.opengl.GlBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GlBuffer.Direct.class)
abstract class GlBufferDsaMixin262 implements GlBufferDsaAccess262 {
    @Shadow @Final private DirectStateAccess dsa;

    @Override
    public final DirectStateAccess lumin$getDsa() {
        return dsa;
    }
}
