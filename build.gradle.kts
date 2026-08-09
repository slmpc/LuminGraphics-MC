import groovy.json.JsonSlurper
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withGroovyBuilder
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO

plugins {
    java
    alias(libs.plugins.loom) apply false
    alias(libs.plugins.moddev) apply false
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

fun org.gradle.api.artifacts.dsl.RepositoryHandler.addProjectRepositories() {
    exclusiveContent {
        forRepository { mavenLocal() }
        filter {
            includeGroup("com.github.slmpc.lumingraphics")
            includeGroup("com.github.slmpc.prismrhi")
        }
    }
    providers.gradleProperty("localRepository")
        .orElse(providers.gradleProperty("publishRepository"))
        .orNull
        ?.let { repository -> maven { url = uri(repository) } }
    mavenCentral()
    maven(url = "https://slmpc.github.io/maven-repository/")
    maven(url = "https://libraries.minecraft.net")
    maven(url = "https://maven.fabricmc.net")
    maven(url = "https://maven.neoforged.net/releases")
}

repositories.addProjectRepositories()

allprojects {
    group = rootProject.group
    version = rootProject.version
    configurations.configureEach {
        resolutionStrategy.cacheChangingModulesFor(0, "seconds")
    }
}

subprojects {
    repositories.addProjectRepositories()
    pluginManager.withPlugin("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(25))
            withSourcesJar()
        }
        tasks.withType<JavaCompile>().configureEach {
            options.release.set(25)
            options.encoding = "UTF-8"
        }
        tasks.withType<Jar>().configureEach {
            from(rootProject.file("LICENSE")) {
                into("META-INF")
                rename { "LICENSE-LuminGraphics-MC" }
            }
            from(rootProject.file("COPYING")) {
                into("META-INF")
                rename { "COPYING-GPL-3.0" }
            }
        }
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.tomlj:tomlj:1.1.1")
    testImplementation(libs.junit)
    testImplementation(gradleTestKit())
    testRuntimeOnly(libs.junit.launcher)
}

val neoform2612 by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
val neoform262 by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
dependencies {
    neoform2612("net.neoforged:neoform:${libs.versions.neoform.v2612.get()}@zip")
    neoform262("net.neoforged:neoform:${libs.versions.neoform.v262.get()}@zip")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    exclude("**/ArchitectureGuardTest.class")
    listOf("baseline.26.1.2", "baseline.26.2", "baseline.root.26.1.2", "baseline.root.26.2")
        .forEach { propertyName ->
            val value = System.getProperty(propertyName)
            if (value != null) {
                systemProperty(propertyName, value)
            }
            inputs.property(propertyName, value ?: "<default>")
        }
}

@Suppress("UNCHECKED_CAST")
val minecraftLeafSpecs = gradle.extra["minecraftLeafSpecs"] as Map<String, Map<String, String?>>

fun minecraftLeafSpec(projectPath: String): Map<String, String?> =
    minecraftLeafSpecs[projectPath]
        ?: throw GradleException(
            "Unknown Minecraft leaf project path '$projectPath'. Expected one of: ${minecraftLeafSpecs.keys.joinToString()}",
        )

fun Map<String, String?>.required(name: String): String =
    requireNotNull(this[name]) { "Minecraft leaf spec '$name' is missing" }

fun registerGeneratedBaseline(
    suffix: String,
    minecraftVersion: String,
    neoformVersion: String,
    originSha: String,
    originConfiguration: Configuration,
): Pair<TaskProvider<JavaExec>, TaskProvider<JavaExec>> {
    val commonSpec = minecraftLeafSpec(":mc-$minecraftVersion:common")
    val fabricSpec = minecraftLeafSpec(":mc-$minecraftVersion:fabric")
    val outputDir = layout.buildDirectory.dir("generated-baselines/$minecraftVersion")
    val loomSources = providers.provider {
        val task = project(fabricSpec.required("projectPath"))
            .tasks.named("genSourcesWithVineflower").get()
        val property = task.withGroovyBuilder { getProperty("sourcesOutputJar") } as RegularFileProperty
        property.get().asFile
    }
    val moddevSources = layout.projectDirectory.file(
        "${commonSpec.required("physicalDir")}/build/moddev/artifacts/vanilla-$neoformVersion-sources.jar",
    )
    val generateTask = tasks.register<JavaExec>("generate${suffix}Baseline") {
        group = "minecraft matrix"
        description = "Materializes independently generated Loom and ModDev sources for Minecraft $minecraftVersion."
        dependsOn(
            tasks.named("classes"),
            "${fabricSpec.required("projectPath")}:genSources",
            "${commonSpec.required("projectPath")}:createMinecraftArtifacts",
        )
        inputs.file(loomSources)
        inputs.files(moddevSources, originConfiguration)
        outputs.dir(outputDir)
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("com.github.slmpc.lumingraphics.mc.baseline.GeneratedBaselineTool")
        doFirst {
            delete(outputDir)
            setArgs(
                listOf(
                    "generate",
                    minecraftVersion,
                    libs.versions.loom.get(),
                    libs.versions.moddev.get(),
                    "net.neoforged:neoform:$neoformVersion@zip",
                    "https://maven.neoforged.net/releases/net/neoforged/neoform/$neoformVersion/neoform-$neoformVersion.zip",
                    originConfiguration.singleFile,
                    originSha,
                    loomSources.get(),
                    moddevSources.asFile,
                    outputDir.get().asFile,
                ),
            )
        }
    }
    val verifyTask = tasks.register<JavaExec>("verify${suffix}Baseline") {
        group = "verification"
        dependsOn(tasks.named("classes"), generateTask)
        inputs.dir(outputDir)
        inputs.dir(layout.projectDirectory.dir("reference/vanilla-$minecraftVersion"))
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("com.github.slmpc.lumingraphics.mc.baseline.GeneratedBaselineTool")
        args("verify", outputDir.get().asFile, file("reference/vanilla-$minecraftVersion"))
    }
    return generateTask to verifyTask
}

val baseline2612 = registerGeneratedBaseline(
    "2612",
    libs.versions.minecraft.v2612.get(),
    libs.versions.neoform.v2612.get(),
    "3eb9f8cc282badfbc210a27a8304f14cc58fa40401a94072874dfaded3c8cb52",
    neoform2612,
)
val baseline262 = registerGeneratedBaseline(
    "262",
    libs.versions.minecraft.v262.get(),
    libs.versions.neoform.v262.get(),
    "09fd01e2371a94c78bc4d0d28645ab88206fcef395795182cee841765ebd5a63",
    neoform262,
)

tasks.register("downloadMinecraftSources") {
    group = "minecraft matrix"
    description = "Runs Loom and ModDev source production for both supported Minecraft baselines."
    dependsOn(baseline2612.first, baseline262.first)
}

tasks.register("verifyMinecraftSources") {
    group = "verification"
    description = "Parses and verifies selected Mojang source baselines and their SHA-256 manifests."
    dependsOn(tasks.named("test"), "verifyWrapperChecksum", baseline2612.second, baseline262.second)
}

fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes())
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

