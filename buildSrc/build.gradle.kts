import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("io.micronaut.build.internal.publishing:io.micronaut.build.internal.publishing.gradle.plugin:8.0.0")
    implementation("org.tomlj:tomlj:1.1.1")
    testImplementation(kotlin("test"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
    this.compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
