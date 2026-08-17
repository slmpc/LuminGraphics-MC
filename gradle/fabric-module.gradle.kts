import groovy.json.JsonSlurper
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.withGroovyBuilder
import java.io.DataInputStream
import java.io.File
import java.net.URLClassLoader
import java.util.zip.ZipFile

@Suppress("UNCHECKED_CAST")
val minecraftLeafSpecs = gradle.extra["minecraftLeafSpecs"] as Map<String, Map<String, String?>>
val moduleSpec = requireNotNull(minecraftLeafSpecs[path])
fun Map<String, String?>.required(name: String): String = requireNotNull(this[name])

apply(plugin = "java-library")
apply(plugin = if (moduleSpec.required("version") == "1.21.1") "fabric-loom" else "net.fabricmc.fabric-loom")

val commonProject = project(moduleSpec.required("commonPath"))
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val prismVersion = catalog.findVersion("prism").get().requiredVersion
val minecraftVersion = moduleSpec.required("version")
val versionKey = minecraftVersion.replace(".", "")
val expectedLoader = catalog.findVersion("fabric-loader-v$versionKey").get().requiredVersion
val expectedApi = catalog.findVersion("fabric-api-v$versionKey").get().requiredVersion
val mixinName = when (minecraftVersion) {
    "26.1.2" -> "lumin_graphics_mc_2612.mixins.json"
    "26.2" -> "lumin-graphics-mc-262.mixins.json"
    else -> "lumin_graphics_mc_$versionKey.mixins.json"
}
val fabricMixinName = if (minecraftVersion == "1.21.1") "lumin_graphics_mc_1211.fabric.mixins.json" else null
val accessWidenerName = "lumin_graphics_mc_$versionKey.accesswidener"
val luminVersion = catalog.findVersion("lumin").get().requiredVersion
val packagedLibraries = listOf(
    "com.github.slmpc.lumingraphics:lumin-graphics-core:$luminVersion",
    "com.github.slmpc.lumingraphics:lumin-graphics-render:$luminVersion",
    "com.github.slmpc.lumingraphics:lumin-graphics-text:$luminVersion",
    "com.github.slmpc.lumingraphics:lumin-graphics-ui:$luminVersion",
    "com.github.slmpc.prismrhi:prism-rhi-core:$prismVersion",
    "com.github.slmpc.prismrhi:prism-rhi-backend-opengl-common:$prismVersion",
    "com.github.slmpc.prismrhi:prism-rhi-backend-opengl41:$prismVersion",
    "com.github.slmpc.prismrhi:prism-rhi-backend-opengl46:$prismVersion",
)

extensions.configure<BasePluginExtension> {
    archivesName.set(moduleSpec.required("archiveBaseName"))
}

val luminGraphicsShadow by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    add("compileOnly", commonProject)
    add("compileOnly", catalog.findLibrary("mixin").get())
    add("compileOnly", "com.github.slmpc.prismrhi:prism-rhi-backend-opengl41:$prismVersion")
    add("compileOnly", "com.github.slmpc.prismrhi:prism-rhi-backend-opengl46:$prismVersion")
    add(luminGraphicsShadow.name, project(":bridge-contract")) { isTransitive = false }
    packagedLibraries.forEach { coordinate ->
        add(luminGraphicsShadow.name, coordinate) { isTransitive = false }
    }
}

val sourceSets = extensions.getByType<SourceSetContainer>()
sourceSets.named("main") {
    java.srcDir(commonProject.file("src/main/java"))
    resources.srcDir(commonProject.file("src/main/resources"))
}

extensions.getByName("loom").withGroovyBuilder {
    setProperty("accessWidenerPath", file("src/main/resources/$accessWidenerName"))
}

tasks.named("compileJava") {
    mustRunAfter(tasks.named("genSourcesWithVineflower"))
}

