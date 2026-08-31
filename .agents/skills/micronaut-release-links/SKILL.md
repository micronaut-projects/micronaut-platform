---
name: micronaut-release-links
description: Generate a Markdown list of GitHub release links from Micronaut module/version lists, including human-readable entries such as "micronaut core 5.1.13" and Gradle catalog entries such as "managed-micronaut-openapi = \"7.1.3\"". Use when asked to turn a Micronaut dependency or release list into linked Markdown.
license: MIT
compatibility: Compatible with Agent Skills spec and skill directories such as .agents/skills or equivalent agent-specific skill paths.
metadata:
  author: local
  version: "1.1.0"
---

# Micronaut Release Links

Generate one Markdown bullet per supplied Micronaut module version. Each bullet must link directly to the matching GitHub release tag.

## Procedure

1. Read every non-empty input line. Accept either of these forms:
   - Human-readable: `micronaut <module> <version>`.
   - Gradle catalog: `managed-micronaut-<module> = "<version>"`.
2. Normalize a catalog entry by removing the `managed-` prefix, changing its first hyphen after `micronaut` to a space, and removing `=`, quotation marks, and surrounding whitespace. For example, `managed-micronaut-openapi = "7.1.3"` becomes `micronaut openapi 7.1.3`.
3. Derive the GitHub repository as `micronaut-projects/micronaut-<module>`. Keep hyphenated module names intact: `micronaut-opensearch` maps to `micronaut-projects/micronaut-opensearch`.
4. Emit `- [<normalized label>](https://github.com/micronaut-projects/<repository>/releases/tag/v<version>)` for each line, preserving the input order.
5. Return the complete generated list in exactly one fenced `markdown` code block. Do this even though the content is Markdown, so the user can copy the source without the chat rendering its links. Do not add text outside the code block unless the user explicitly asks for commentary.

## Requirements and edge cases

- Use the release tag `v<version>`.
- Do not use a release-search page, a repository home page, or a dependency-management URL.
- Preserve semantic-version qualifiers exactly. For example, `5.1.0-RC1` uses the tag `v5.1.0-RC1`.
- Treat blank lines as separators and omit them from the result.
- Always fence the result with ```` ```markdown ```` and ```` ``` ````; do not render the generated links directly in the chat response.
- If a line does not identify a `micronaut` module and a version, ask for the intended repository rather than inventing a link.
- If the source name is an alias that does not correspond to `micronaut-<module>`, ask the user for the GitHub repository before generating its link.

## Examples

Input:

```text
micronaut core 5.1.13
managed-micronaut-openapi = "7.1.3"
micronaut email 3.2.0
```

Output:

```markdown
- [micronaut core 5.1.13](https://github.com/micronaut-projects/micronaut-core/releases/tag/v5.1.13)
- [micronaut openapi 7.1.3](https://github.com/micronaut-projects/micronaut-openapi/releases/tag/v7.1.3)
- [micronaut email 3.2.0](https://github.com/micronaut-projects/micronaut-email/releases/tag/v3.2.0)
```

## Validation checklist

- Verify there is one bullet for each non-empty input line.
- Verify the displayed label contains the normalized module name and exact version.
- Verify every URL uses `https://github.com/micronaut-projects/micronaut-<module>/releases/tag/v<version>`.
