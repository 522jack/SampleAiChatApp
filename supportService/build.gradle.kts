plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "com.claude.support"
version = "1.0.0"

dependencies {
    // Shared module dependency
    implementation(project(":shared"))

    // Ktor Server
    implementation("io.ktor:ktor-server-core:3.0.1")
    implementation("io.ktor:ktor-server-netty:3.0.1")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.1")
    implementation("io.ktor:ktor-server-cors:3.0.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.1")

    // Ktor Client (for OLLAMA and Claude API)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)

    // Kotlinx
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    // Logging
    implementation(libs.napier)

    // Testing
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.claude.support.ApplicationKt")
}

// Set working directory for the run task
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

kotlin {
    jvmToolchain(17)
}

// Handle duplicate files in distribution
tasks.withType<Tar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Zip> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}