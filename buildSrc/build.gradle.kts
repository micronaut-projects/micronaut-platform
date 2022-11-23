plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("io.micronaut.build.internal.publishing:io.micronaut.build.internal.publishing.gradle.plugin:6.1.0")
}
