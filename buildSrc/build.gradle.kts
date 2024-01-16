import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("io.micronaut.build.internal.publishing:io.micronaut.build.internal.publishing.gradle.plugin:6.6.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    this.compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}
