import org.gradle.kotlin.dsl.withGroovyBuilder

apply(from = rootProject.file("gradle/neoforge-module.gradle"))

extensions.getByName("neoForge").withGroovyBuilder {
    setProperty("version", "26.2.0.37-beta")
}
