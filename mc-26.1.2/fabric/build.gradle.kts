apply(from = rootProject.file("gradle/fabric-module.gradle"))

dependencies {
    add("minecraft", "com.mojang:minecraft:26.1.2")
    add("implementation", "net.fabricmc:fabric-loader:0.19.2")
    add("implementation", "net.fabricmc.fabric-api:fabric-api:0.150.0+26.1.2")
}
