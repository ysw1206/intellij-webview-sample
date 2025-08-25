plugins {
    kotlin("jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    maven("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases")
}

dependencies {
    implementation(kotlin("stdlib"))
    // pty4j 의존성 (정확한 버전)
    implementation("org.jetbrains.pty4j:pty4j:0.12.13")
    // 또는 최신 버전 시도
    implementation("org.jetbrains.pty4j:pty4j:0.12.10")
    
    // Jackson JSON 처리
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
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