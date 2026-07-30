package com.github.slmpc.lumingraphics.mc.baseline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MatrixBuildFunctionalTest {
    @TempDir Path temporaryDirectory;

    @Test
    void isolatedVerifyTaskGraphRequiresBothIndependentGeneratorsAndRepeats() throws IOException {
        Path repositoryRoot = Path.of("").toAbsolutePath().normalize();
        Path runnerProjectDir = temporaryDirectory.resolve("matrix-fixture").toAbsolutePath().normalize();
        assertNotEquals(repositoryRoot, runnerProjectDir, "TestKit must not run against the active checkout");
        assertFalse(runnerProjectDir.startsWith(repositoryRoot), "TestKit fixture must be outside the active checkout");
        Files.createDirectories(runnerProjectDir);
        for (String project : List.of(
                "mc-26.1.2-common", "mc-26.1.2-fabric", "mc-26.2-common", "mc-26.2-fabric")) {
            Files.createDirectory(runnerProjectDir.resolve(project));
        }
        Files.writeString(runnerProjectDir.resolve("settings.gradle"), """
                rootProject.name = 'matrix-fixture'
                include 'mc-26.1.2-common', 'mc-26.1.2-fabric', 'mc-26.2-common', 'mc-26.2-fabric'
                """);
        Files.writeString(runnerProjectDir.resolve("build.gradle"), """
                subprojects {
                    tasks.register('genSources')
                    tasks.register('createMinecraftArtifacts')
                }
                tasks.register('generate2612Baseline') {
                    dependsOn ':mc-26.1.2-fabric:genSources', ':mc-26.1.2-common:createMinecraftArtifacts'
                }
                tasks.register('generate262Baseline') {
                    dependsOn ':mc-26.2-fabric:genSources', ':mc-26.2-common:createMinecraftArtifacts'
                }
                tasks.register('verify2612Baseline') { dependsOn 'generate2612Baseline' }
                tasks.register('verify262Baseline') { dependsOn 'generate262Baseline' }
                tasks.register('verifyMinecraftSources') {
                    dependsOn 'verify2612Baseline', 'verify262Baseline'
                }
                """);
        List<String> arguments = List.of(
                "--console=plain", "-Dorg.gradle.daemon=false", "--max-workers=1",
                "verifyMinecraftSources", "--dry-run");
        assertFalse(arguments.stream().anyMatch(argument -> argument.equals("test") || argument.endsWith(":test")),
                "nested TestKit arguments must not select a test task");

        for (int attempt = 1; attempt <= 2; attempt++) {
            String output = GradleRunner.create()
                    .withProjectDir(runnerProjectDir.toFile())
                    .withArguments(arguments)
                    .build()
                    .getOutput();
            for (String task : new String[] {":mc-26.1.2-fabric:genSources", ":mc-26.2-fabric:genSources",
                    ":mc-26.1.2-common:createMinecraftArtifacts", ":mc-26.2-common:createMinecraftArtifacts",
                    ":generate2612Baseline", ":generate262Baseline", ":verify2612Baseline", ":verify262Baseline"}) {
                assertTrue(output.contains(task + " SKIPPED"),
                        "attempt " + attempt + " missing required task " + task + "\n" + output);
            }
        }
    }
}
