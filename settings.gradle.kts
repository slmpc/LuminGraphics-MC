import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven(url = "https://maven.fabricmc.net")
        maven(url = "https://maven.neoforged.net/releases")
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        exclusiveContent {
            forRepository {
                mavenLocal()
            }
            filter {
                includeGroup("com.github.slmpc.lumingraphics")
                includeGroup("com.github.slmpc.prismrhi")
            }
        }
        maven(url = "https://slmpc.github.io/maven-repository/")
        mavenCentral()
        maven(url = "https://libraries.minecraft.net")
        maven(url = "https://maven.fabricmc.net")
        maven(url = "https://maven.neoforged.net/releases")
    }
}

rootProject.name = "LuminGraphics-MC"
include(":bridge-contract")

val minecraftLeafSpecs: Map<String, Map<String, String?>> = mapOf(
    ":mc-26.1.2:common" to mapOf(
        "projectPath" to ":mc-26.1.2:common", "stableId" to "mc-26.1.2-common", "version" to "26.1.2", "role" to "common",
        "physicalDir" to "mc-26.1.2/common", "commonPath" to ":mc-26.1.2:common",
        "archiveBaseName" to "mc-26.1.2-common", "negativeMode" to null,
    ),
    ":mc-26.1.2:fabric" to mapOf(
        "projectPath" to ":mc-26.1.2:fabric", "stableId" to "mc-26.1.2-fabric", "version" to "26.1.2", "role" to "fabric",
        "physicalDir" to "mc-26.1.2/fabric", "commonPath" to ":mc-26.1.2:common",
        "archiveBaseName" to "lumin-graphics-mc-fabric-26.1.2", "negativeMode" to "wrong-thread",
    ),
    ":mc-26.1.2:neoforge" to mapOf(
        "projectPath" to ":mc-26.1.2:neoforge", "stableId" to "mc-26.1.2-neoforge", "version" to "26.1.2", "role" to "neoforge",
        "physicalDir" to "mc-26.1.2/neoforge", "commonPath" to ":mc-26.1.2:common",
        "archiveBaseName" to "lumin-graphics-mc-neoforge-26.1.2", "negativeMode" to "stale-token",
    ),
    ":mc-26.2:common" to mapOf(
        "projectPath" to ":mc-26.2:common", "stableId" to "mc-26.2-common", "version" to "26.2", "role" to "common",
        "physicalDir" to "mc-26.2/common", "commonPath" to ":mc-26.2:common",
        "archiveBaseName" to "mc-26.2-common", "negativeMode" to null,
    ),
    ":mc-26.2:fabric" to mapOf(
        "projectPath" to ":mc-26.2:fabric", "stableId" to "mc-26.2-fabric", "version" to "26.2", "role" to "fabric",
        "physicalDir" to "mc-26.2/fabric", "commonPath" to ":mc-26.2:common",
        "archiveBaseName" to "lumin-graphics-mc-fabric-26.2", "negativeMode" to "wrong-context",
    ),
    ":mc-26.2:neoforge" to mapOf(
        "projectPath" to ":mc-26.2:neoforge", "stableId" to "mc-26.2-neoforge", "version" to "26.2", "role" to "neoforge",
        "physicalDir" to "mc-26.2/neoforge", "commonPath" to ":mc-26.2:common",
        "archiveBaseName" to "lumin-graphics-mc-neoforge-26.2", "negativeMode" to "missing-accessor",
    ),
)

minecraftLeafSpecs.forEach { (path, spec) ->
    include(path)
    project(path).projectDir = file(requireNotNull(spec["physicalDir"]))
}
gradle.extra["minecraftLeafSpecs"] = minecraftLeafSpecs
