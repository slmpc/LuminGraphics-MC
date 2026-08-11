import groovy.json.JsonSlurper
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.withGroovyBuilder
import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

apply(plugin = "java-library")
apply(plugin = "net.neoforged.moddev")

@Suppress("UNCHECKED_CAST")
val minecraftLeafSpecs = gradle.extra["minecraftLeafSpecs"] as Map<String, Map<String, String?>>
val moduleSpec = requireNotNull(minecraftLeafSpecs[path])
fun Map<String, String?>.required(name: String): String = requireNotNull(this[name])

val commonProject = project(moduleSpec.required("commonPath"))
evaluationDependsOn(commonProject.path)
val minecraftVersion = moduleSpec.required("version")
val versionKey = minecraftVersion.replace(".", "")
val packageKey = "v$versionKey"
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val expectedNeoForgeVersion = catalog.findVersion("neoforge-v$versionKey").get().requiredVersion
val expectedNeoFormVersion = catalog.findVersion("neoform-v$versionKey").get().requiredVersion
val entrypointClass =
    "com.github.slmpc.lumingraphics.mc.$packageKey.neoforge.LuminGraphicsNeoForge$versionKey"
val mixinConfigName = when (minecraftVersion) {
    "26.1.2" -> "lumin_graphics_mc_2612.mixins.json"
    "26.2" -> "lumin-graphics-mc-262.mixins.json"
    else -> "lumin_graphics_mc_$versionKey.mixins.json"
}
val accessTransformer = layout.projectDirectory.file("src/main/resources/META-INF/accesstransformer.cfg")
val luminVersion = catalog.findVersion("lumin").get().requiredVersion
val prismVersion = catalog.findVersion("prism").get().requiredVersion
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
    add(luminGraphicsShadow.name, project(":bridge-contract")) { isTransitive = false }
    packagedLibraries.forEach { coordinate ->
        add(luminGraphicsShadow.name, coordinate) { isTransitive = false }
    }
}

val sourceSets = extensions.getByType<SourceSetContainer>()
val commonSourceSets = commonProject.extensions.getByType<SourceSetContainer>()
extensions.getByName("neoForge").withGroovyBuilder {
    "accessTransformers" {
        "from"(accessTransformer)
    }
    "runs" {
        "create"("client") {
            "client"()
        }
    }
    "mods" {
        "create"("lumin_graphics_mc") {
            "sourceSet"(sourceSets.named("main").get())
            "sourceSet"(commonSourceSets.named("main").get())
        }
    }
}

