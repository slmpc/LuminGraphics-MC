package com.github.slmpc.lumingraphics.mc.v2612.runtime;

import com.github.slmpc.lumingraphics.mc.v2612.text.MinecraftFontAdapter2612;
import com.github.slmpc.lumingraphics.text.atlas.TtfFontLoader;
import com.github.slmpc.lumingraphics.text.emoji.SystemEmojiAtlas;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinecraftUiRuntime2612FontApiTest {
    @Test
    void exposesMinecraftOwnedFontAndGlyphServices() throws Exception {
        assertEquals(TtfFontLoader.class,
                MinecraftUiRuntime2612.class.getMethod("font", String.class).getReturnType());
        assertEquals(MinecraftFontAdapter2612.class,
                MinecraftUiRuntime2612.class.getMethod("minecraftFont").getReturnType());
        assertEquals(SystemEmojiAtlas.class,
                MinecraftUiRuntime2612.class.getMethod("systemEmojiAtlas").getReturnType());
        assertEquals(void.class,
                MinecraftUiRuntime2612.class.getMethod("setFontGlyphsPerFrame", int.class).getReturnType());
    }

    @Test
    void defaultFontAtlasUses1024PixelPages() {
        MinecraftUiRuntime2612.UiConfig config = MinecraftUiRuntime2612.UiConfig.defaults(
                Identifier.fromNamespaceAndPath("test", "font.ttf"));

        assertEquals(1024, config.atlasWidth());
        assertEquals(1024, config.atlasHeight());
    }

    @Test
    void defaultFontUses48PixelGlyphsAndFourPixelSdfPadding() {
        MinecraftUiRuntime2612.UiConfig config = MinecraftUiRuntime2612.UiConfig.defaults(
                Identifier.fromNamespaceAndPath("test", "font.ttf"));

        assertEquals(48, config.fontPixelHeight());
        assertEquals(4, config.fontPadding());
    }

    @Test
    void rejectsFontSizesAboveTheUiBudget() {
        Identifier font = Identifier.fromNamespaceAndPath("test", "font.ttf");

        assertThrows(IllegalArgumentException.class, () -> new MinecraftUiRuntime2612.UiConfig(
                "default", java.util.Map.of("default", font), MinecraftUiRuntime2612.TextureFilter.LINEAR,
                64 * 1024, 16, 49, 4, 1024, 1024, 8));
        assertThrows(IllegalArgumentException.class, () -> new MinecraftUiRuntime2612.UiConfig(
                "default", java.util.Map.of("default", font), MinecraftUiRuntime2612.TextureFilter.LINEAR,
                64 * 1024, 16, 48, 5, 1024, 1024, 8));
    }

    @Test
    void rejectsFontAtlasPagesThatAreNot1024By1024() {
        Identifier font = Identifier.fromNamespaceAndPath("test", "font.ttf");

        assertThrows(IllegalArgumentException.class, () -> new MinecraftUiRuntime2612.UiConfig(
                "default", java.util.Map.of("default", font), MinecraftUiRuntime2612.TextureFilter.LINEAR,
                64 * 1024, 16, 48, 4, 512, 512, 8));
    }

    @Test
    void registeringTheSameResourceFontKeepsTheExistingSource() throws Exception {
        Identifier font = Identifier.fromNamespaceAndPath("test", "font.ttf");
        MinecraftUiResources2612 resources = new MinecraftUiResources2612(null, null, null,
                MinecraftUiRuntime2612.UiConfig.defaults(font));
        try {
            Field sourcesField = MinecraftUiResources2612.class.getDeclaredField("fontSources");
            sourcesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> sources = (Map<String, Object>) sourcesField.get(resources);
            Object initial = sources.get("default");

            resources.registerResourceFont("default", font);

            assertSame(initial, sources.get("default"));
        } finally {
            resources.close();
        }
    }

    @Test
    void registeringAChangedResourceFontReplacesTheExistingSource() throws Exception {
        Identifier first = Identifier.fromNamespaceAndPath("test", "font.ttf");
        Identifier second = Identifier.fromNamespaceAndPath("test", "other.ttf");
        MinecraftUiResources2612 resources = new MinecraftUiResources2612(null, null, null,
                MinecraftUiRuntime2612.UiConfig.defaults(first));
        try {
            Field sourcesField = MinecraftUiResources2612.class.getDeclaredField("fontSources");
            sourcesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> sources = (Map<String, Object>) sourcesField.get(resources);
            Object initial = sources.get("default");

            resources.registerResourceFont("default", second);

            assertNotSame(initial, sources.get("default"));
        } finally {
            resources.close();
        }
    }
}
