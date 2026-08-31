#!/usr/bin/env python3
"""Create a CycloneDX SBOM from the platform POM and generated version catalog."""

import argparse
import json
import re
import sys
import xml.etree.ElementTree as element_tree
from pathlib import Path
from urllib.parse import quote


MAVEN_NAMESPACE = "{http://maven.apache.org/POM/4.0.0}"
PROPERTY_PATTERN = re.compile(r"\$\{([^}]+)}")


def element_text(element, name):
    child = element.find(f"{MAVEN_NAMESPACE}{name}")
    return child.text.strip() if child is not None and child.text else None


def resolve(value, properties):
    if value is None:
        return None
    for _ in range(20):
        resolved = PROPERTY_PATTERN.sub(
            lambda match: properties.get(match.group(1), match.group(0)), value
        )
        if resolved == value:
            break
        value = resolved
    if PROPERTY_PATTERN.search(value):
        raise ValueError(f"Could not resolve Maven property in '{value}'")
    return value


def purl(group, name, version):
    return "pkg:maven/{}/{}@{}".format(
        quote(group, safe="."), quote(name, safe="._-"), quote(version, safe="._-+"),
    )


def managed_components(pom):
    root = element_tree.parse(pom).getroot()
    properties = {
        child.tag.removeprefix(MAVEN_NAMESPACE): child.text.strip()
        for child in root.findall(f"{MAVEN_NAMESPACE}properties/*")
        if child.text
    }
    project_group = element_text(root, "groupId")
    project_name = element_text(root, "artifactId")
    project_version = element_text(root, "version")
    if not all((project_group, project_name, project_version)):
        raise ValueError("The platform POM must define groupId, artifactId, and version")

    properties.update({
        "project.groupId": project_group,
        "project.artifactId": project_name,
        "project.version": project_version,
    })
    dependencies = root.findall(
        f"{MAVEN_NAMESPACE}dependencyManagement/{MAVEN_NAMESPACE}dependencies/{MAVEN_NAMESPACE}dependency"
    )
    components = {}
    for dependency in dependencies:
        group = resolve(element_text(dependency, "groupId"), properties)
        name = resolve(element_text(dependency, "artifactId"), properties)
        version = resolve(element_text(dependency, "version"), properties)
        dependency_type = resolve(element_text(dependency, "type") or "jar", properties)
        classifier = resolve(element_text(dependency, "classifier") or "", properties)
        if not all((group, name, version)):
            raise ValueError("Each managed dependency must define groupId, artifactId, and version")
        # Maven's managed dependency identity also includes type and classifier. Keep
        # the last declaration, matching the effective managed version in this POM.
        components[(group, name, dependency_type, classifier)] = (group, name, version)
    return (project_group, project_name, project_version), sorted(set(components.values()))


def catalog_components(catalog):
    versions = {}
    components = []
    section = None
    libraries = []
    for line in catalog.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("[") and line.endswith("]"):
            section = line[1:-1]
            continue
        if "=" not in line:
            continue
        alias, value = (part.strip() for part in line.split("=", 1))
        if section == "versions":
            versions[alias] = json.loads(value)
        elif section == "libraries":
            libraries.append((alias, value))

    for alias, value in libraries:
        fields = dict(re.findall(r'(group|name|module|version(?:\.ref)?)\s*=\s*"([^"]+)"', value))
        group = fields.get("group")
        name = fields.get("name")
        if not group or not name:
            module = fields.get("module")
            if not module or ":" not in module:
                raise ValueError(f"Version catalog library '{alias}' has no module coordinates")
            group, name = module.split(":", 1)
        version = fields.get("version") or versions.get(fields.get("version.ref"))
        if not version:
            raise ValueError(f"Version catalog library '{alias}' has no resolved version")
        components.append((group, name, version))
    return components


def main():
    parser = argparse.ArgumentParser(
        description="Generate a CycloneDX SBOM from a platform POM and generated version catalog."
    )
    parser.add_argument("--pom", required=True, type=Path)
    parser.add_argument("--catalog", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    project, pom_components = managed_components(args.pom)
    components = sorted(set(pom_components + catalog_components(args.catalog)))
    project_purl = purl(*project) + "?project_path=%3Amicronaut-platform"
    component_refs = [purl(*component) for component in components]
    sbom = {
        "bomFormat": "CycloneDX",
        "specVersion": "1.6",
        "version": 1,
        "metadata": {
            "component": {
                "type": "library",
                "bom-ref": project_purl,
                "group": project[0],
                "name": project[1],
                "version": project[2],
                "purl": project_purl,
            }
        },
        "components": [
            {
                "type": "library",
                "bom-ref": ref,
                "group": component[0],
                "name": component[1],
                "version": component[2],
                "purl": ref,
            }
            for component, ref in zip(components, component_refs)
        ],
        "dependencies": [{"ref": project_purl, "dependsOn": component_refs}],
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(sbom, indent=2) + "\n", encoding="utf-8")
    print(f"Generated SBOM with {len(components)} managed dependencies at {args.output}")


if __name__ == "__main__":
    try:
        main()
    except (element_tree.ParseError, ValueError) as error:
        print(f"Unable to generate platform BOM SBOM: {error}", file=sys.stderr)
        sys.exit(1)
