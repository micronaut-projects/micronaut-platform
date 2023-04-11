plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("io.micronaut.build.internal.publishing:io.micronaut.build.internal.publishing.gradle.plugin:6.3.5")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