tasks.register("verifyWrapperChecksum") {
    val wrapperJar = file("gradle/wrapper/gradle-wrapper.jar")
    val checksumFile = file("gradle/wrapper/gradle-wrapper.jar.sha256")
    inputs.files(wrapperJar, checksumFile)
    doLast {
        val expected = checksumFile.readText().trim()
        val actual = sha256(wrapperJar)
        if (actual != expected) {
            throw GradleException("Gradle wrapper JAR checksum mismatch: expected $expected, got $actual")
        }
    }
}

val minecraftModules = minecraftLeafSpecs.values.toList()
val publishedProjectPaths = listOf(":bridge-contract") + minecraftModules.map { it.required("projectPath") }
val loaderVariants = minecraftModules.filter { it["role"] != "common" }.map { it.required("projectPath") }
val luminVersion = libs.versions.lumin.get()
val prismVersion = libs.versions.prism.get()

gradle.allprojects {
    if (path in publishedProjectPaths) {
        val minecraftSpec = minecraftLeafSpecs[path]
        pluginManager.withPlugin("java") {
            pluginManager.apply("maven-publish")
            extensions.configure<PublishingExtension> {
                val publishRepository = providers.gradleProperty("publishRepository").orNull
                publishRepository?.let { repository ->
                    repositories.maven {
                        name = "localRelease"
                        url = uri(repository)
                    }
                }
                publications.create<MavenPublication>("mavenJava") {
                    from(components.getByName("java"))
                    artifactId = minecraftSpec?.required("archiveBaseName") ?: project.name
                    pom {
                        name.set(artifactId)
                        description.set("LuminGraphics-MC ${project.path} artifact")
                        licenses {
                            license {
                                name.set("GNU Lesser General Public License v3.0 only")
                                url.set("https://www.gnu.org/licenses/lgpl-3.0.html")
                                distribution.set("repo")
                            }
                        }
                    }
                }
                if (publishRepository == null) {
                    tasks.named("publish") {
                        dependsOn("publishToMavenLocal")
                    }
                }
            }
        }
    }
}

