#!/usr/bin/env python3
"""Tests for release-train.py: python3 .github/scripts/test_release_train.py"""

import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("release-train.py")
spec = importlib.util.spec_from_file_location("release_train", SCRIPT)
train = importlib.util.module_from_spec(spec)
spec.loader.exec_module(train)


def pom(artifact_id, dependencies, version="5.2.2"):
    entries = "".join(
        f"<dependency><groupId>{group}</groupId><artifactId>{artifact}</artifactId>"
        f"<version>{dependency_version}</version><scope>{scope}</scope></dependency>"
        for group, artifact, dependency_version, scope in dependencies
    )
    return (
        '<project xmlns="http://maven.apache.org/POM/4.0.0"><groupId>io.micronaut.data</groupId>'
        f"<artifactId>{artifact_id}</artifactId><version>{version}</version>"
        f"<dependencies>{entries}</dependencies></project>"
    )


class ClassifyFileTest(unittest.TestCase):
    def classify(self, path, patch=None):
        return train.classify_file({"filename": path, "patch": patch})

    def test_sources(self):
        self.assertEqual(self.classify("data-model/src/main/java/io/micronaut/data/Foo.java"), "source")
        self.assertEqual(self.classify("src/main/resources/META-INF/services/x"), "source")

    def test_docs_tests_and_build(self):
        self.assertEqual(self.classify("src/main/docs/guide/index.adoc"), "docs")
        self.assertEqual(self.classify("data-jdbc/src/test/groovy/FooSpec.groovy"), "test")
        self.assertEqual(self.classify("test-suite-kotlin/src/main/kotlin/Foo.kt"), "test")
        self.assertEqual(self.classify("buildSrc/src/main/groovy/Conventions.groovy"), "build")
        self.assertEqual(self.classify(".github/workflows/gradle.yml"), "build")
        self.assertEqual(self.classify("data-jdbc/build.gradle.kts"), "build")

    def test_catalog(self):
        self.assertEqual(self.classify("gradle/libs.versions.toml"), "catalog")

    def test_gradle_properties(self):
        version_only = "@@ -1 +1 @@\n-projectVersion=5.2.2\n+projectVersion=5.2.3-SNAPSHOT"
        self.assertEqual(self.classify("gradle.properties", version_only), "version")
        other = "@@ -1 +1 @@\n-org.gradle.jvmargs=-Xmx1g\n+org.gradle.jvmargs=-Xmx2g"
        self.assertEqual(self.classify("gradle.properties", other), "build")


class DecideTest(unittest.TestCase):
    def files(self, **categories):
        files = {category: [] for category in ("source", "catalog", "version", "build", "test", "docs")}
        files.update(categories)
        return files

    def test_source_change_releases(self):
        decision, _ = train.decide([{}], self.files(source=["a"], test=["b"]), None, False)
        self.assertEqual(decision, train.RELEASE)

    def test_catalog_change_needs_dependency_check(self):
        decision, _ = train.decide([{}], self.files(catalog=["gradle/libs.versions.toml"]), None, False)
        self.assertEqual(decision, train.CHECK_DEPENDENCIES)

    def test_build_only_change_waits(self):
        decision, _ = train.decide([{}], self.files(build=["a"], docs=["b"]), None, False)
        self.assertEqual(decision, train.SKIP)

    def test_standalone_release_is_bumped(self):
        decision, _ = train.decide([], self.files(), "5.2.3", False)
        self.assertEqual(decision, train.BUMP)

    def test_no_changes(self):
        decision, _ = train.decide([], self.files(), None, False)
        self.assertEqual(decision, train.UNCHANGED)

    def test_truncated_comparison_releases(self):
        decision, _ = train.decide([{}], self.files(build=["a"]), None, True)
        self.assertEqual(decision, train.RELEASE)


class BotCommitTest(unittest.TestCase):
    def commit(self, login, message):
        return {"author": {"login": login}, "commit": {"message": message}}

    def test_bot_commits_are_ignored(self):
        self.assertTrue(train.is_bot_commit(self.commit("micronaut-build", "chore: Bump version to 5.2.3-SNAPSHOT")))
        self.assertTrue(train.is_bot_commit(self.commit("someone", "[skip ci] Release v5.2.2")))
        self.assertFalse(train.is_bot_commit(self.commit("someone", "Fix geospatial ordering (#4097)")))