tasks.named<Jar>("jar") {
    dependsOn(luminGraphicsShadow)
    from({ luminGraphicsShadow.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/jars/**", "META-INF/jarjar/**")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.register("verifyFabricWiring") {
    group = "verification"
    description = "Validates Fabric metadata and version isolation for Minecraft $minecraftVersion."
    val resources = layout.projectDirectory.dir("src/main/resources")
    val entrypoint = layout.projectDirectory.file(
        "src/main/java/com/github/slmpc/lumingraphics/mc/fabric/v$versionKey/LuminGraphicsFabricClient.java",
    )
    inputs.files(fileTree("src") { include("**/*") })
    doLast {
        val metadataFile = resources.file("fabric.mod.json").asFile
        val awFile = resources.file(accessWidenerName).asFile
        if (!metadataFile.isFile) throw GradleException("Missing Fabric metadata for $minecraftVersion")
        if (!awFile.isFile) throw GradleException("Missing version-specific Access Widener for $minecraftVersion")
        if (!entrypoint.asFile.isFile) throw GradleException("Missing Fabric client entrypoint for $minecraftVersion")

        @Suppress("UNCHECKED_CAST")
        val metadata = JsonSlurper().parse(metadataFile) as Map<String, Any?>
        val expectedEntrypoint = "com.github.slmpc.lumingraphics.mc.fabric.v$versionKey.LuminGraphicsFabricClient"
        if (metadata["id"] != "lumin_graphics_mc") {
            throw GradleException("Unexpected Fabric mod id: ${metadata["id"]}")
        }
        if (metadata["version"] != "${project.version}+mc$minecraftVersion") {
            throw GradleException("Unexpected Fabric version: ${metadata["version"]}")
        }
        val entrypoints = metadata["entrypoints"] as? Map<*, *>
        if (metadata["environment"] != "client" || entrypoints?.get("client") != listOf(expectedEntrypoint)) {
            throw GradleException("Fabric client entrypoint mismatch for $minecraftVersion")
        }
        val expectedMixins = listOfNotNull(mixinName, fabricMixinName)
        if (metadata["accessWidener"] != awFile.name || metadata["mixins"] != expectedMixins) {
            throw GradleException("Fabric AW/mixin registration mismatch for $minecraftVersion")
        }
        // Fabric Loader silently excludes a nested mod whose own dependencies cannot be satisfied, so an
        // exact "=" pin on fabricloader/fabric-api makes the whole runtime disappear from the classpath as
        // soon as the player updates either one, and the failure only surfaces as NoClassDefFoundError in
        // the consuming mod. Both stay lower bounds. The game version is a genuine single-version binding
        // and stays exact.
        val expectedDependencies = mutableMapOf(
            "fabricloader" to ">=$expectedLoader",
            "minecraft" to "=$minecraftVersion",
            "java" to ">=25",
        )
        if (minecraftVersion != "1.21.1") {
            expectedDependencies["fabric-api"] = ">=$expectedApi"
        }
        if (metadata["depends"] != expectedDependencies) {
            throw GradleException(
                "Fabric dependency constraints mismatch: expected $expectedDependencies, found ${metadata["depends"]}",
            )
        }
        val implementationDependencies = configurations.getByName("implementation").dependencies
        val loaderDependencies = if (minecraftVersion == "1.21.1") {
            configurations.getByName("modImplementation").dependencies
        } else {
            implementationDependencies
        }
        val loaderPinned = loaderDependencies.any {
            it.group == "net.fabricmc" && it.name == "fabric-loader" && it.version == expectedLoader
        }
        val apiPinned = implementationDependencies.any {
            it.group == "net.fabricmc.fabric-api" && it.name == "fabric-api" && it.version == expectedApi
        }
        if (!loaderPinned || apiPinned != (minecraftVersion != "1.21.1")) {
            throw GradleException("Fabric dependency pins mismatch for $minecraftVersion")
        }
        val awText = awFile.readText()
        val expectedNamespace = if (minecraftVersion == "1.21.1") "named" else "official"
        if (!awText.startsWith("accessWidener v2 $expectedNamespace")) {
            throw GradleException("Access Widener namespace/header mismatch for $minecraftVersion")
        }
        if (awText.contains(if (minecraftVersion == "26.1.2") "FrameBufferCache" else "TextureFormat")) {
            throw GradleException("Swapped Fabric Access Widener detected for $minecraftVersion")
        }
        val commonMixin = commonProject.file("src/main/resources/$mixinName")
        if (!commonMixin.isFile) throw GradleException("Missing matching common mixin output: $mixinName")
    }
}

val verifyFabricArtifact = tasks.register("verifyFabricArtifact") {
    group = "verification"
    description = "Inspects and class-loads the Fabric artifact for Minecraft $minecraftVersion."
    dependsOn(tasks.named("jar"))
    inputs.files(tasks.named<Jar>("jar").flatMap { it.archiveFile })
    inputs.files(configurations.getByName("runtimeClasspath"))
    doLast {
        val jarFile = tasks.named<Jar>("jar").get().archiveFile.get().asFile
        val entrypointName = "com.github.slmpc.lumingraphics.mc.fabric.v$versionKey.LuminGraphicsFabricClient"
        val entrypointPath = entrypointName.replace('.', '/') + ".class"
        val commonMarker = when (minecraftVersion) {
            "1.21.1" -> "com/github/slmpc/lumingraphics/mc/v1211/bridge/GlStateManagerBridge1211.class"
            "26.1.2" -> "com/github/slmpc/lumingraphics/mc/v2612/bridge/Blaze3DBridge2612.class"
            "26.2" -> "com/github/slmpc/lumingraphics/mc/v262/bridge/Blaze3DBridge262.class"
            else -> throw GradleException("Unsupported Minecraft version: $minecraftVersion")
        }
        val ownVersionPackage = "/v$versionKey/"
        ZipFile(jarFile).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            listOfNotNull(entrypointPath, "fabric.mod.json", accessWidenerName, mixinName, fabricMixinName, commonMarker).forEach { required ->
                if (required !in names) throw GradleException("Fabric artifact misses $required")
            }
            if (names.any { name -> "/v" in name && name.contains("/mc/") && ownVersionPackage !in name }) {
                throw GradleException("Fabric artifact contains classes from another Minecraft version")
            }
            @Suppress("UNCHECKED_CAST")
            val metadata = JsonSlurper().parse(zip.getInputStream(zip.getEntry("fabric.mod.json"))) as Map<String, Any?>
            val entrypoints = metadata["entrypoints"] as? Map<*, *>
            if (entrypoints?.get("client") != listOf(entrypointName)) {
                throw GradleException("Packaged Fabric entrypoint mismatch for $minecraftVersion")
            }
            DataInputStream(zip.getInputStream(zip.getEntry(entrypointPath))).use { input ->
                if (
                    Integer.toUnsignedLong(input.readInt()) != 0xCAFEBABEL || input.readUnsignedShort() != 0 ||
                    input.readUnsignedShort() != 69
                ) {
                    throw GradleException("Fabric entrypoint is not Java 25 bytecode")
                }
            }
        }
        val runtimeClasspath = configurations.getByName("runtimeClasspath")
        val urls = (listOf(jarFile) + runtimeClasspath.files).map { it.toURI().toURL() }.toTypedArray()
        URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { loader ->
            val entrypointClass = Class.forName(entrypointName, false, loader)
            val initializerClass = Class.forName("net.fabricmc.api.ClientModInitializer", false, loader)
            if (!initializerClass.isAssignableFrom(entrypointClass)) {
                throw GradleException("Packaged entrypoint does not implement Fabric ClientModInitializer")
            }
        }
        listOf(rootProject.file("bridge-contract/src/main"), commonProject.file("src/main")).forEach { sourceRoot ->
            val leaked = fileTree(sourceRoot) { include("**/*.java") }.files.find { source ->
                source.readText().contains("net.fabricmc")
            }
            if (leaked != null) throw GradleException("Fabric type leaked outside Fabric modules: $leaked")
        }
    }
}

tasks.named("check") {
    dependsOn("verifyFabricWiring", verifyFabricArtifact)
}

val smokeTaskNames = gradle.startParameter.taskNames.map { it.substringAfterLast(':') }
if (smokeTaskNames.any { it in listOf("runAllBridgeSmokes", "runAllBridgeNegativeSmokes") }) {
    val negative = "runAllBridgeNegativeSmokes" in smokeTaskNames
    val evidenceDir = System.getenv("LUMIN_MC_SMOKE_EVIDENCE_DIR")
        ?: "D:/Dev/ChenMeng/LuminGraphics/.omo/start-work/attempts/lumin-graphics-prism-rhi-migration-20260730/todo22-smokes"
    val smokeRunDir = layout.buildDirectory.dir("todo22-run-${if (negative) "negative" else "positive"}")
    tasks.named<JavaExec>("runClient") {
        val smokeOutput = File(
            evidenceDir,
            "${moduleSpec.required("stableId")}-${if (negative) "negative" else "positive"}.json",
        )
        environment("LUMIN_MC_SMOKE_MODE", if (negative) moduleSpec.required("negativeMode") else "positive")
        environment("LUMIN_MC_SMOKE_OUTPUT", smokeOutput.absolutePath)
        doFirst {
            delete(smokeOutput)
            if (!negative) delete(File(smokeOutput.parentFile, smokeOutput.name.replace(".json", ".png")))
            delete(smokeRunDir)
            workingDir(smokeRunDir.get().asFile)
        }
        doLast { delete(smokeRunDir) }
    }
}