tasks.named<Jar>("jar") {
    dependsOn(commonProject.tasks.named("classes"))
    from(commonSourceSets.named("main").get().output)
    dependsOn(luminGraphicsShadow)
    from({ luminGraphicsShadow.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/jars/**", "META-INF/jarjar/**")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named("jarJar") {
    enabled = false
}

val expectedAccessTargets = when (minecraftVersion) {
    "1.21.1" -> emptyMap()
    "26.1.2" -> mapOf(
        "com.mojang.blaze3d.opengl.GlTexture" to
            "(ILjava/lang/String;Lcom/mojang/blaze3d/textures/TextureFormat;IIIII)V",
        "com.mojang.blaze3d.opengl.GlTextureView" to "(Lcom/mojang/blaze3d/opengl/GlTexture;II)V",
        "com.mojang.blaze3d.opengl.GlBuffer" to
            "(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/opengl/DirectStateAccess;IJILjava/nio/ByteBuffer;)V",
    )
    "26.2" -> mapOf(
        "com.mojang.blaze3d.opengl.GlTexture" to
            "(ILjava/lang/String;Lcom/mojang/blaze3d/GpuFormat;IIIIILcom/mojang/blaze3d/opengl/FrameBufferCache;)V",
        "com.mojang.blaze3d.opengl.GlTextureView" to
            "(Lcom/mojang/blaze3d/opengl/GlTexture;IILcom/mojang/blaze3d/opengl/FrameBufferCache;)V",
        "com.mojang.blaze3d.opengl.GlBuffer\$Direct" to
            "(Lcom/mojang/blaze3d/opengl/DirectStateAccess;IJIZ)V",
        "com.mojang.blaze3d.opengl.GlProgram" to "(ILjava/lang/String;)V",
    )
    else -> throw GradleException("Unsupported Minecraft version: $minecraftVersion")
}

val verifyNeoForgeContract = tasks.register("verifyNeoForgeContract") {
    group = "verification"
    description = "Validates NeoForge $minecraftVersion entrypoint, metadata, AT, mixin, and common-output wiring."
    dependsOn(tasks.named("jar"), tasks.named("createMinecraftArtifacts"), commonProject.tasks.named("classes"))
    inputs.files(sourceSets.named("main").get().allSource, accessTransformer)
    inputs.files(commonSourceSets.named("main").get().output)
    doLast {
        val catalogText = rootProject.file("gradle/libs.versions.toml").readText()
        if (!catalogText.contains("moddev = \"2.0.140\"")) {
            throw GradleException("NeoForge modules require exact ModDevGradle 2.0.140")
        }
        val sourcePath = entrypointClass.replace('.', '/') + ".java"
        val source = file("src/main/java/$sourcePath")
        if (!source.isFile) {
            throw GradleException("Missing NeoForge $minecraftVersion client entrypoint: $source")
        }
        val sourceText = source.readText()
        val lifecycleContracts = when (minecraftVersion) {
            "1.21.1" -> listOf("MinecraftUiRuntime1211.bindCurrent")
            "26.1.2" -> listOf("MinecraftUiRuntime2612.bindCurrent", "runtime.close()")
            "26.2" -> listOf("RenderSystem.assertOnRenderThread()", "GL.getCapabilities()", "token.invalidate()")
            else -> throw GradleException("Unsupported Minecraft version: $minecraftVersion")
        }
        (listOf("@Mod(value = LuminGraphicsNeoForge$versionKey.MOD_ID, dist = Dist.CLIENT)") + lifecycleContracts)
            .forEach { contract ->
                if (!sourceText.contains(contract)) {
                    throw GradleException("NeoForge $minecraftVersion lifecycle contract is missing: $contract")
                }
            }
        listOf("glfwCreateWindow", "glfwInit", "GL.createCapabilities").forEach { forbidden ->
            if (sourceText.contains(forbidden)) {
                throw GradleException("NeoForge $minecraftVersion must borrow Minecraft context, found $forbidden")
            }
        }

        val metadata = file("src/main/resources/META-INF/neoforge.mods.toml")
        if (!metadata.isFile) throw GradleException("Missing NeoForge $minecraftVersion metadata: $metadata")
        val metadataText = metadata.readText()
        val requiredMetadata = listOf(
            "modLoader=\"javafml\"",
            "loaderVersion=\"${if (minecraftVersion == "1.21.1") "[4,)" else "[11,)"}\"",
            "modId=\"lumin_graphics_mc\"",
            "version=\"${project.version}+mc$minecraftVersion\"",
            "config=\"$mixinConfigName\"",
            "versionRange=\"[$expectedNeoForgeVersion]\"",
            "versionRange=\"[$minecraftVersion]\"",
            "side=\"CLIENT\"",
            "[features.lumin_graphics_mc]",
            "javaVersion=\"[25,)\"",
        )
        requiredMetadata.forEach { value ->
            if (!metadataText.contains(value)) {
                throw GradleException("NeoForge $minecraftVersion metadata is missing exact value: $value")
            }
        }

        val mixinConfig = commonProject.file("src/main/resources/$mixinConfigName")
        if (!mixinConfig.isFile) {
            throw GradleException("Missing matching common mixin config: $mixinConfig")
        }
        @Suppress("UNCHECKED_CAST")
        val mixin = JsonSlurper().parse(mixinConfig) as Map<String, Any?>
        val registeredMixins =
            ((mixin["mixins"] as? List<*>) ?: emptyList<Any>()) +
                ((mixin["client"] as? List<*>) ?: emptyList<Any>())
        if (registeredMixins.isEmpty()) {
            throw GradleException("NeoForge $minecraftVersion mixin config registers no mixins")
        }

        val atFile = accessTransformer.asFile
        if (!atFile.isFile) throw GradleException("Missing NeoForge $minecraftVersion access transformer: $atFile")
        val atPattern = Regex("^public(?:-f)?\\s+([^\\s]+)\\s+<init>(\\([^\\s]+)$")
        val actualTargets = linkedMapOf<String, String>()
        atFile.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith('#') }.forEach { line ->
            val match = atPattern.matchEntire(line)
                ?: throw GradleException("Malformed NeoForge $minecraftVersion AT line: $line")
            actualTargets[match.groupValues[1]] = match.groupValues[2]
        }
        if (actualTargets != expectedAccessTargets) {
            throw GradleException("NeoForge $minecraftVersion AT targets do not match its generated shape: $actualTargets")
        }

        val patchedJar = file("build/moddev/artifacts/minecraft-patched-$expectedNeoForgeVersion.jar")
        if (expectedAccessTargets.isNotEmpty() && !patchedJar.isFile) {
            throw GradleException("Missing matching patched Minecraft JAR: $patchedJar")
        }
        val javap = File(System.getProperty("java.home"), "bin/javap.exe")
        expectedAccessTargets.forEach { (className, descriptor) ->
            val process = ProcessBuilder(
                javap.absolutePath,
                "-classpath",
                patchedJar.absolutePath,
                "-p",
                "-s",
                className,
            ).redirectErrorStream(true).start()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw GradleException("Timed out validating NeoForge $minecraftVersion AT target $className")
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (process.exitValue() != 0 || !output.contains("descriptor: $descriptor")) {
                throw GradleException("NeoForge $minecraftVersion AT descriptor not found in $className: $descriptor")
            }
        }

        val classFile = layout.buildDirectory.file(
            "classes/java/main/${entrypointClass.replace('.', '/')}.class",
        ).get().asFile
        if (!classFile.isFile) throw GradleException("Entrypoint was not compiled: $classFile")
        val classBytes = classFile.readBytes()
        val majorVersion = ((classBytes[6].toInt() and 0xff) shl 8) or (classBytes[7].toInt() and 0xff)
        if (majorVersion != 69) {
            throw GradleException("NeoForge $minecraftVersion entrypoint is not Java 25 bytecode: $majorVersion")
        }
        val outputJar = tasks.named<Jar>("jar").get().archiveFile.get().asFile
        val runtimeClasspath = sourceSets.named("main").get().runtimeClasspath.files
        URLClassLoader(
            (listOf(outputJar) + runtimeClasspath).map { it.toURI().toURL() }.toTypedArray(),
            ClassLoader.getPlatformClassLoader(),
        ).use { runtimeLoader ->
            Class.forName(entrypointClass, false, runtimeLoader)
        }
        val commonClassSuffix = when (minecraftVersion) {
            "1.21.1" -> "com/github/slmpc/lumingraphics/mc/v1211/bridge/GlStateManagerBridge1211.class"
            "26.1.2" -> "com/github/slmpc/lumingraphics/mc/v2612/bridge/Blaze3DBridge2612.class"
            "26.2" -> "com/github/slmpc/lumingraphics/mc/v262/bridge/Blaze3DBridge262.class"
            else -> throw GradleException("Unsupported Minecraft version: $minecraftVersion")
        }
        val commonClass = commonProject.layout.buildDirectory.file("classes/java/main/$commonClassSuffix").get().asFile
        if (!commonClass.isFile) throw GradleException("Matching common output was not compiled: $commonClass")

        val commonNeoForgeImports = commonProject.fileTree("src/main/java") { include("**/*.java") }.files.filter {
            it.readText().contains("net.neoforged")
        }
        if (commonNeoForgeImports.isNotEmpty()) {
            throw GradleException("NeoForge types leaked outside loader module: $commonNeoForgeImports")
        }
        val ownVersionPackage = "lumingraphics.mc.v$versionKey"
        val crossVersionSources = fileTree("src/main/java") { include("**/*.java") }.files.filter {
            val text = it.readText()
            text.contains("lumingraphics.mc.v") && !text.contains(ownVersionPackage)
        }
        if (crossVersionSources.isNotEmpty()) {
            throw GradleException("Cross-version accessor leakage in ${project.path}: $crossVersionSources")
        }

        ZipFile(outputJar).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toSet()
            val requiredEntries = listOf(
                entrypointClass.replace('.', '/') + ".class",
                "META-INF/neoforge.mods.toml",
                "META-INF/accesstransformer.cfg",
                mixinConfigName,
                commonClassSuffix,
            )
            requiredEntries.forEach { entry ->
                if (entry !in entries) {
                    throw GradleException("NeoForge $minecraftVersion JAR is missing required entry: $entry")
                }
            }
            val ownVersionPrefix = "com/github/slmpc/lumingraphics/mc/v$versionKey/"
            if (entries.any { entry -> entry.startsWith("com/github/slmpc/lumingraphics/mc/v") && !entry.startsWith(ownVersionPrefix) }) {
                throw GradleException("NeoForge $minecraftVersion JAR contains cross-version classes")
            }
            if (minecraftVersion == "1.21.1" && entries.any { entry ->
                    entry.endsWith("/GlContextVersionMixin1211.class") ||
                        entry == "lumin_graphics_mc_1211.fabric.mixins.json"
                }) {
                throw GradleException("NeoForge 1.21.1 JAR must not modify the EarlyWindow OpenGL context")
            }
        }
        logger.lifecycle(
            "Validated NeoForge $minecraftVersion: NeoForm $expectedNeoFormVersion, " +
                "NeoForge $expectedNeoForgeVersion, ${registeredMixins.size} mixins, ${actualTargets.size} AT descriptors",
        )
    }
}

