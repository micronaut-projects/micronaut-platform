#!/usr/bin/env python3
"""Plan a Micronaut release train and draft its release notes.

Subcommands:
  plan      Compare each module's tracked branch with its last release and decide what to release.
  pom-diff  Compare the published POMs of a module at two revisions.
  drafts    Fold the dependency checks into the plan, write the summary and the draft releases.
"""

import argparse
import base64
import json
import os
import re
import subprocess
import sys
import time
import tomllib
import urllib.error
import urllib.request
import xml.etree.ElementTree as element_tree
from pathlib import Path


API_URL = os.environ.get("GITHUB_API_URL", "https://api.github.com")
ORG = "micronaut-projects"
BOT_LOGINS = {"micronaut-build", "github-actions[bot]"}
BOT_MESSAGE_PREFIXES = ("[skip ci]", "chore: Bump version to")
RELEASE_COMMIT = re.compile(r"^\[skip ci\] Release v(\S+)")
MAVEN_NAMESPACE = "{http://maven.apache.org/POM/4.0.0}"
NON_PUBLISHED_ROOTS = ("test-", "tests", "doc-examples", "examples", "benchmarks")
BUILD_LOGIC_ROOTS = ("buildSrc/", "build-logic/", ".github/", "gradle/", "config/")
TRAIN_START = "<!-- release-train:start -->"
TRAIN_END = "<!-- release-train:end -->"

RELEASE = "release"
BUMP = "bump"
CHECK_DEPENDENCIES = "check-dependencies"
SKIP = "skip"
UNCHANGED = "unchanged"


class GitHub:
    def __init__(self, token):
        self.token = token

    def request(self, path, method="GET", body=None):
        url = path if path.startswith("http") else f"{API_URL}{path}"
        data = json.dumps(body).encode() if body is not None else None
        headers = {
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "micronaut-release-train",
        }
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        for attempt in range(4):
            request = urllib.request.Request(url, data=data, headers=headers, method=method)
            try:
                with urllib.request.urlopen(request, timeout=60) as response:
                    payload = response.read()
                    return json.loads(payload) if payload else None
            except urllib.error.HTTPError as error:
                if error.code == 404:
                    return None
                if error.code >= 500 and attempt < 3:
                    time.sleep(2 ** attempt)
                    continue
                raise RuntimeError(f"{method} {url} failed: {error.code} {error.read().decode()}") from error
            except (urllib.error.URLError, ConnectionError, TimeoutError):
                if attempt == 3:
                    raise
                time.sleep(2 ** attempt)
        return None


def read_properties(text):
    properties = {}
    for line in text.splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            properties[key.strip()] = value.strip()
    return properties


def default_branch(version):
    parts = version.split(".")
    if len(parts) < 2:
        raise ValueError(f"Cannot derive a branch from version {version}")
    return f"{parts[0]}.{parts[1]}.x"


def train_modules(manifest, versions):
    modules = []
    for key, settings in manifest.get("modules", {}).items():
        if key not in versions:
            raise ValueError(f"{key} is in the release train but not in gradle/libs.versions.toml")
        current = versions[key]
        modules.append({
            "key": key,
            "repo": settings.get("repo", f"{ORG}/{key.removeprefix('managed-')}"),
            "branch": settings.get("branch", default_branch(current)),
            "current": current,
        })
    return modules


def is_bot_commit(commit):
    login = (commit.get("author") or {}).get("login", "")
    message = commit["commit"]["message"]
    return login in BOT_LOGINS or message.startswith(BOT_MESSAGE_PREFIXES)


def only_project_version_changed(patch):
    changed = [
        line[1:].strip() for line in patch.splitlines()
        if line[:1] in "+-" and not line.startswith(("+++", "---"))
    ]
    return bool(changed) and all(not line or line.startswith("projectVersion") for line in changed)


def classify_file(file):
    """Return source, catalog, version, build, test or docs for one changed file."""
    path = file["filename"]
    if path == "gradle/libs.versions.toml":
        return "catalog"
    if path == "gradle.properties":
        patch = file.get("patch")
        return "version" if patch and only_project_version_changed(patch) else "build"
    if path.startswith(BUILD_LOGIC_ROOTS):
        return "build"
    if "src/main/docs/" in path:
        return "docs"
    if path.startswith(NON_PUBLISHED_ROOTS):
        return "test"
    if path.startswith("src/main/") or "/src/main/" in path:
        return "source"
    if path.startswith("src/") or "/src/" in path:
        return "test"
    return "build"


