plugins {
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
}

intellij {
    version.set("2023.2.5")  // 현재 IntelliJ 버전에 맞춤
    type.set("IC")           // Community Edition
    plugins.set(listOf("org.jetbrains.plugins.terminal"))
}

kotlin {
    jvmToolchain(17)
}

tasks {
    patchPluginXml {
        sinceBuild.set("232")      // IntelliJ 2023.2에 맞춤
        untilBuild.set("232.*")    // 2023.2.x 버전까지 지원
    }

    runIde {
        jvmArgs = listOf("-Xmx2048m")
    }
}