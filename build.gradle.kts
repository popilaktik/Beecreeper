plugins {
    java
}

group = "ru.beecreeper"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Purpur основан на Paper, поэтому плагин собирается против Paper API
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

java {
    // Minecraft 26.1+ требует Java 25
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveFileName.set("BeeCreeper-${project.version}.jar")
}
