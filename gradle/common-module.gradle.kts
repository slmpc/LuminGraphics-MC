import org.gradle.api.artifacts.VersionCatalogsExtension

apply(plugin = "java-library")
apply(plugin = "net.neoforged.moddev")

val luminVersion = extensions.getByType<VersionCatalogsExtension>()
    .named("libs")
    .findVersion("lumin")
    .get()
    .requiredVersion

dependencies {
    add("api", project(":bridge-contract"))
    add("api", platform("com.github.slmpc.lumingraphics:lumin-graphics-bom:$luminVersion"))
    add("api", "com.github.slmpc.lumingraphics:lumin-graphics-core")
    add("api", "com.github.slmpc.lumingraphics:lumin-graphics-render")
}

val commonJava by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}
val commonResources by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(commonJava.name, file("src/main/java"))
    add(commonResources.name, file("src/main/resources"))
}
