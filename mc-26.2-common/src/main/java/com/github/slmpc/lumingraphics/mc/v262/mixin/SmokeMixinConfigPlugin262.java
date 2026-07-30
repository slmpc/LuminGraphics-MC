package com.github.slmpc.lumingraphics.mc.v262.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class SmokeMixinConfigPlugin262 implements IMixinConfigPlugin {
    private static final String DSA_MIXIN =
            "com.github.slmpc.lumingraphics.mc.v262.mixin.GlBufferDsaMixin262";

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !DSA_MIXIN.equals(mixinClassName)
                || !"missing-accessor".equals(System.getenv("LUMIN_MC_SMOKE_MODE"));
    }

    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass,
                                   String mixinClassName, IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName, ClassNode targetClass,
                                    String mixinClassName, IMixinInfo mixinInfo) { }
}
