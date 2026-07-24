---
name: micronaut-minor-release-notes
description: Create Markdown release notes for a Micronaut Framework minor release by comparing managed module versions in gradle/libs.versions.toml across x-patch maintenance branches, synthesizing intervening GitHub module releases, and linking included features to versioned module documentation. Use when asked to draft, research, summarize, or prepare Micronaut Framework minor-release notes, module changes, or dependency-upgrade notes.
license: MIT
compatibility: Requires Git access to the Micronaut Platform repository and network access to GitHub releases.
metadata:
  author: micronaut-projects
  version: "1.0.0"
---

# Micronaut Framework Minor Release Notes

Produce accurate, reader-focused notes for the modules shipped in a Micronaut Framework minor release. Compare the selected minor branch with its immediately preceding minor branch, then summarize the GitHub releases between their managed versions.

## Inputs and result

Require a target maintenance branch named `<major>.<minor>.x`, such as `5.1.x`. If none is supplied, use the checked-out branch only when it follows this naming convention. The previous branch is `<major>.<minor - 1>.x`.

Inspect every `managed-micronaut-*` entry in `gradle/libs.versions.toml`, not only modules with obvious feature notes. Always begin the output with Micronaut Core, using `managed-micronaut-core`, then audit every remaining entry and emit a module section only when one or more consumer-facing changes remain after filtering. Omit non-Core modules whose qualifying releases contain only CI/tooling, build-plugin, GitHub Action, documentation-only, or Micronaut dependency and BOM alignment updates. Write the complete release note as literal Markdown source to `build/release-notes.md` and deliver the same source inside one fenced `markdown` code block. This preserves visible `[link text](URL)` syntax instead of allowing the host application to render it. Group non-Core modules under the category headings defined by the Micronaut documentation index. Each short paragraph identifies the version transition and highlights substantive new features, user-visible improvements, and notable non-Micronaut runtime or transitive dependency updates. Link each feature or improvement to its relevant section in that module's documentation for the shipped version when one exists. Render documentation links with Markdown link syntax (`[link text](URL)`), never as bare or parenthesized URLs. Use GitHub release pages as research inputs and as the required links for versions rendered in module headings; do not include additional release-source links or a release-source section in the output.

## Procedure