tasks.register<JavaExec>("verifyVariantJars") {
    group = "verification"
    description = "Recursively verifies the four self-contained loader artifacts and their resolved provenance."
    dependsOn(tasks.named("classes"))
    loaderVariants.forEach { variant -> dependsOn("$variant:jar") }
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.github.slmpc.lumingraphics.mc.packaging.VariantJarVerifier")
    doFirst {
        val resolvedArtifacts = linkedSetOf<File>()
        loaderVariants.forEach { variant ->
            resolvedArtifacts.addAll(project(variant).configurations.getByName("luminGraphicsShadow").files)
        }
        if (resolvedArtifacts.size != 9) {
            throw GradleException("Expected nine resolved bridge/Lumin/Prism artifacts, got ${resolvedArtifacts.size}")
        }
        val arguments = mutableListOf<Any>(layout.projectDirectory.asFile)
        arguments.addAll(resolvedArtifacts)
        providers.gradleProperty("variantJarOverride").orNull?.let { arguments.add(file(it)) }
        setArgs(arguments)
    }
}

tasks.register("buildAllVariants") {
    group = "build"
    description = "Builds and verifies all six modules and four final dependency-mod artifacts."
    minecraftModules.forEach { module -> dependsOn("${module.required("projectPath")}:check") }
    loaderVariants.forEach { variant ->
        dependsOn("$variant:clean", "$variant:build")
        project(variant).tasks.configureEach {
            if (name != "clean") {
                mustRunAfter(project(variant).tasks.named("clean"))
            }
        }
    }
    dependsOn("verifyVariantJars")
}

tasks.register("verifyPublishedCoordinates") {
    group = "verification"
    description = "Inspects and freshly resolves every current publication from the explicit local Maven repository."
    doLast {
        if (project.version.toString() != "1.2.5") {
            throw GradleException("Unexpected development publication version: ${project.version}")
        }
        val repositoryProperty = providers.gradleProperty("publishRepository").orNull
            ?: throw GradleException("verifyPublishedCoordinates requires -PpublishRepository=<local Maven path>")
        val repositoryRoot = file(repositoryProperty).canonicalFile
        val coordinates = listOf("bridge-contract") + minecraftModules.map { it.required("archiveBaseName") }
        coordinates.forEach { artifactId ->
            val publicationVersion = project.version.toString()
            val coordinateDirectory = File(
                repositoryRoot,
                "com/github/slmpc/lumingraphics/mc/$artifactId/$publicationVersion",
            )
            val jarFile = File(coordinateDirectory, "$artifactId-$publicationVersion.jar")
            val pomFile = File(coordinateDirectory, "$artifactId-$publicationVersion.pom")
            if (!jarFile.isFile || jarFile.length() == 0L || !pomFile.isFile || pomFile.length() == 0L) {
                throw GradleException("Missing published JAR/POM for $artifactId: $coordinateDirectory")
            }
            val detached = configurations.detachedConfiguration(
                dependencies.create("com.github.slmpc.lumingraphics.mc:$artifactId:$publicationVersion"),
            )
            detached.isTransitive = false
            detached.resolutionStrategy.cacheChangingModulesFor(0, "seconds")
            detached.resolutionStrategy.cacheDynamicVersionsFor(0, "seconds")
            val resolved = detached.singleFile.canonicalFile
            val digest = sha256(jarFile)
            if (sha256(resolved) != digest) {
                throw GradleException("Fresh resolution is stale for $artifactId: $resolved")
            }
            println(
                "PUBLISHED_COORDINATE_OK gav=com.github.slmpc.lumingraphics.mc:$artifactId:$publicationVersion " +
                    "resolvedVersion=$publicationVersion sha256=$digest jar=$jarFile pom=$pomFile cache=$resolved",
            )
        }
    }
}

