import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.tasks.SourceSetContainer
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
    setProperty("neoFormVersion", "26.1.2-1")
    "addModdingDependenciesTo"(sourceSets.named("test").get())
}

dependencies {
    val prismVersion = libs.versions.prism.get()
    add("api", "com.github.slmpc.prismrhi:prism-rhi-backend-opengl-common:$prismVersion")
    add("implementation", "com.github.slmpc.prismrhi:prism-rhi-backend-opengl41:$prismVersion")
    add("implementation", "com.github.slmpc.prismrhi:prism-rhi-backend-opengl-dsa:$prismVersion")
    add("api", "com.github.slmpc.lumingraphics:lumin-graphics-text:${libs.versions.lumin.get()}")
    add("api", "com.github.slmpc.lumingraphics:lumin-graphics-ui:${libs.versions.lumin.get()}")
    add("compileOnly", "org.spongepowered:mixin:0.8.7")
    add("testImplementation", libs.junit)
    add("testImplementation", "org.ow2.asm:asm:9.9")
    add("testCompileOnly", "org.spongepowered:mixin:0.8.7")
    add("testRuntimeOnly", libs.junit.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
