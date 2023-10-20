<!-- Checklist: https://github.com/micronaut-projects/micronaut-core/wiki/New-Module-Checklist -->

# Micronaut Platform

[![Maven Central](https://img.shields.io/maven-central/v/io.micronaut.platform/micronaut-platform.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.micronaut.platform%22%20AND%20a:%22micronaut-platform%22)
[![Build Status](https://github.com/micronaut-projects/micronaut-platform/workflows/Java%20CI/badge.svg)](https://github.com/micronaut-projects/micronaut-platform/actions)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=micronaut-projects_micronaut-platform&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=micronaut-projects_micronaut-platform)
[![Revved up by Develocity](https://img.shields.io/badge/Revved%20up%20by-Develocity-06A0CE?logo=Gradle&labelColor=02303A)](https://ge.micronaut.io/scans)

Micronaut project-template

## Documentation

See the [Documentation](https://micronaut-projects.github.io/micronaut-platform/latest/guide/) for more information.

See the [Snapshot Documentation](https://micronaut-projects.github.io/micronaut-platform/snapshot/guide/) for the current development docs.

## Snapshots and Releases

Snapshots are automatically published to [Sonatype Snapshots](https://s01.oss.sonatype.org/content/repositories/snapshots/io/micronaut/) using [Github Actions](https://github.com/micronaut-projects/micronaut-platform/actions).

See the documentation in the [Micronaut Docs](https://docs.micronaut.io/latest/guide/index.html#usingsnapshots) for how to configure your build to use snapshots.

Releases are published to Maven Central via [Github Actions](https://github.com/micronaut-projects/micronaut-platform/actions).

Releases are completely automated. To perform a release use the following steps:

* [Publish the draft release](https://github.com/micronaut-projects/micronaut-bom/releases). There should be already a draft release created, edit and publish it. The Git Tag should start with `v`. For example `v1.0.0`.
* [Monitor the Workflow](https://github.com/micronaut-projects/micronaut-bom/actions?query=workflow%3ARelease) to check it passed successfully.
* If everything went fine, [publish to Maven Central](https://github.com/micronaut-projects/micronaut-bom/actions?query=workflow%3A"Maven+Central+Sync").
* Celebrate!