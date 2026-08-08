apply(from = rootProject.file("gradle/fabric-module.gradle.kts"))

dependencies {
    add("minecraft", libs.minecraft.v2612)
    add("implementation", libs.fabric.loader.v2612)
    add("implementation", libs.fabric.api.v2612)
}