val bridgeSmokeVariants = loaderVariants
val bridgeSmokeEvidence = file(
    System.getenv("LUMIN_MC_SMOKE_EVIDENCE_DIR")
        ?: "D:/Dev/ChenMeng/LuminGraphics/.omo/start-work/attempts/lumin-graphics-prism-rhi-migration-20260730/todo22-smokes",
)

@Suppress("UNCHECKED_CAST")
fun validateBridgeSmokeArtifacts(negative: Boolean) {
    val suffix = if (negative) "negative" else "positive"
    bridgeSmokeVariants.forEach { path ->
        val spec = minecraftLeafSpec(path)
        val jsonFile = File(bridgeSmokeEvidence, "${spec.required("stableId")}-$suffix.json")
        if (!jsonFile.isFile || jsonFile.length() == 0L) {
            throw GradleException("Missing terminal bridge smoke artifact: $jsonFile")
        }
        val receipt = JsonSlurper().parse(jsonFile) as Map<String, Any?>
        val expectedMode = if (negative) spec["negativeMode"] else "positive"
        if (receipt["pass"] != true || receipt["cleanup"] != true || receipt["mode"] != expectedMode) {
            throw GradleException("Bridge smoke did not pass: $jsonFile: $receipt")
        }
        if (negative) {
            if (
                receipt["expectedFailure"] != receipt["observedFailure"] ||
                receipt["beforeDraw"] != true || receipt["beforeDeletion"] != true
            ) {
                throw GradleException("Negative smoke did not fail before native work: $jsonFile")
            }
            return@forEach
        }
        val objects = receipt["objects"] as? List<Map<String, Any?>>
        val completeObjects = objects?.size == 3 && objects.all { objectReceipt ->
            val directions = objectReceipt["directions"] as? List<Map<String, Any?>>
            directions?.size == 2 && directions.all { direction ->
                direction["mode"] == "BORROWED_ZERO_COPY" && direction["nativeId"] is Number
            } && objectReceipt["preCloseGlIs"] == true && objectReceipt["borrowedCloseGlIs"] == true &&
                objectReceipt["ownerCloseGlIs"] == false
        }
        if (!completeObjects) {
            throw GradleException("Incomplete zero-copy object matrix: $jsonFile")
        }
        listOf("sampler", "pipeline", "encoder", "renderPass", "borrowedCloseLive", "ownerCloseDeleted")
            .forEach { field ->
                if (receipt[field] != true) {
                    throw GradleException("Missing $field proof: $jsonFile")
                }
            }
        val pngFile = File(jsonFile.parentFile, jsonFile.name.replace(".json", ".png"))
        val image = ImageIO.read(pngFile)
        if (image == null || image.width != 8 || image.height != 8) {
            throw GradleException("Invalid PNG: $pngFile")
        }
        val colors = HashSet<Int>()
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                colors.add(image.getRGB(x, y))
            }
        }
        if (colors.size < 4) {
            throw GradleException("PNG is blank or lacks deterministic colors: $pngFile")
        }
        if (receipt["pixelHash"] != sha256(pngFile)) {
            throw GradleException("PNG hash mismatch: $pngFile")
        }
    }
}

