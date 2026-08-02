package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import com.github.slmpc.lumingraphics.mc.v2612.text.MinecraftFontAdapter2612;
import com.github.slmpc.lumingraphics.text.atlas.TtfFontLoader;
import com.github.slmpc.lumingraphics.text.emoji.SystemEmojiAtlas;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecraftUiRuntime2612FontApiTest {
    @Test
    void exposesMinecraftOwnedFontAndGlyphServices() throws Exception {
        assertEquals(TtfFontLoader.class,
                MinecraftUiRuntime2612.class.getMethod("font", String.class).getReturnType());
        assertEquals(MinecraftFontAdapter2612.class,
                MinecraftUiRuntime2612.class.getMethod("minecraftFont").getReturnType());
        assertEquals(SystemEmojiAtlas.class,
                MinecraftUiRuntime2612.class.getMethod("systemEmojiAtlas").getReturnType());
    }
}