tasks.named("check") {
    dependsOn(verifyNeoForgeContract)
}

val smokeTaskNames = gradle.startParameter.taskNames.map { it.substringAfterLast(':') }
if (smokeTaskNames.any { it in listOf("runAllBridgeSmokes", "runAllBridgeNegativeSmokes") }) {
    val negative = "runAllBridgeNegativeSmokes" in smokeTaskNames
    val evidenceDir = System.getenv("LUMIN_MC_SMOKE_EVIDENCE_DIR")
        ?: "D:/Dev/ChenMeng/LuminGraphics/.omo/start-work/attempts/lumin-graphics-prism-rhi-migration-20260730/todo22-smokes"
    val smokeRunDir = layout.buildDirectory.dir("todo22-run-${if (negative) "negative" else "positive"}")
    tasks.matching { it.name == "runClient" }.configureEach {
        val smokeOutput = File(
            evidenceDir,
            "${moduleSpec.required("stableId")}-${if (negative) "negative" else "positive"}.json",
        )
        withGroovyBuilder {
            "environment"("LUMIN_MC_SMOKE_MODE", if (negative) moduleSpec.required("negativeMode") else "positive")
            "environment"("LUMIN_MC_SMOKE_OUTPUT", smokeOutput.absolutePath)
        }
        doFirst {
            delete(smokeOutput)
            if (!negative) delete(File(smokeOutput.parentFile, smokeOutput.name.replace(".json", ".png")))
            delete(smokeRunDir)
            this@configureEach.withGroovyBuilder {
                "workingDir"(smokeRunDir.get().asFile)
            }
        }
        doLast { delete(smokeRunDir) }
    }
}