1. Confirm the target branch is a semantic-version maintenance branch. Stop and ask for the intended comparison if it is not.
2. Retrieve and parse the current [`modules.yml`](https://github.com/micronaut-projects/micronaut-docs-index/blob/main/modules.yml) from the Micronaut documentation index. Map each requested repository slug, such as `micronaut-data`, to its category title and module title. Skip the `Most Popular` category: it is a duplicate navigation grouping, not a release-note section. If a module occurs in more than one remaining category, use its first occurrence in `modules.yml` so it is written once; do not duplicate the module unless the user asks.
3. Read `gradle/libs.versions.toml` from both branches using `git show <branch>:gradle/libs.versions.toml`. Read `managed-micronaut-core` first and record its target and baseline versions. Then enumerate every key beginning `managed-micronaut-`; do not filter the list before research or drafting. Locate the same key on the previous branch. The target value is the shipped version; the previous-branch value is the baseline.
4. Normalize versions for semantic comparison. For every entry whose target differs from its baseline, enumerate every published, non-draft GitHub release whose tag is strictly greater than the baseline and less than or equal to the target. Record the qualifying tags in ascending order before reading any release body. Do not infer changes from release dates or use a lexical string comparison. Record unchanged entries as audited with no release research required; do not emit them.
5. Build a private release-audit matrix before drafting. For every changed module and every qualifying tag, record: tag, whether its release body was retrieved, every feature/improvement/user-relevant bug-fix item, and every dependency update with its artifact, old version when available, and new version. Parse and classify *every* dependency-update bullet before drafting; do not use the bullet's section heading, `managed.` prefix, or the absence of a feature section as a shortcut. Classify each dependency update as consumer-facing runtime/transitive, build/test/CI-only, or Micronaut ecosystem alignment; record the inclusion or omission decision with a reason. A failed or missing release-body retrieval is an incomplete audit, not evidence that the module has no highlights; report the gap instead of emitting a generic summary.
6. Read every release body in the audit matrix before drafting. Retain every consumer-facing non-Micronaut runtime or transitive dependency upgrade unless it is genuinely redundant. This includes direct dependencies and updates expressed as `managed.<dependency>`: classify the underlying library, not the catalog-property name. Never omit an update merely because it appears in a dependency-update section, the release contains no feature bullets, or the dependency is absent from the dependency link registry. Before describing a module as alignment-only, explicitly review and classify every dependency-update entry and confirm that all are build/test/CI-only or Micronaut ecosystem alignment. When the same dependency is updated repeatedly, report only the final shipped version; do not enumerate intermediate updates unless the user explicitly requests a progression. For example, write “updates AWS SDK v2 to 2.48.3”, not its intermediate patch history. Likewise, an Elasticsearch module update from `managed.elasticsearch` to 9.4.1 and then 9.4.3 must report the Elasticsearch Java Client's final 9.4.3 version; an OpenSearch release updating `opensearch-java` to 3.9.0 must report that runtime client update. Omit only build/test/CI-only updates and Micronaut ecosystem alignment, including Micronaut Core, Serde, and Logging BOM alignment.
7. Before writing a dependency upgrade, read [the dependency link registry](references/dependency-links.md). When it contains the dependency, render its name as the prescribed Markdown link. Do not add a bare or parenthesized URL.
8. For Core, open the versioned guide at `https://docs.micronaut.io/<target>/guide/`; for other modules, open `https://micronaut-projects.github.io/micronaut-<module>/<target>/guide/`. Search headings and anchors for every selected feature or improvement. Link the feature name directly to the most specific matching guide anchor, for example `[OWASP HTML Sanitizer module](https://micronaut-projects.github.io/micronaut-security/5.3.1/guide/#htmlSanitizer). Do not invent anchors; omit the documentation link if no relevant section exists or the guide is unavailable.
9. Write Micronaut Core first as `## Micronaut Core ([<baseline>](https://github.com/micronaut-projects/micronaut-core/releases/v<baseline>) → [<target>](https://github.com/micronaut-projects/micronaut-core/releases/v<target>))`, followed by its retained Core changes. Then write one compact paragraph per included non-Core module, grouped under `## <Category title>` and labelled `### <Module title> ([<baseline>](https://github.com/micronaut-projects/micronaut-<module>/releases/v<baseline>) → [<target>](https://github.com/micronaut-projects/micronaut-<module>/releases/v<target>))`. Both versions in every heading must be Markdown links to their GitHub release pages, using the repository slug established by the documentation index; verify the release-page URL rather than assuming the catalog key is the repository slug. Follow the version range with every consumer-facing item retained by the audit matrix, including each retained non-Micronaut runtime/transitive dependency's final shipped version. Omit a non-Core module entirely when its complete audit has no retained item; do not emit a generic version-transition-only paragraph. Place each documentation or registered dependency URL behind its descriptive Markdown link text; never append a raw URL in parentheses. Use `## Other` for included catalog entries absent from `modules.yml`. Write the complete categorized release note, without the enclosing response fence, to `build/release-notes.md`. Then return that same content inside one fenced `markdown` code block and tell the user where to find the generated file.
10. Validate that the first output section is Micronaut Core and that its baseline and target equal the two `managed-micronaut-core` values. Validate that every `managed-micronaut-*` entry on the target branch has an audit result, even though only modules with retained consumer-facing items appear after Core. For every changed module, validate that the audit matrix contains every semantic-version-qualified tag and a retrieval status for each. Reconcile every consumer-facing non-Micronaut dependency update in the audit matrix to an output statement; reject the draft if its final shipped version was silently dropped. Reject any included non-Core module whose content consists only of CI/tooling, build-plugin, GitHub Action, documentation-only, or Micronaut dependency/BOM alignment updates. Also validate that the first and last releases bracket each changed version range, categories and module titles match `modules.yml`, every documentation link uses the target version and resolves to a relevant heading, and each mentioned registered dependency uses its prescribed Markdown link and version rule. Verify that `build/release-notes.md` exists and contains the complete release note before responding. Do not emit the private audit matrix used for this validation.

## GitHub release lookup

Use this API shape and filter it with a semantic-version-aware tool or logic:

```text
https://api.github.com/repos/micronaut-projects/micronaut-<module>/releases?per_page=100
```

Tags are normally `v<version>`. Verify the actual tag instead of assuming it. Paginate when the API response does not contain the baseline-to-target range.

## Output template

````markdown
```markdown
## Micronaut Core ([<core baseline>](https://github.com/micronaut-projects/micronaut-core/releases/v<core baseline>) → [<core target>](https://github.com/micronaut-projects/micronaut-core/releases/v<core target>))

<Core changes.>

## <Category title>

### <Module title> ([<baseline>](https://github.com/micronaut-projects/micronaut-<module>/releases/v<baseline>) → [<target>](https://github.com/micronaut-projects/micronaut-<module>/releases/v<target>))

<One concise paragraph, with links only to relevant target-version documentation sections.>

Data adds [SQLite dialect support](https://micronaut-projects.github.io/micronaut-data/5.1.0/guide/#_setting_the_dialect) and [value-based ETags for optimistic locking](https://micronaut-projects.github.io/micronaut-data/5.1.0/guide/#optimisticLocking).
```
````

## Examples

Triggering requests:

- “Draft the Micronaut Framework 5.1 release notes from the 5.1.x branch.”
- “What changed in Micronaut Security between the versions managed by 5.0.x and 5.1.x?”
- “Summarize Security and Data for the next Framework minor release and link users to their guides.”

Do not use this skill for:

- “Write release notes for Micronaut Security 5.3.1 only.”
- “Update a managed version in the platform catalog.”
- “Find the latest documentation for every Micronaut project.”

## Checklist

- The branches, catalog keys, and exact baseline/target values are recorded; each version rendered in a module heading links to its verified GitHub release page.
- Every `managed-micronaut-*` key on the target branch has an audit result.
- Release selection uses semantic versions and includes all qualifying final releases.
- Every changed module has a complete per-release audit matrix before drafting.
- Modules with no retained consumer-facing content are omitted from the output.
- The summary favors features, improvements, and consumer-relevant non-Micronaut dependency changes.
- Every consumer-facing non-Micronaut dependency upgrade, including direct and `managed.<dependency>` updates, is reconciled to the output with its final shipped version; no module is called alignment-only until every dependency-update entry has been classified.
- Micronaut dependency updates and Micronaut ecosystem BOM alignment are omitted.
- GitHub release pages are used for research and as the required version links in module headings; no additional release-source links or release-source section is included.
- Documented features and improvements link to the matching section of the target-version guide; links are omitted rather than guessed.
- Documentation links use `[descriptive text](URL)` syntax; no bare or parenthesized documentation URLs appear in the prose.
- Mentioned dependencies in the registry use their prescribed Markdown links.
- The complete release note is enclosed in one `markdown` code block, preserving visible Markdown link syntax.
- The identical release-note source, without the response fence, is written to `build/release-notes.md`, and the final response tells the user where to find it.
- Modules are grouped once under their `modules.yml` category; the `Most Popular` category is skipped.
- Micronaut Core, using `managed-micronaut-core`, is always the first output section.