def decide(changes, files, released_since, truncated):
    """Decide what the train does with a module, returning (decision, reasons)."""
    categories = {category for category, paths in files.items() if paths}
    if truncated:
        return RELEASE, ["comparison too large to classify; releasing to be safe"]
    if "source" in categories:
        return RELEASE, ["published sources changed"]
    if "catalog" in categories:
        return CHECK_DEPENDENCIES, ["dependency catalog changed; comparing published POMs"]
    if released_since:
        reason = f"v{released_since} was released on its own and is not in the platform yet"
        return BUMP, [reason] + (["build, test or docs changes since are not released"] if changes else [])
    if changes:
        return SKIP, ["only build, test or docs changes"]
    return UNCHANGED, ["no changes since the last release"]


def plan_module(github, module):
    repo, branch = module["repo"], module["branch"]
    base = f"v{module['current']}"
    if github.request(f"/repos/{repo}/git/ref/tags/{base}") is None:
        raise RuntimeError(f"{repo}: tag {base} not found")
    properties_file = github.request(f"/repos/{repo}/contents/gradle.properties?ref={branch}")
    if properties_file is None:
        raise RuntimeError(f"{repo}: branch {branch} not found")
    properties = read_properties(base64.b64decode(properties_file["content"]).decode())
    project_version = properties.get("projectVersion", "")

    comparison = github.request(f"/repos/{repo}/compare/{base}...{branch}")
    commits = comparison["commits"]
    released_since = None
    for index, commit in enumerate(commits):
        match = RELEASE_COMMIT.match(commit["commit"]["message"])
        if match:
            released_since = match.group(1)
            base = commit["sha"]
            commits = commits[index + 1:]
    if released_since:
        comparison = github.request(f"/repos/{repo}/compare/{base}...{branch}")

    truncated = comparison["total_commits"] > len(comparison["commits"]) or len(comparison.get("files", [])) >= 300
    changes = [
        {
            "sha": commit["sha"][:10],
            "message": commit["commit"]["message"].splitlines()[0],
            "author": (commit.get("author") or {}).get("login") or commit["commit"]["author"]["name"],
            "url": commit["html_url"],
        }
        for commit in commits if not is_bot_commit(commit)
    ]
    files = {category: [] for category in ("source", "catalog", "version", "build", "test", "docs")}
    for file in comparison.get("files", []):
        files[classify_file(file)].append(file["filename"])
    if not changes:
        files = {category: [] for category in files}
    decision, reasons = decide(changes, files, released_since, truncated)

    return {
        **module,
        "title": properties.get("title", repo.split("/")[1]),
        "lastRelease": released_since or module["current"],
        "base": base,
        "head": comparison["commits"][-1]["sha"] if comparison["commits"] else base,
        "nextVersion": project_version.removesuffix("-SNAPSHOT"),
        "projectVersion": project_version,
        "decision": decision,
        "reasons": reasons,
        "changes": changes,
        "files": files,
        "compareUrl": comparison["html_url"],
    }


def git(*args):
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout.strip()


def catalog_changes(previous_text, current_versions):
    previous = tomllib.loads(previous_text).get("versions", {}) if previous_text else {}
    changes = []
    for key in sorted(set(previous) | set(current_versions)):
        if not key.startswith(("managed-", "parent-")):
            continue
        before, after = previous.get(key), current_versions.get(key)
        if before != after:
            changes.append({"key": key, "from": before, "to": after})
    return changes


