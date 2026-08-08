import org.gradle.kotlin.dsl.withGroovyBuilder

apply(from = rootProject.file("gradle/neoforge-module.gradle.kts"))

extensions.getByName("neoForge").withGroovyBuilder {
    setProperty("version", libs.versions.neoforge.v2612.get())
}
