package com.github.slmpc.lumingraphics.mc.v2612.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 26.1.2 中 package-private RenderType 工厂的版本化访问边界。 */
@Mixin(RenderType.class)
public interface RenderTypeFactory2612 {
    @Invoker("create")
    static RenderType lumin$create(String name, RenderSetup setup) {
        throw new AssertionError("Mixin invoker was not transformed");
    }
}
