import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.api.tasks.testing.Test

plugins {
    `java-library`
}

dependencies {
    testImplementation(libs.junit)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

sourceSets.named("main") {
    resources.srcDir(rootProject.layout.projectDirectory.dir("docs"))
}
tasks.named<ProcessResources>("processResources") {
    include("bridge-matrix.csv")
    into("bridge")
}