fun registerBridgeSmokeMatrix(taskName: String, negative: Boolean) {
    tasks.register(taskName) {
        group = "verification"
        description = "Runs all four real Minecraft bridge ${if (negative) "negative " else ""}smokes sequentially."
        bridgeSmokeVariants.forEach { dependsOn("$it:runClient") }
        doLast { validateBridgeSmokeArtifacts(negative) }
    }
}

registerBridgeSmokeMatrix("runAllBridgeSmokes", false)
registerBridgeSmokeMatrix("runAllBridgeNegativeSmokes", true)

if (
    gradle.startParameter.taskNames.any {
        it.substringAfterLast(':') in listOf("runAllBridgeSmokes", "runAllBridgeNegativeSmokes")
    }
) {
    gradle.projectsEvaluated {
        var prior: TaskProvider<*>? = null
        bridgeSmokeVariants.forEach { path ->
            val current = project(path).tasks.named("runClient")
            prior?.let { previous -> current.configure { mustRunAfter(previous) } }
            prior = current
        }
    }
}

val architectureModules = listOf(":bridge-contract") + minecraftModules.map { it.required("projectPath") }
val bridgeContract = project(":bridge-contract")
evaluationDependsOn(bridgeContract.path)
val bridgeContractSourceSets = bridgeContract.extensions.getByType<org.gradle.api.tasks.SourceSetContainer>()

val architectureMatrixCheck = bridgeContract.tasks.register<Test>("architectureMatrixCheck") {
    group = "verification"
    dependsOn(bridgeContract.tasks.named("testClasses"), baseline2612.first, baseline262.first)
    testClassesDirs = bridgeContractSourceSets.named("test").get().output.classesDirs
    classpath = bridgeContractSourceSets.named("test").get().runtimeClasspath
    include("**/BridgeMatrixTest.class")
    useJUnitPlatform()
    testLogging.showStandardStreams = true
    systemProperty("baseline.root.26.1.2", layout.buildDirectory.dir("generated-baselines/26.1.2").get().asFile)
    systemProperty("baseline.root.26.2", layout.buildDirectory.dir("generated-baselines/26.2").get().asFile)
}

tasks.register<Test>("architectureCheck") {
    group = "verification"
    description = "Checks reflection, loader, version, GAV, service, window, JAR, and bridge-matrix boundaries."
    dependsOn(tasks.named("testClasses"), architectureMatrixCheck)
    architectureModules.forEach { module ->
        dependsOn("$module:classes")
        if (module.contains("fabric") || module.contains("neoforge")) {
            dependsOn("$module:jar")
        }
    }
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/ArchitectureGuardTest.class")
    useJUnitPlatform()
    testLogging.showStandardStreams = true
    systemProperty("lumin.mc.root", rootProject.projectDir.absolutePath)
    doLast {
        var dependencyCount = 0
        architectureModules.forEach { path ->
            val module = project(path)
            listOf("api", "implementation", "compileOnly", "runtimeOnly").forEach configurationLoop@{ name ->
                val configuration = module.configurations.findByName(name) ?: return@configurationLoop
                configuration.dependencies.forEach { dependency ->
                    dependencyCount++
                    if (dependency.version == "0.0.1") {
                        throw GradleException(
                            "stale 0.0.1 GAV in $path:$name: ${dependency.group}:${dependency.name}:${dependency.version}",
                        )
                    }
                    val expectedVersion = when (dependency.group) {
                        "com.github.slmpc.lumingraphics" -> luminVersion
                        "com.github.slmpc.prismrhi" -> prismVersion
                        else -> null
                    }
                    if (expectedVersion != null && dependency.version != null && dependency.version != expectedVersion) {
                        throw GradleException(
                            "stale published GAV in $path:$name: ${dependency.group}:${dependency.name}:${dependency.version}",
                        )
                    }
                }
            }
        }
        if (dependencyCount == 0) {
            throw GradleException("architecture dependency graph inspection was empty")
        }
        println("ARCH_MC_DEPENDENCIES count=$dependencyCount modules=${architectureModules.size}")
    }
}