def plan_platform(versions, modules):
    properties = read_properties(Path("gradle.properties").read_text())
    try:
        previous_tag = git("describe", "--tags", "--abbrev=0", "--match", "v*")
        previous_catalog = git("show", f"{previous_tag}:gradle/libs.versions.toml")
    except subprocess.CalledProcessError:
        previous_tag, previous_catalog = None, None
    changes = catalog_changes(previous_catalog, versions)
    module_keys = {module["key"] for module in modules}
    third_party = [change for change in changes if change["key"] not in module_keys]
    return {
        "title": properties.get("title", "Micronaut Platform"),
        "nextVersion": properties["projectVersion"].removesuffix("-SNAPSHOT"),
        "previousTag": previous_tag,
        "catalogChanges": changes,
        "thirdPartyChanges": third_party,
    }


def platform_releases(plan):
    moving = [module for module in plan["modules"] if module["decision"] in (RELEASE, BUMP)]
    return bool(moving or plan["platform"]["catalogChanges"])


def write_output(name, value):
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a") as file:
            file.write(f"{name}={value}\n")


def command_plan(arguments):
    versions = tomllib.loads(Path(arguments.catalog).read_text())["versions"]
    manifest = tomllib.loads(Path(arguments.manifest).read_text())
    github = GitHub(os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN"))
    modules = [plan_module(github, module) for module in train_modules(manifest, versions)]
    plan = {"platform": plan_platform(versions, modules), "modules": modules}
    plan["platform"]["release"] = platform_releases(plan)
    Path(arguments.output).parent.mkdir(parents=True, exist_ok=True)
    Path(arguments.output).write_text(json.dumps(plan, indent=2))

    checks = [
        {"key": module["key"], "repo": module["repo"], "base": module["base"], "head": module["head"]}
        for module in modules if module["decision"] == CHECK_DEPENDENCIES
    ]
    write_output("dependency-checks", json.dumps(checks))
    write_output("has-dependency-checks", str(bool(checks)).lower())
    for module in modules:
        print(f"{module['key']}: {module['decision']} ({'; '.join(module['reasons'])})")


def pom_dependencies(pom_path):
    root = element_tree.parse(pom_path).getroot()

    def text(element, name):
        child = element.find(f"{MAVEN_NAMESPACE}{name}")
        return child.text.strip() if child is not None and child.text else ""

    artifact = f"{text(root, 'groupId')}:{text(root, 'artifactId')}"
    own_version = text(root, "version")
    dependencies = set()
    for section in ("dependencyManagement/{0}dependencies/{0}dependency", "dependencies/{0}dependency"):
        for dependency in root.iterfind(f"{MAVEN_NAMESPACE}" + section.format(MAVEN_NAMESPACE)):
            scope = text(dependency, "scope") or "compile"
            if scope == "test":
                continue
            # The module's own artifacts move from the last release to the next snapshot: not a change.
            version = text(dependency, "version")
            version = "${project.version}" if version == own_version else version
            dependencies.add(
                f"{text(dependency, 'groupId')}:{text(dependency, 'artifactId')}:{version}"
                f" ({scope}{', optional' if text(dependency, 'optional') == 'true' else ''})"
            )
    return artifact, dependencies


def published_dependencies(directory):
    artifacts = {}
    for pom in Path(directory).glob("**/build/publications/maven/pom-default.xml"):
        artifact, dependencies = pom_dependencies(pom)
        artifacts[artifact] = dependencies
    return artifacts


def compare_published(before, after):
    differences = []
    for artifact in sorted(set(before) | set(after)):
        if artifact not in before:
            differences.append(f"{artifact}: new artifact")
        elif artifact not in after:
            differences.append(f"{artifact}: no longer published")
        else:
            differences += [f"{artifact}: - {entry}" for entry in sorted(before[artifact] - after[artifact])]
            differences += [f"{artifact}: + {entry}" for entry in sorted(after[artifact] - before[artifact])]
    return differences


def command_pom_diff(arguments):
    before = published_dependencies(arguments.base_dir)
    after = published_dependencies(arguments.head_dir)
    if not before or not after:
        raise SystemExit(f"No published POMs found under {arguments.base_dir} or {arguments.head_dir}")
    differences = compare_published(before, after)
    result = {"key": arguments.key, "changed": bool(differences), "differences": differences}
    Path(arguments.output).write_text(json.dumps(result, indent=2))
    print(json.dumps(result, indent=2))


def apply_dependency_checks(plan, results_dir):
    results = {}
    for path in Path(results_dir).glob("**/*.json") if results_dir else []:
        result = json.loads(path.read_text())
        results[result["key"]] = result
    for module in plan["modules"]:
        if module["decision"] != CHECK_DEPENDENCIES:
            continue
        result = results.get(module["key"])
        if result is None:
            module["decision"] = RELEASE
            module["reasons"] = ["dependency check did not complete; releasing to be safe, review before the real run"]
        elif result["changed"]:
            module["decision"] = RELEASE
            module["reasons"] = ["published dependencies changed"]
            module["dependencyChanges"] = result["differences"]
        elif module["lastRelease"] != module["current"]:
            module["decision"] = BUMP
            module["reasons"] = [f"v{module['lastRelease']} was released on its own; only build or test dependencies changed since"]
        else:
            module["decision"] = SKIP
            module["reasons"] = ["only build or test dependencies changed"]
    plan["platform"]["release"] = platform_releases(plan)
    return plan


def module_notes(module):
    lines = [f"- {change['message']} ({change['author']})" for change in module["changes"]]
    return "\n".join(lines) or "- No changes"


def module_draft(github, module):
    """Find the module's draft release for the next version, creating one when missing."""
    tag = f"v{module['nextVersion']}"
    releases = github.request(f"/repos/{module['repo']}/releases?per_page=30") or []
    for release in releases:
        if release["draft"] and release["tag_name"] == tag:
            return release, "existing"
    for release in releases:
        if release["draft"] and release["target_commitish"] == module["branch"]:
            return release, f"existing draft is tagged {release['tag_name']}, expected {tag}"
    if not github.token:
        return None, "no token to create the draft"
    notes = github.request(f"/repos/{module['repo']}/releases/generate-notes", "POST", {
        "tag_name": tag,
        "target_commitish": module["branch"],
        "previous_tag_name": f"v{module['lastRelease']}",
    })
    release = github.request(f"/repos/{module['repo']}/releases", "POST", {
        "tag_name": tag,
        "target_commitish": module["branch"],
        "name": f"{module['title']} {module['nextVersion']}",
        "body": notes["body"],
        "draft": True,
    })
    return release, "created"


def train_section(plan):
    modules = plan["modules"]
    released = [module for module in modules if module["decision"] == RELEASE]
    bumped = [module for module in modules if module["decision"] == BUMP]
    held = [module for module in modules if module["decision"] in (SKIP, UNCHANGED)]
    lines = [TRAIN_START, "## Release train", ""]
    if released or bumped:
        lines += ["| Module | Version | Why |", "| --- | --- | --- |"]
        for module in released:
            notes = f" ([notes]({module['draftUrl']}))" if module.get("draftUrl") else ""
            lines.append(
                f"| {module['title']} | {module['current']} → {module['nextVersion']} |"
                f" {'; '.join(module['reasons'])}, [{len(module['changes'])} commits]({module['compareUrl']}){notes} |"
            )
        for module in bumped:
            lines.append(f"| {module['title']} | {module['current']} → {module['lastRelease']} | {'; '.join(module['reasons'])} |")
        lines.append("")
    if held:
        lines.append("Not released: " + ", ".join(
            f"{module['title']} ({'; '.join(module['reasons'])})" for module in held
        ))
        lines.append("")
    third_party = plan["platform"]["thirdPartyChanges"]
    if third_party:
        lines += ["### Dependency upgrades", ""]
        lines += [f"- `{change['key']}` {change['from'] or 'new'} → {change['to'] or 'removed'}" for change in third_party]
        lines.append("")
    lines.append(TRAIN_END)
    return "\n".join(lines)


def merge_section(body, section):
    body = body or ""
    if TRAIN_START in body and TRAIN_END in body:
        before, rest = body.split(TRAIN_START, 1)
        after = rest.split(TRAIN_END, 1)[1]
        return f"{before}{section}{after}"
    return f"{section}\n\n{body}".rstrip() + "\n"


def platform_draft(github, repository, plan):
    tag = f"v{plan['platform']['nextVersion']}"
    section = train_section(plan)
    releases = github.request(f"/repos/{repository}/releases?per_page=30") or []
    for release in releases:
        if release["draft"] and release["tag_name"] == tag:
            return github.request(release["url"], "PATCH", {"body": merge_section(release["body"], section)})
    return github.request(f"/repos/{repository}/releases", "POST", {
        "tag_name": tag,
        "name": f"{plan['platform']['title']} {plan['platform']['nextVersion']}",
        "body": section,
        "draft": True,
    })


def summary(plan, write_drafts):
    platform = plan["platform"]
    lines = [
        "# Release train plan",
        "",
        f"Platform {platform['nextVersion']} (previous {platform['previousTag'] or 'none'}): "
        + ("**releases**" if platform["release"] else "nothing to release"),
        "",
        "| Module | Branch | Last release | Next | Decision | Why | Changes |",
        "| --- | --- | --- | --- | --- | --- | --- |",
    ]
    for module in plan["modules"]:
        draft = f" · [draft]({module['draftUrl']})" if module.get("draftUrl") else ""
        lines.append(
            f"| {module['title']} | `{module['branch']}` | {module['lastRelease']} | {module['nextVersion']} |"
            f" **{module['decision']}** | {'; '.join(module['reasons'])} |"
            f" [{len(module['changes'])} commits]({module['compareUrl']}){draft} |"
        )
    for module in plan["modules"]:
        if module.get("draftStatus") and module["draftStatus"] not in ("existing", "created"):
            lines += ["", f"> {module['title']}: {module['draftStatus']}"]
        if module.get("dependencyChanges"):
            lines += ["", f"<details><summary>{module['title']} dependency changes</summary>", ""]
            unique = sorted({difference.split(": ", 1)[-1] for difference in module["dependencyChanges"]})
            lines += [f"- `{difference}`" for difference in unique]
            lines += ["", "</details>"]
    lines += ["", train_section(plan).replace(TRAIN_START, "").replace(TRAIN_END, "").strip()]
    if not write_drafts:
        lines += ["", "_Dry run without draft releases: re-run with `write-drafts` to create or update them._"]
    return "\n".join(lines) + "\n"


def command_drafts(arguments):
    plan = apply_dependency_checks(json.loads(Path(arguments.plan).read_text()), arguments.results)
    if arguments.write_drafts:
        module_github = GitHub(os.environ.get("MODULES_TOKEN"))
        for module in plan["modules"]:
            if module["decision"] == RELEASE:
                release, status = module_draft(module_github, module)
                module["draftStatus"] = status
                if release:
                    module["draftUrl"] = release["html_url"]
        if plan["platform"]["release"]:
            platform_github = GitHub(os.environ.get("GITHUB_TOKEN"))
            release = platform_draft(platform_github, arguments.repository, plan)
            plan["platform"]["draftUrl"] = release["html_url"]
    Path(arguments.output).write_text(json.dumps(plan, indent=2))
    text = summary(plan, arguments.write_drafts)
    step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if step_summary:
        with open(step_summary, "a") as file:
            file.write(text)
    print(text)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    subcommands = parser.add_subparsers(dest="command", required=True)

    plan = subcommands.add_parser("plan")
    plan.add_argument("--catalog", default="gradle/libs.versions.toml")
    plan.add_argument("--manifest", default="gradle/release-train.toml")
    plan.add_argument("--output", default="build/release-train/plan.json")
    plan.set_defaults(handler=command_plan)

    pom_diff = subcommands.add_parser("pom-diff")
    pom_diff.add_argument("--key", required=True)
    pom_diff.add_argument("--base-dir", required=True)
    pom_diff.add_argument("--head-dir", required=True)
    pom_diff.add_argument("--output", required=True)
    pom_diff.set_defaults(handler=command_pom_diff)

    drafts = subcommands.add_parser("drafts")
    drafts.add_argument("--plan", default="build/release-train/plan.json")
    drafts.add_argument("--results", help="Directory holding the pom-diff results")
    drafts.add_argument("--output", default="build/release-train/plan-final.json")
    drafts.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY", f"{ORG}/micronaut-platform"))
    drafts.add_argument("--write-drafts", action="store_true")
    drafts.set_defaults(handler=command_drafts)

    arguments = parser.parse_args(argv)
    arguments.handler(arguments)


if __name__ == "__main__":
    sys.exit(main())
