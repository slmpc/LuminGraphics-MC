package com.github.slmpc.lumingraphics.mc.packaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class VariantJarVerifierTest {
    @TempDir
    Path root;

    @Test
    void standardArtifactPathsUseNestedVersionAndLoaderDirectories() {
        for (VariantPath variant : List.of(
                new VariantPath("fabric", "26.1.2"),
                new VariantPath("neoforge", "26.1.2"),
                new VariantPath("fabric", "26.2"),
                new VariantPath("neoforge", "26.2"))) {
            Path actual = VariantJarVerifier.standardArtifactPath(root, variant.loader(), variant.minecraft());
            Path expected = root.resolve("mc-" + variant.minecraft()).resolve(variant.loader())
                    .resolve("build/libs").resolve(variant.fileName());
            Path legacy = root.resolve("mc-" + variant.minecraft() + '-' + variant.loader())
                    .resolve("build/libs").resolve(variant.fileName());

            assertEquals(expected, actual);
            assertFalse(legacy.equals(actual));
        }
    }

    @Test
    void missingNestedArtifactReportsItsExactExpectedPath() {
        Path expected = VariantJarVerifier.standardArtifactPath(root, "fabric", "26.1.2");

        IOException failure = assertThrows(IOException.class,
                () -> VariantJarVerifier.requireFinalArtifact(expected));

        assertEquals("Final variant artifact is missing: " + expected, failure.getMessage());
    }

    @Test
    void shadowCatalogIncludesTheBridgeContractInEachLoaderArtifact() {
        assertTrue(ArtifactCatalog.EXPECTED.stream().anyMatch(coordinate ->
                coordinate.group().equals("com.github.slmpc.lumingraphics.mc")
                        && coordinate.artifact().equals("bridge-contract")
                        && coordinate.version().equals("1.2.1")));
    }

    private record VariantPath(String loader, String minecraft) {
        private String fileName() {
            return "lumin-graphics-mc-" + loader + '-' + minecraft + "-1.2.1.jar";
        }
    }
}
