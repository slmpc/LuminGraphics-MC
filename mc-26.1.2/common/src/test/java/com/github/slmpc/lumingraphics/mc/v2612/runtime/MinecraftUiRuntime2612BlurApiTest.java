package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class MinecraftUiRuntime2612BlurApiTest {
    @Test
    void exposesMinecraftOwnedHudBlur() throws Exception {
        assertEquals(void.class, MinecraftUiRuntime2612.class
                .getMethod("applyBlur", MinecraftBlurRegion2612.class).getReturnType());
    }

    @Test
    void rejectsMalformedRegionsBeforeGpuAccess() {
        UiRect bounds = new UiRect(1.0f, 2.0f, 10.0f, 12.0f);
        assertThrows(IllegalArgumentException.class,
                () -> MinecraftBlurRegion2612.rounded(bounds, -1.0f, 2.0f));
        assertThrows(IllegalArgumentException.class,
                () -> MinecraftBlurRegion2612.rounded(bounds, 2.0f, Float.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new MinecraftBlurRegion2612(bounds,
                        MinecraftBlurRegion2612.CornerRadii.uniform(2.0f), 2.0f,
                        java.util.Collections.nCopies(65,
                                new MinecraftBlurRegion2612.Segment(bounds, 1.0f))));
    }

    @Test
    void snapshotsMutableSegmentInput() {
        UiRect bounds = new UiRect(1.0f, 2.0f, 10.0f, 12.0f);
        List<MinecraftBlurRegion2612.Segment> segments = new java.util.ArrayList<>();
        segments.add(new MinecraftBlurRegion2612.Segment(bounds, 1.0f));
        MinecraftBlurRegion2612 region = new MinecraftBlurRegion2612(bounds,
                MinecraftBlurRegion2612.CornerRadii.uniform(2.0f), 2.0f, segments);

        segments.clear();

        assertEquals(1, region.segments().size());
    }

    @Test
    void fullscreenBlurOwnsItsRenderPassWithoutAnOuterUiPass() throws Exception {
        String owner = MinecraftUiRuntime2612.class.getName().replace('.', '/') + ".class";
        ClassReader reader;
        try (var input = MinecraftUiRuntime2612.class.getClassLoader().getResourceAsStream(owner)) {
            reader = new ClassReader(java.util.Objects.requireNonNull(input, owner));
        }
        java.util.Set<String> constructed = new java.util.HashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                       String signature, String[] exceptions) {
                if (!name.equals("applyBlur")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitTypeInsn(int opcode, String type) {
                        if (opcode == Opcodes.NEW) constructed.add(type);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        assertTrue(constructed.contains("com/github/slmpc/lumingraphics/render/frame/RenderExecution"));
        assertFalse(constructed.contains(
                "com/github/slmpc/lumingraphics/mc/v2612/runtime/MinecraftFrameExecution2612"));
    }
}
