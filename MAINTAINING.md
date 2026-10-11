# Micronaut module maintenance tasks

## Triage incoming issues

New issues need to be categorized. At least with one of the following labels:

* `type: bug`: when something is not working as designed.
* `type: improvement`: a minor improvement over an existing feature.
* `type: enhancement`: a completely new feature.
* `type: docs`: documentation change.

There are other labels that are useful for changelog generation:

* `type: breaking`.
* `type: deprecated`.
* `type: removed`.

Issues with the above labels will show up in their own section in the changelog.

Sometimes, before accepting bugs, we need to ask more information to the requester, or need to validate ourselves that it
is actually a bug. There are some labels to help with these situations:

* `status: awaiting feedback`: used to mark that we are waiting for more information to the user.
* `status: awaiting validation`: we need to validate ourselves that it is actually an issue.
* `status: awaiting third-party`: the issue is blocked by another bug in a third-party library

Note that when the blockers are cleared, the awaiting labels need to be manually removed. There are some other labels
around this:

* `status: validated`: the issue is ready to be being worked on.
* `status: acknowledged` (possibly duplicate?).
* `status: in progress`: (could be removed, we assign issues to mark them being worked on)

There are sometimes where we are not sure whether we want or can solve an issue. The labels about this are:

* `status: under consideration`: the issue is being considered, but has not been accepted yet.
* `status: future consideration`: we won't fix it now (because either we can't or we don't want to), but this can be
  revisited in the future.
* `status: next major version`: it is a breaking change and therefore needs to be implemented in the next major version.

There are also a bunch of `relates-to` labels that can be used to further categorize issues. This is helpful in projects
with a lot of issues, or projects where different people work on different parts or modules.