class ManifestTest(unittest.TestCase):
    def test_defaults_and_overrides(self):
        manifest = {"modules": {"managed-micronaut-data": {}, "managed-micronaut-serialization": {"branch": "3.2.x"}}}
        versions = {"managed-micronaut-data": "5.2.2", "managed-micronaut-serialization": "3.2.4"}
        modules = train.train_modules(manifest, versions)
        self.assertEqual(modules[0]["repo"], "micronaut-projects/micronaut-data")
        self.assertEqual(modules[0]["branch"], "5.2.x")
        self.assertEqual(modules[1]["branch"], "3.2.x")

    def test_unknown_module_fails(self):
        with self.assertRaises(ValueError):
            train.train_modules({"modules": {"managed-micronaut-nope": {}}}, {})


class CatalogChangesTest(unittest.TestCase):
    def test_reports_managed_and_parent_changes(self):
        previous = '[versions]\nmanaged-jna = "5.19.0"\nparent-foo = "1"\ngroovy = "4"\n'
        current = {"managed-jna": "5.19.1", "parent-foo": "1", "groovy": "5", "managed-new": "1.0"}
        changes = train.catalog_changes(previous, current)
        self.assertEqual(
            changes,
            [{"key": "managed-jna", "from": "5.19.0", "to": "5.19.1"}, {"key": "managed-new", "from": None, "to": "1.0"}],
        )


class PomDiffTest(unittest.TestCase):
    def write(self, root, project, content):
        path = Path(root, project, "build/publications/maven/pom-default.xml")
        path.parent.mkdir(parents=True)
        path.write_text(content)

    def test_runtime_dependency_change_is_detected(self):
        with tempfile.TemporaryDirectory() as before, tempfile.TemporaryDirectory() as after:
            self.write(before, "data-jdbc", pom("micronaut-data-jdbc", [("org.x", "x", "1.0", "compile")]))
            self.write(after, "data-jdbc", pom("micronaut-data-jdbc", [("org.x", "x", "1.1", "compile")]))
            differences = train.compare_published(train.published_dependencies(before), train.published_dependencies(after))
            self.assertEqual(len(differences), 2)

    def test_own_version_change_is_ignored(self):
        with tempfile.TemporaryDirectory() as before, tempfile.TemporaryDirectory() as after:
            own = ("io.micronaut.data", "micronaut-data-model")
            self.write(before, "data-jdbc", pom("micronaut-data-jdbc", [(*own, "5.2.2", "compile")], "5.2.2"))
            self.write(after, "data-jdbc", pom("micronaut-data-jdbc", [(*own, "5.2.3-SNAPSHOT", "compile")], "5.2.3-SNAPSHOT"))
            differences = train.compare_published(train.published_dependencies(before), train.published_dependencies(after))
            self.assertEqual(differences, [])

    def test_test_scope_change_is_ignored(self):
        with tempfile.TemporaryDirectory() as before, tempfile.TemporaryDirectory() as after:
            self.write(before, "data-jdbc", pom("micronaut-data-jdbc", [("org.t", "t", "1.0", "test")]))
            self.write(after, "data-jdbc", pom("micronaut-data-jdbc", [("org.t", "t", "2.0", "test")]))
            differences = train.compare_published(train.published_dependencies(before), train.published_dependencies(after))
            self.assertEqual(differences, [])


class ReleaseNotesTest(unittest.TestCase):
    def test_train_section_is_replaced_in_place(self):
        body = f"Intro\n\n{train.TRAIN_START}\nold\n{train.TRAIN_END}\n\n## Bugs\n- fix"
        merged = train.merge_section(body, f"{train.TRAIN_START}\nnew\n{train.TRAIN_END}")
        self.assertIn("new", merged)
        self.assertNotIn("old", merged)
        self.assertTrue(merged.startswith("Intro"))
        self.assertTrue(merged.endswith("- fix"))

    def test_train_section_is_prepended(self):
        merged = train.merge_section("## Bugs\n- fix", f"{train.TRAIN_START}\nnew\n{train.TRAIN_END}")
        self.assertTrue(merged.startswith(train.TRAIN_START))


if __name__ == "__main__":
    unittest.main()
