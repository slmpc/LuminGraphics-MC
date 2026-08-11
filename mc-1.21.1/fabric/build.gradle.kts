import org.gradle.kotlin.dsl.withGroovyBuilder

apply(from = rootProject.file("gradle/fabric-module.gradle.kts"))

dependencies {
    add("minecraft", libs.minecraft.v1211)
    add("mappings", requireNotNull(project.extensions.getByName("loom").withGroovyBuilder {
        "officialMojangMappings"()
    }))
    add("modImplementation", libs.fabric.loader.v1211)
}
