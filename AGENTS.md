# Micronaut Platform Agent Guidance

This repository publishes the Micronaut Platform BOM, the Gradle version catalog,
and the Micronaut parent POM. Keep changes focused on dependency alignment,
release metadata, generated catalog output, and the public documentation that
explains how users consume the platform.

## Repository Layout

- `gradle/libs.versions.toml` is the source of managed platform versions.
  Versions prefixed with `managed-` are exposed through the platform BOM, and
  versions prefixed with `parent-` feed the parent POM.
- `platform/` owns the platform BOM and Gradle catalog generation. Update
  `platform/build.gradle.kts` only for BOM behavior, suppressions, accepted
  regressions, or compatibility checks.
- `parent/` owns the generated Maven parent POM. Keep parent plugin and property
  changes aligned with the `parent-*` catalog entries.
- `src/main/docs/guide/` contains the published guide. `toc.yml` is the guide
  navigation source of truth; keep it in sync with any added or renamed `.adoc`
  files.

## Change Rules

- Do not add application code to the root project. The root coordinates the
  build, documentation, and publication setup.
- Prefer catalog updates over hard-coded dependency versions in build files.
- Treat platform version changes as user-visible dependency management changes:
  check whether `src/main/docs/guide/releaseHistory.adoc` or
  `src/main/docs/guide/breaks.adoc` needs an entry.
- Preserve accepted regression and suppression comments in
  `platform/build.gradle.kts`; add concise rationale when a new suppression is
  required.
- Keep synchronized template files, especially `.github/workflows/*`, aligned
  with `micronaut-project-template` unless the task explicitly asks for a local
  override.

## Contributing Guidelines

- Before opening or updating a pull request, read this repository's
  `CONTRIBUTING.md` and follow every repo-specific PR requirement it names.
- Treat contributor-checklist items as handoff requirements. If a requirement is
  not applicable, state that explicitly in the PR description or handoff note.
- For UI-visible changes, confirm whether screenshots or other visual evidence
  are required and include them in the PR description; if screenshots cannot be
  provided, explain why and describe the verification that was performed.

## Verification

Use the Gradle wrapper. This repository currently builds on Java 25, matching
the Java CI workflow.

- Full verification: `./gradlew check jacocoReport --no-daemon --continue`
- Platform BOM/catalog checks: `./gradlew :micronaut-platform:check`
- Parent POM checks: `./gradlew :micronaut-parent:check`
- Guide assembly: `./gradlew publishGuide` or `./gradlew pG`
- Guide plus API docs: `./gradlew docs`

For documentation-only guidance changes, at minimum validate the edited Markdown
or AsciiDoc and run the smallest Gradle docs task that exercises the changed
surface when practical.
