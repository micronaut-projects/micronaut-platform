#!/usr/bin/env python3
"""Plan a Micronaut release train and draft its release notes.

Subcommands:
  plan      Compare each module's tracked branch with its last release and decide what to release.
  pom-diff  Compare the published POMs of a module at two revisions.
  release-check  Record the outcome of a module's release checks (audit, release build, docs).
  drafts    Fold the dependency checks into the plan, write the summary and the draft releases.
"""

import argparse
import base64
import json
import os
import re
import subprocess
import sys
import tarfile
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
# The Central Portal accepts uploads up to 1 GB: https://central.sonatype.org/publish/publish-portal-upload/
UPLOAD_LIMIT_BYTES = 1_000_000_000
UPLOAD_WARNING_BYTES = UPLOAD_LIMIT_BYTES * 8 // 10
MONTHLY_SHARE_WARNING = 0.25
PUBLISHING_LIMITS_URL = "https://central.sonatype.org/publish/maven-central-publishing-limits/"
CHECKSUM_SUFFIXES = (".md5", ".sha1", ".sha256", ".sha512")
FAILED_CONCLUSIONS ={"failure", "timed_out", "cancelled", "action_required", "startup_failure"}
AUDIT_FINDINGS_HEADER = "Actionable vulnerability findings:"
TRAIN_START = "<!-- release-train:start -->"
TRAIN_END = "<!-- release-train:end -->"

RELEASE = "release"
BUMP = "bump"
CHECK_DEPENDENCIES = "check-dependencies"
SKIP = "skip"
UNCHANGED = "unchanged"
EXCLUDED = "excluded"


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
            "excluded": settings.get("exclude"),
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