The majority of the issues are defined in the
[management](https://github.com/micronaut-projects/management/blob/master/labels.tf) repo, and propagated via Terraform.
If you want new labels:

* If they can be beneficial to several repos, send a pull request to the management repo.
* If they are repo-specific, just go ahead and create them with the GitHub UI.

Finally, issues (especially bugs) should be prioritized with either `priority: high`, `priority: medium` or
`priority: low`. Checkout the
[Issue Priority Labels](https://github.com/micronaut-projects/micronaut-core/wiki/Issue-Priority-Labels) document for
guidelines about when to use each of them.

## Review pull requests

Pull requests, regardless of whether they are created by internal or external contributors, should meet the following
criteria:

* All the GitHub checks are passing (CLA signed and builds passing).
* Code has a minimum quality, it uses the Micronaut APIs correctly, doesn't contain bad smells, etc. Essentially, the
  type of things you would review in every other software project.
* Contains tests.
* Includes documentation.
* If it closes any issues,
  [they should be linked](https://docs.github.com/en/free-pro-team@latest/github/managing-your-work-on-github/linking-a-pull-request-to-an-issue)
  either using closing keywords, or manually.

Regarding the target branch, backwards-compatible bug fixes and improvements typically target the default branch,
backwards-compatible enhancements target the next minor version branch, and breaking changes target the next major version
branch. Check the
[Micronaut Module Versioning](https://github.com/micronaut-projects/micronaut-core/wiki/Micronaut-Module-Versioning)
document for more information.

Before merging pull requests, it is really important to ensure they target the correct branch, so that in the next
patch/minor release we don't leak breaking changes. Check the
[Micronaut Module Branch Naming](https://github.com/micronaut-projects/micronaut-core/wiki/Micronaut-Module-Branch-Naming)
document for more information.

Note that
[Micronaut Core and Starter](https://github.com/micronaut-projects/micronaut-core/wiki/Micronaut-Core-and-Starter-Branching-Strategy)
follow a slightly different strategy.

### Automated pull requests

#### Dependency upgrades

All Micronaut repos have 2 dependency upgrade checking mechanism:

1. Renovate: it has the advantage that it performs dependency upgrades not only on build dependencies, but also on
   GitHub Actions workflows. On the other hand, its biggest downside is that it's unable to find newer versions for
   those defined in `gradle.properties`. It will also send different PRs for the same version upgrade if the artifact ID
   is different. For example, if you have `com.example:client:1.0` and `com.example:server:1.0`, and a new 1.1 version
   arrives for both, it will send 2 PRs, where they should both be upgraded at the same time.

2. To overcome those disadvantages, we have our own dependency upgrade solution based on the
   [Gradle Use Latest Versions Plugin](https://github.com/patrikerdes/gradle-use-latest-versions-plugin). It runs daily
   during weekdays.

The consequence of having both approaches in place is that we get multiple dependency upgrade PRs: one created by
`micronaut-build` via our automation, and one or many (one per dependency) created by Renovate. When merging those, it
is better to prefer the `micronaut-build` ones, if possible, for 2 reasons: a) they attempt to upgrade multiple dependencies
in a single PR, which creates less noise in the Git history; b) Once you merge that, Renovate will react and automatically
close its own PRs if the dependency is up-to-date.

When an upgrade to a new version arrives, we need to be careful when merging, so that we don't introduce an
unnecessary upgrade burden on our users. Read the
[Module Upgrade Strategy](https://github.com/micronaut-projects/micronaut-core/wiki/Module-Upgrade-Strategy) for more
information.

Note that if a new version arrives, and we are not ready yet to do the upgrade, you need to
[pin the old version](https://github.com/micronaut-projects/micronaut-build/#configuration-options), because otherwise,
Renovate and our workflow will keep sending PRs. You should also create an issue to upgrade so that it's not forgotten.

#### Files sync

We have a [template repo](https://github.com/micronaut-projects/micronaut-project-template) that we use as the single
source of truth for certain files. It is used as a template to create new repos, and changes to certain files in the
template repo will get propagated automatically. The files propagated are:

* Workflow files (`.github/workflows/*`). They are copied using rsync:
  * `central-sync.yml`.
  * `graalvm-dev.yml`.
  * `graalvm-latest.yml`. Java CI (`gradle.yml`) calls it after its build job passes, so a repository that keeps its own
    `gradle.yml` must add the `native` job itself.
  * `gradle.yml`.
  * `publish-snapshot.yml`.
  * `release.yml`.
  * `sonatype.yml`.

* Renovate configuration (`.github/renovate.json`).
* Gradle wrapper.
* `.gitignore`.
* `ISSUE_TEMPLATE.md`, `LICENSE`, `MAINTAINING.md`, `config/HEADER` and `config/spotless.license.java`.
* Checkstyle's `config/checkstyle/checkstyle.xml` and `config/checkstyle/suppressions.xml`.

Regarding the Gradle wrapper, the template repo checks every week if there is a new Gradle version. If there is, it will
upgrade the wrapper in the template repo itself, and via the files sync workflow this gets propagated to all repos. This
way we make sure we stay up-to-date regarding Gradle versions in all repos.

##### Customised workflow files

The files sync workflow copies the workflow files listed above verbatim, so it is not possible to have custom steps in
them and still be part of the sync process: any modification to those files will be overwritten the next time the files
sync workflow is executed.

The "Java CI" (`gradle.yml`) workflow does have the ability to have an optional setup step, though. If there is a `setup.sh`
file in the project root, it will be executed before invoking Gradle.

There are projects, such as micronaut-gcp and micronaut-kubernetes, that have made customizations to sync'ed workflows
because it's absolutely necessary. In those projects, the sync pull requests are manually merged so that the customizations
aren't lost. When merging them, keep `gradle.yml` and `graalvm-latest.yml` in step: Java CI calls GraalVM Latest CI as a
reusable workflow, so taking only one of the two either stops the native tests or breaks Java CI.

Note that it is perfectly possible to have new workflows that aren't part of the sync process.

## Releases

Modules on a maintenance branch tracked by [Micronaut Platform](https://github.com/micronaut-projects/micronaut-platform)
are released together with the platform by the [release train](#release-train). Modules outside the train, and urgent
fixes that cannot wait for the next platform release, are released on their own with a
[standalone release](#standalone-releases).

### Release train

The release train is driven from micronaut-platform. A platform release decides which tracked modules changed since
their last release and releases only those, together with the platform BOM.

The real run (staging, verifying and publishing from micronaut-platform) is not enabled yet. Until it is, use the dry
run to prepare a platform release and release modules with the [standalone process](#standalone-releases).

#### What gets released

The train tracks the branches listed in micronaut-platform's `gradle/release-train.toml`. By default a module tracks the
`<major>.<minor>.x` branch of its version in the platform catalog: `managed-micronaut-data = "5.2.2"` tracks
`micronaut-data` on `5.2.x`. For each tracked branch, the train compares the branch with the module's last release tag:

* Changes to published sources (`src/main`, except `src/main/docs`): the module is released.
* Changes to `gradle/libs.versions.toml` only: the module is released only if the dependencies in its published POMs
  changed. Upgrades of test or build-only dependencies do not trigger a release.
* Build, test, docs, workflow or Gradle wrapper changes only: the module is not released. They go out with the next
  change that is released.
* Commits by `micronaut-build`, such as release commits and snapshot bumps, are ignored.

A standalone release made since the last platform release is picked up: the next platform release includes that version
without releasing the module again.

Some modules stay out of the train because their releases use a large share of the namespace's monthly
[Maven Central publishing limits](https://central.sonatype.org/publish/maven-central-publishing-limits/). Micronaut Oracle
Cloud is one: each release publishes 190 artifacts, about 268 MB and 9,480 files. Such a module is listed in
`gradle/release-train.toml` with `exclude = "<reason>"`. It is never released by the train and keeps the standalone
process, and the platform still picks up its releases. The dry run shows each train's size, file count and releases as a
share of the monthly limits; check the namespace's current usage on the Central Portal before a real run.

The platform itself is released when any module is released, or when a managed version in its catalog changed.

#### Choosing the version

The version always comes from `projectVersion` on the tracked branch: `5.2.3-SNAPSHOT` is released as `5.2.3`.
Post-release moves the branch to the next patch snapshot, so patch releases need no action.

For a new minor or major version, either:

* bump `projectVersion` on the tracked branch to the new snapshot, for example `5.3.0-SNAPSHOT`, or
* create the new branch, for example `5.3.x`, and set `branch = "5.3.x"` for the module in micronaut-platform's
  `gradle/release-train.toml`.

#### Release notes

The automatic draft release of each module remains the source of its release notes. The dry run creates or updates the
draft for the version being released (tag `v<version>`), and adds a release train section to the platform's draft
release, listing the modules and the third-party dependency upgrades. Review and edit the module drafts before the real
run: they are published as written.

#### Dry run

In micronaut-platform, run the **Release Train** workflow from the Actions tab on the platform branch being released,
for example `5.2.x`. Without the `write-drafts` option the run only reports; with it, the run also creates or updates
the draft releases. Writing the module drafts needs the release train GitHub App, configured in micronaut-platform with
the `RELEASE_TRAIN_CLIENT_ID` variable and the `RELEASE_TRAIN_PRIVATE_KEY` secret.

The dry run never tags, signs or publishes anything. It:

* Plans the train: for each tracked module, the last release, the next version, the commits since the last release
  and whether it is released.
* Rehearses each release in a `release check (<module> <version>)` job that runs what the module's release workflow
  runs: the vulnerability audit (blocking for GA versions, advisory for milestones and release candidates), the release
  bundle at the release version without signing (`./gradlew preReleaseCheck prepareBundle`), the docs, and the
  published dependency comparison when only the catalog changed. A failed job is a release that would fail.
* Summarises the run: a release readiness verdict, a table of each check per module, the vulnerability findings, and
  the CI status of each branch head. A failing branch CI is shown as a warning, because the release workflow does not
  run it.

Fix every failure on the module branch, for example by upgrading a vulnerable dependency, and run the dry run again
until every release check passes.

#### Staged module releases

In the real run, micronaut-platform dispatches each module's
[release workflow](https://github.com/micronaut-projects/micronaut-project-template/blob/master/.github/workflows/release.yml)
twice with `workflow_dispatch`. The inputs are:

* `mode`: `stage` or `finalize`.
* `version`: the version to release, without the `v` prefix, for example `5.2.3`.
* `branch`: the branch to release from, for example `5.2.x`.
* `train-id`: identifies the train; runs are named `Release train <train-id> <mode> v<version>`.
* `stage-run-id`: `finalize` only, the run of the `stage` step to take the artifacts and provenance from.

`stage` runs the vulnerability audit, commits the release version and pushes the `v<version>` tag, and publishes to
the Central Portal with `--publishing-type=USER_MANAGED`. The deployment is validated but not public. The run records
the deployment id as the `central-deployment` artifact and generates the SLSA provenance.

The platform then checks its BOM against the staged artifacts and publishes every deployment of the train, or drops
them all if anything failed.

`finalize` publishes the GitHub release from its draft with the build artifacts and provenance, publishes the docs to
the `gh-pages` branch, and runs post-release: the next snapshot version and the milestones. If `finalize` fails, run it
again with the same inputs. The `stage` artifacts are kept for 5 days, so `finalize` must run within that time.

Do not start these modes by hand outside a train: a staged deployment that the platform does not publish is never
released. Staging needs a version of micronaut-build that writes the Central Portal deployment id
([micronaut-build#963](https://github.com/micronaut-projects/micronaut-build/pull/963)).

### Standalone releases

The release process is highly automated and normally involves just publishing a GitHub release. But before you get there,
there are some parts you need to understand first.

First of all, all the repos have an automatic changelog generation mechanism: when a change is made to the repo
(a push event), it creates (or updates if there is already one) a draft release, calculating the next patch version. The
release notes will contain pull requests merged and issues closed since the last release.

When the module is ready for a new release, check the generated release notes, and make changes if needed (for example,
you can add an introduction paragraph highlighting some items included in the release). If the version you are going to
publish is not a new patch version, but a new minor or major, update the release notes text to reflect the new version.
If you are publishing a milestone or release candidate, check the pre-release checkbox.

Note that the release tags must be preceded with `v`, e.g.: `v1.2.3`.

The release workflow runs a vulnerability audit before publishing. Vulnerability findings are advisory for milestone and
release-candidate tags such as `v1.2.3-M1` and `v1.2.3-RC1`, but they remain blocking for GA releases.

Once you publish the GitHub release, the
[Release GitHub Action workflow](https://github.com/micronaut-projects/micronaut-project-template/blob/master/.github/workflows/release.yml)
will kick off, performing the following steps:

* Pre-release: sets the `projectVersion` property in `gradle.properties` to the release version, and commit and pushes
  the result.
* Generates documentation guide and publishes it to the `gh-pages` branch.
* Sends a pull request to Core to update the BOM.
* Post-release:
  * Determines the next patch version, and sets it as a `SNAPSHOT` version.
  * Closes the milestone that matches the release version, and creates a new one for the next patch.

If everything goes well, you now need to manually trigger the Maven Central publishing workflow via the GitHub UI.

If there is an issue with the release, it's important not to trigger the Maven Central publishing workflow because once
we publish a version to Maven Central we cannot change or remove it anymore.

There are some properties in `gradle.properties` that affect the release process:

* `githubBranch`: the current branch, usually `master`.
* `githubCoreBranch`: the Micronaut Core branch where the BOM update pull requests will be sent to.
* `bomProperty`: in Micronaut Core's `gradle.properties`, the property that represents this module's version.
* `bomProperties`: if needed, additional properties for the BOM pull request.

For example, assuming a module has the release `1.0.0` as the latest version published, which was included in the
Micronaut `2.2.0` BOM. If the next version you want to publish is:

* A new patch release (`1.0.1`): simply publish the existing draft release.
* A new minor release (`1.1.0`):
  * Before the release, push a `1.0.x` branch off `master`.
  * Bump the version in master to `1.1.0-SNAPSHOT`.
  * Set the `githubCoreBranch` property to `2.3.x` (or `3.0.x` if it will be the next one).
  * Edit the draft release setting the version to `1.1.0` in the release title, body, tag, etc.
  * Publish the release.
* A new major release (`2.0.0`):
  * Before the release, push a `1.0.x` branch off `master`.
  * Bump the version in master to `2.0.0-SNAPSHOT`.
  * Set the `githubCoreBranch` property to `3.0.x` (or `2.3.x` if this new major version doesn't introduce breaking changes).
  * Edit the draft release setting the version to `2.0.0` in the release title, body, tag, etc.
  * Publish the release.
