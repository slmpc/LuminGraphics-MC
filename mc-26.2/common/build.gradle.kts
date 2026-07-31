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
    setProperty("neoFormVersion", "26.2-2")
}

dependencies {
    add("api", "com.github.slmpc.prismrhi:prism-rhi-backend-opengl-common:0.1.0")
    add("implementation", "com.github.slmpc.prismrhi:prism-rhi-backend-opengl41:0.1.0")
    add("implementation", "com.github.slmpc.prismrhi:prism-rhi-backend-opengl-dsa:0.1.0")
    add("api", "com.github.slmpc.lumingraphics:lumin-graphics-text:0.1.0")
    add("compileOnly", "net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7")
    add("testImplementation", libs.junit)
    add("testRuntimeOnly", libs.junit.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:all,-processing")
}

sourceSets.named("test") {
    compileClasspath += sourceSets.named("main").get().compileClasspath
    runtimeClasspath += sourceSets.named("main").get().runtimeClasspath
}
