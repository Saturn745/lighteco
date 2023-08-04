plugins {
    id("lighteco.shadow-logic")
}

dependencies {
    api(project(":lighteco-api"))

    implementation("org.spongepowered:configurate-yaml:4.0.0")

    compileOnly("org.projectlombok:lombok:1.18.28")
    annotationProcessor("org.projectlombok:lombok:1.18.28")

    compileOnly("org.checkerframework:checker-qual:3.8.0")
    compileOnly("org.jetbrains:annotations:20.1.0")
}