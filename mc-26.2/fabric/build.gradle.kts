apply(from = rootProject.file("gradle/fabric-module.gradle.kts"))

dependencies {
    add("minecraft", libs.minecraft.v262)
    add("implementation", libs.fabric.loader.v262)
    add("implementation", libs.fabric.api.v262)
}
