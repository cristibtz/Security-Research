plugins {
    kotlin("jvm") version "1.8.22"
    kotlin("plugin.serialization") version "1.8.22"
    application
}

group = "com.example.vulnerable"
version = "1.0.0"

application {
    mainClass.set("com.example.vulnerable.SimpleXXEKt")
    
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor core dependencies with VULNERABLE XMLUtil 0.86.1
    implementation("io.ktor:ktor-server-core-jvm:2.3.4")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.4")
    
    // Content negotiation with VULNERABLE XMLUtil 0.86.1
    implementation("io.ktor:ktor-server-content-negotiation:2.3.4")
    implementation("io.ktor:ktor-serialization-kotlinx-xml:2.3.4")
    
    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    
    implementation("ch.qos.logback:logback-classic:1.4.11")
    
    testImplementation("io.ktor:ktor-server-tests-jvm:2.3.4")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.8.22")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}
