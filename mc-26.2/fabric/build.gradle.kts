apply(from = rootProject.file("gradle/fabric-module.gradle"))

dependencies {
    add("minecraft", "com.mojang:minecraft:26.2")
    add("implementation", "net.fabricmc:fabric-loader:0.19.3")
    add("implementation", "net.fabricmc.fabric-api:fabric-api:0.156.0+26.2")
}
