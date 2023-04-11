plugins {
    id("io.micronaut.build.internal.docs")
    id("io.micronaut.build.internal.quality-reporting")
}
val snapshotRewrite = tasks.register<io.micronaut.build.internal.SnapshotRewrite>("snapshotRewrite") {
    versions.convention(layout.projectDirectory.file("gradle/libs.versions.toml"))
}