def branch_ci(github, repo, sha):
    """Summarise the check runs on the branch head the train would release."""
    response = github.request(f"/repos/{repo}/commits/{sha}/check-runs?per_page=100") or {"check_runs": []}
    runs = response["check_runs"]
    failing = sorted({run["name"] for run in runs if run["conclusion"] in FAILED_CONCLUSIONS})
    pending = sorted({run["name"] for run in runs if run["status"] != "completed"})
    if failing:
        state = "failure"
    elif pending:
        state = "pending"
    else:
        state = "success" if runs else "none"
    return {"state": state, "failing": failing, "pending": pending, "url": f"https://github.com/{repo}/commit/{sha}"}


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
    head = comparison["commits"][-1]["sha"] if comparison["commits"] else base

    return {
        **module,
        "title": properties.get("title", repo.split("/")[1]),
        "lastRelease": released_since or module["current"],
        "base": base,
        "head": head,
        "ci": branch_ci(github, repo, head) if decision in (RELEASE, CHECK_DEPENDENCIES) else None,
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


def plan_excluded(github, module):
    """An excluded module releases on its own; the train only picks up those releases for the platform."""
    reason = f"outside the train: {module['excluded']}"
    try:
        planned = plan_module(github, module)
    except RuntimeError as error:
        return {
            **module, "title": module["key"].removeprefix("managed-"), "lastRelease": module["current"],
            "nextVersion": "", "decision": EXCLUDED, "reasons": [reason, f"not checked: {error}"],
            "changes": [], "files": {}, "compareUrl": f"https://github.com/{module['repo']}", "ci": None,
        }
    planned["ci"] = None
    if planned["lastRelease"] != module["current"]:
        planned["decision"] = BUMP
        planned["reasons"] = [f"v{planned['lastRelease']} was released on its own and is not in the platform yet", reason]
    else:
        planned["decision"] = EXCLUDED
        planned["reasons"] = [reason]
    return planned


def command_plan(arguments):
    versions = tomllib.loads(Path(arguments.catalog).read_text())["versions"]
    manifest = tomllib.loads(Path(arguments.manifest).read_text())
    github = GitHub(os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN"))
    modules = [
        plan_excluded(github, module) if module["excluded"] else plan_module(github, module)
        for module in train_modules(manifest, versions)
    ]
    plan = {"platform": plan_platform(versions, modules), "modules": modules}
    plan["platform"]["centralLimits"] = manifest.get("central-limits")
    plan["platform"]["release"] = platform_releases(plan)
    Path(arguments.output).parent.mkdir(parents=True, exist_ok=True)
    Path(arguments.output).write_text(json.dumps(plan, indent=2))

    # Every module that may release gets a release check; catalog-only changes also compare their POMs.
    checks = [
        {
            "key": module["key"],
            "title": module["title"],
            "repo": module["repo"],
            "base": module["base"],
            "head": module["head"],
            "version": module["nextVersion"],
            "check-dependencies": module["decision"] == CHECK_DEPENDENCIES,
        }
        for module in modules if module["decision"] in (RELEASE, CHECK_DEPENDENCIES)
    ]
    write_output("release-checks", json.dumps(checks))
    write_output("has-release-checks", str(bool(checks)).lower())
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


def audit_findings(log_text):
    """Return the actionable findings the vulnerability audit printed, one entry per vulnerable component."""
    findings, collecting = [], False
    for line in log_text.splitlines():
        line = re.sub(r"^\S+Z ", "", line.rstrip())
        if line.strip() == AUDIT_FINDINGS_HEADER:
            collecting = True
        elif collecting and line.startswith("- "):
            findings.append(line[2:])
        elif collecting and line.startswith("  ") and findings:
            findings[-1] += f" {line.strip()}"
        elif collecting and line.strip():
            collecting = False
    return findings


def bundle_stats(bundle_dir):
    """Size and file count of the bundle a release would upload to the Central Portal."""
    bundles = sorted(Path(bundle_dir).glob("*.tgz")) + sorted(Path(bundle_dir).glob("*.tar*")) if bundle_dir else []
    if not bundles:
        return None
    bundle = bundles[0]
    with tarfile.open(bundle) as archive:
        names = [member.name for member in archive.getmembers() if member.isfile()]
    # The rehearsal is not signed: a release adds one .asc signature per file that is not a checksum.
    signed = [name for name in names if not name.endswith(CHECKSUM_SUFFIXES + (".asc",))]
    return {"name": bundle.name, "bytes": bundle.stat().st_size, "files": len(names) + len(signed)}


def command_release_check(arguments):
    """Record the outcome of each release check for one module, as reported by the workflow steps."""
    checks = {
        "dependencies": os.environ.get("DEPENDENCIES_OUTCOME") or "skipped",
        "audit": os.environ.get("AUDIT_OUTCOME") or "skipped",
        "build": os.environ.get("BUILD_OUTCOME") or "skipped",
        "docs": os.environ.get("DOCS_OUTCOME") or "skipped",
    }
    result = {"key": arguments.key, "checks": checks, "runUrl": os.environ.get("RUN_URL")}
    audit_log = Path(arguments.audit_log) if arguments.audit_log else None
    if audit_log and audit_log.exists():
        text = audit_log.read_text(errors="replace")
        result["auditFindings"] = audit_findings(text)
        result["auditAdvisory"] = "Vulnerability audit advisory" in text
    bundle = bundle_stats(arguments.bundle_dir)
    if bundle:
        result["bundle"] = bundle
    dependency_result = Path(arguments.dependency_result) if arguments.dependency_result else None
    if dependency_result and dependency_result.exists():
        result["dependencyDiff"] = json.loads(dependency_result.read_text())
    elif checks["dependencies"] == "success":
        checks["dependencies"] = "failure"
    Path(arguments.output).write_text(json.dumps(result, indent=2))
    print(json.dumps(result, indent=2))
    failed = [name for name, outcome in checks.items() if outcome == "failure"]
    if failed:
        raise SystemExit(f"Release checks failed for {arguments.key}: {', '.join(failed)}")


def apply_release_checks(plan, results_dir):
    results = {}
    for path in Path(results_dir).glob("**/release-check.json") if results_dir else []:
        result = json.loads(path.read_text())
        results[result["key"]] = result
    for module in plan["modules"]:
        if module["key"] in results:
            module["releaseCheck"] = results[module["key"]]
        if module["decision"] != CHECK_DEPENDENCIES:
            continue
        result = results.get(module["key"], {}).get("dependencyDiff")
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


def commit_count(module):
    count = len(module["changes"])
    return f"{count} commit{'s' if count != 1 else ''}"


def train_section(plan):
    modules = plan["modules"]
    released = [module for module in modules if module["decision"] == RELEASE]
    bumped = [module for module in modules if module["decision"] == BUMP]
    held = [module for module in modules if module["decision"] in (SKIP, UNCHANGED)]
    excluded = [module for module in modules if module["decision"] == EXCLUDED]
    lines = [TRAIN_START, "## Release train", ""]
    if released or bumped:
        lines += ["| Module | Version | Why |", "| --- | --- | --- |"]
        for module in released:
            notes = f" ([notes]({module['draftUrl']}))" if module.get("draftUrl") else ""
            lines.append(
                f"| {module['title']} | {module['current']} → {module['nextVersion']} |"
                f" {'; '.join(module['reasons'])}, [{commit_count(module)}]({module['compareUrl']}){notes} |"
            )
        for module in bumped:
            lines.append(f"| {module['title']} | {module['current']} → {module['lastRelease']} | {'; '.join(module['reasons'])} |")
        lines.append("")
    if held:
        lines.append("Not released: " + ", ".join(
            f"{module['title']} ({'; '.join(module['reasons'])})" for module in held
        ))
        lines.append("")
    if excluded:
        lines.append("Released on their own, outside the train: " + ", ".join(module["title"] for module in excluded))
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


CHECK_LABELS = {
    "audit": "vulnerability audit",
    "build": "release build",
    "docs": "docs",
    "dependencies": "dependency check",
}
ICONS = {"success": "✅", "failure": "❌", "cancelled": "❌", "advisory": "⚠️", "pending": "⏳", "skipped": "➖", "none": "➖"}


def check_names(names, limit=3):
    shown = ", ".join(name if len(name) <= 60 else f"{name[:57]}..." for name in names[:limit])
    return f"{shown} and {len(names) - limit} more" if len(names) > limit else shown


def readiness(module):
    """Return (cells, blocking, warnings) for a module the train would release."""
    result = module.get("releaseCheck")
    if result is None:
        cells = {name: "failure" for name in CHECK_LABELS}
        return cells, ["release checks did not run"], []
    cells = dict(result["checks"])
    if cells["audit"] == "success" and result.get("auditAdvisory"):
        cells["audit"] = "advisory"
    ci = module.get("ci") or {"state": "none"}
    cells["ci"] = ci["state"]
    blocking = [CHECK_LABELS[name] for name in CHECK_LABELS if cells[name] in ("failure", "cancelled")]
    # The release workflow does not run the branch's CI, so a red branch warns rather than blocks.
    warnings = []
    if ci["state"] == "failure":
        warnings.append(f"branch CI failing: {check_names(ci['failing'])}")
    elif ci["state"] == "pending":
        warnings.append(f"branch CI still running: {check_names(ci['pending'])}")
    if cells["audit"] == "advisory":
        warnings.append("vulnerability audit findings are advisory for milestones and release candidates")
    bundle = result.get("bundle")
    if bundle and bundle["bytes"] > UPLOAD_LIMIT_BYTES:
        blocking.append(f"bundle is {megabytes(bundle['bytes'])}, over the Central Portal's 1 GB upload limit")
    elif bundle and bundle["bytes"] > UPLOAD_WARNING_BYTES:
        warnings.append(f"bundle is {megabytes(bundle['bytes'])}, close to the Central Portal's 1 GB upload limit")
    return cells, blocking, warnings


def megabytes(size):
    return f"{size / 1_000_000:,.1f} MB"


def monthly_share(total_bytes, files, releases, limits):
    """The train's share of the namespace's monthly publishing limits, and a warning when it is large."""
    if not limits:
        return "", None
    shares = {
        "size": (total_bytes / limits["bytes"], f"{limits['bytes'] / 1e9:g} GB"),
        "files": (files / limits["files"], f"{limits['files']:,} files"),
        "releases": (releases / limits["releases"], f"{limits['releases']:,} releases"),
    }
    text = " This train uses " + ", ".join(
        f"{share:.1%} of the monthly {name} limit ({limit})" for name, (share, limit) in shares.items()
    ) + "."
    large = [name for name, (share, _) in shares.items() if share > MONTHLY_SHARE_WARNING]
    warning = (
        f"this train alone uses over {MONTHLY_SHARE_WARNING:.0%} of the monthly {' and '.join(large)} limit"
        if large else None
    )
    return text, warning


def train_upload(releasing, limits=None):
    """One line totalling what the train would upload to the Central Portal."""
    bundles = [(module, (module.get("releaseCheck") or {}).get("bundle")) for module in releasing]
    measured = [(module, bundle) for module, bundle in bundles if bundle]
    if not measured:
        return None
    total = sum(bundle["bytes"] for _, bundle in measured)
    files = sum(bundle["files"] for _, bundle in measured)
    largest_module, largest = max(measured, key=lambda entry: entry[1]["bytes"])
    unmeasured = len(bundles) - len(measured)
    # The platform BOM is one more release; its size is negligible.
    share, warning = monthly_share(total, files, len(bundles) + 1, limits)
    line = (
        f"**Central upload:** {len(measured)} deployment{'s' if len(measured) != 1 else ''}, {megabytes(total)} and about {files:,} files once signed"
        + (f" ({unmeasured} not measured)" if unmeasured else "")
        + f". Each module is uploaded separately; the largest is {largest_module['title']} at {megabytes(largest['bytes'])}"
        f" (limit 1 GB per upload).{share} Check the namespace's usage this month on the Central Portal before the real run"
        f" ([publishing limits]({PUBLISHING_LIMITS_URL}))."
    )
    return line + (f"\n\n> ⚠️ {warning[0].upper()}{warning[1:]}." if warning else "")


def readiness_section(plan):
    releasing = [module for module in plan["modules"] if module["decision"] == RELEASE]
    if not releasing:
        return []
    states = {module["key"]: readiness(module) for module in releasing}
    blocked = [module for module in releasing if states[module["key"]][1]]
    if blocked:
        verdict = f"❌ **{len(blocked)} of {len(releasing)} modules would fail their release:** " + "; ".join(
            f"{module['title']} ({', '.join(states[module['key']][1])})" for module in blocked
        )
    else:
        verdict = f"✅ **All {len(releasing)} releasing modules pass their release checks.**"
    upload = train_upload(releasing, plan["platform"].get("centralLimits"))
    lines = [
        "## Release readiness",
        "",
        verdict,
        "",
        *([upload, ""] if upload else []),
        "| Module | Version | Vulnerability audit | Release build | Docs | Dependencies | Branch CI | Bundle |",
        "| --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for module in releasing:
        cells = states[module["key"]][0]
        run = (module.get("releaseCheck") or {}).get("runUrl")
        title = f"[{module['title']}]({run})" if run else module["title"]
        ci = f"[{ICONS[cells['ci']]}]({module['ci']['url']})" if module.get("ci") else ICONS["none"]
        bundle = (module.get("releaseCheck") or {}).get("bundle")
        size = f"{megabytes(bundle['bytes'])}, {bundle['files']:,} files" if bundle else ICONS["none"]
        lines.append(
            f"| {title} | {module['nextVersion']} | {ICONS[cells['audit']]} | {ICONS[cells['build']]} |"
            f" {ICONS[cells['docs']]} | {ICONS[cells['dependencies']]} | {ci} | {size} |"
        )
    for module in releasing:
        findings = (module.get("releaseCheck") or {}).get("auditFindings")
        if findings:
            failed = states[module["key"]][0]["audit"] == "failure"
            count = f"{len(findings)} vulnerability finding{'s' if len(findings) != 1 else ''}"
            lines += ["", f"<details{' open' if failed else ''}><summary>{module['title']}: {count}</summary>", ""]
            lines += [f"- {finding}" for finding in findings]
            lines += ["", "</details>"]
        for warning in states[module["key"]][2]:
            lines += ["", f"> ⚠️ {module['title']}: {warning}"]
    lines.append("")
    return lines


def annotations(plan):
    """GitHub workflow commands that surface blocked modules on the run page."""
    lines = []
    for module in plan["modules"]:
        if module["decision"] != RELEASE:
            continue
        _, blocking, warnings = readiness(module)
        if blocking:
            lines.append(f"::error title={module['title']} {module['nextVersion']} would fail::{'; '.join(blocking)}")
        for warning in warnings:
            lines.append(f"::warning title={module['title']} {module['nextVersion']}::{warning}")
    return lines


def summary(plan, write_drafts):
    platform = plan["platform"]
    lines = [
        "# Release train plan",
        "",
        f"Platform {platform['nextVersion']} (previous {platform['previousTag'] or 'none'}): "
        + ("**releases**" if platform["release"] else "nothing to release"),
        "",
        *readiness_section(plan),
        "## Modules",
        "",
        "| Module | Branch | Last release | Next | Decision | Why | Changes |",
        "| --- | --- | --- | --- | --- | --- | --- |",
    ]
    for module in plan["modules"]:
        draft = f" · [draft]({module['draftUrl']})" if module.get("draftUrl") else ""
        lines.append(
            f"| {module['title']} | `{module['branch']}` | {module['lastRelease']} | {module['nextVersion']} |"
            f" **{module['decision']}** | {'; '.join(module['reasons'])} |"
            f" [{commit_count(module)}]({module['compareUrl']}){draft} |"
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
    plan = apply_release_checks(json.loads(Path(arguments.plan).read_text()), arguments.results)
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
    for line in annotations(plan):
        print(line)


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

    release_check = subcommands.add_parser("release-check")
    release_check.add_argument("--key", required=True)
    release_check.add_argument("--audit-log", help="Output of the vulnerability audit")
    release_check.add_argument("--dependency-result", help="pom-diff result, when the dependencies were compared")
    release_check.add_argument("--bundle-dir", help="Directory holding the rehearsed Central Portal bundle")
    release_check.add_argument("--output", required=True)
    release_check.set_defaults(handler=command_release_check)

    drafts = subcommands.add_parser("drafts")
    drafts.add_argument("--plan", default="build/release-train/plan.json")
    drafts.add_argument("--results", help="Directory holding the release-check results")
    drafts.add_argument("--output", default="build/release-train/plan-final.json")
    drafts.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY", f"{ORG}/micronaut-platform"))
    drafts.add_argument("--write-drafts", action="store_true")
    drafts.set_defaults(handler=command_drafts)

    arguments = parser.parse_args(argv)
    arguments.handler(arguments)


if __name__ == "__main__":
    sys.exit(main())
