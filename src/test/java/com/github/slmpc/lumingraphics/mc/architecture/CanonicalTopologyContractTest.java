package com.github.slmpc.lumingraphics.mc.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CanonicalTopologyContractTest {
    @Test
    void settingsOwnedImmutableSpecsDriveProjectsAndCommonArchives() throws IOException {
        String settings = Files.readString(Path.of("settings.gradle.kts"));
        String rootBuild = Files.readString(Path.of("build.gradle.kts"));
        String common2612 = Files.readString(Path.of("mc-26.1.2/common/build.gradle.kts"));
        String common262 = Files.readString(Path.of("mc-26.2/common/build.gradle.kts"));

        assertTrue(settings.contains("val minecraftLeafSpecs: Map<String, Map<String, String?>> = mapOf("));
        assertEquals(6, occurrences(settings, "\"projectPath\" to"), "canonical map must define exactly six leaves");
        assertTrue(settings.contains("minecraftLeafSpecs.forEach { (path, spec) ->"));
        assertTrue(settings.contains("include(path)"));
        assertTrue(settings.contains("project(path).projectDir = file(requireNotNull(spec[\"physicalDir\"]))"));
        assertTrue(settings.contains("gradle.extra[\"minecraftLeafSpecs\"] = minecraftLeafSpecs"));
        assertFalse(settings.contains("include(\":mc-"), "settings must not duplicate canonical leaf paths");

        assertTrue(rootBuild.contains("val minecraftLeafSpecs = gradle.extra[\"minecraftLeafSpecs\"]"));
        assertFalse(rootBuild.contains("val minecraftLeafSpecs = mapOf("),
                "build.gradle.kts must consume the settings-owned map instead of defining a second map");

        assertCommonArchiveLookup(common2612);
        assertCommonArchiveLookup(common262);
    }

    private static void assertCommonArchiveLookup(String buildScript) {
        assertTrue(buildScript.contains("val moduleSpec = requireNotNull(minecraftLeafSpecs[path])"));
        assertTrue(buildScript.contains("archivesName.set(requireNotNull(moduleSpec[\"archiveBaseName\"]))"));
        assertFalse(buildScript.contains("archivesName.set(\"mc-"),
                "common archive names must come from the canonical leaf spec");
    }

    private static int occurrences(String text, String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }
}
