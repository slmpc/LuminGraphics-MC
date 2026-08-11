import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withGroovyBuilder

apply(from = rootProject.file("gradle/common-module.gradle.kts"))

@Suppress("UNCHECKED_CAST")
val minecraftLeafSpecs = gradle.extra["minecraftLeafSpecs"] as Map<String, Map<String, String?>>
val moduleSpec = requireNotNull(minecraftLeafSpecs[path])

extensions.getByType<BasePluginExtension>().apply {
    archivesName.set(requireNotNull(moduleSpec["archiveBaseName"]))
}

val sourceSets = extensions.getByType<SourceSetContainer>()
extensions.getByName("neoForge").withGroovyBuilder {
    setProperty("neoFormVersion", libs.versions.neoform.v1211.get())
    "addModdingDependenciesTo"(sourceSets.named("test").get())
}

dependencies {
    val prismVersion = libs.versions.prism.get()
    add("api", "com.github.slmpc.prismrhi:prism-rhi-backend-opengl-common:$prismVersion") {
        exclude(group = "org.lwjgl")
    }
    add("implementation", "com.github.slmpc.prismrhi:prism-rhi-backend-opengl41:$prismVersion") {
        exclude(group = "org.lwjgl")
    }
    add("implementation", "com.github.slmpc.prismrhi:prism-rhi-backend-opengl46:$prismVersion") {
        exclude(group = "org.lwjgl")
    }
    add("api", "com.github.slmpc.lumingraphics:lumin-graphics-text:${libs.versions.lumin.get()}") {
        exclude(group = "org.lwjgl")
    }
    add("api", "com.github.slmpc.lumingraphics:lumin-graphics-ui:${libs.versions.lumin.get()}") {
        exclude(group = "org.lwjgl")
    }
    add("compileOnly", "org.spongepowered:mixin:0.8.7")
    add("testImplementation", libs.junit)
    add("testRuntimeOnly", libs.junit.launcher)
}

tasks.named<Test>("test") { useJUnitPlatform() }
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
