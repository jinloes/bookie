#!/usr/bin/env python3
"""Fail closed when a released Flyway migration changes."""

from __future__ import annotations

import argparse
import hashlib
import re
import subprocess
import sys
from pathlib import Path

CHECKSUM_LINE = re.compile(r"^([0-9a-f]{64})  (migration/V([0-9]+)__[^/]+\.sql)$")
MIGRATION_NAME = re.compile(r"^V([0-9]+)__[^/]+\.sql$")


def load_manifest(manifest_path: Path) -> dict[Path, str]:
    entries: dict[Path, str] = {}
    for line_number, raw_line in enumerate(
        manifest_path.read_text(encoding="utf-8").splitlines(), start=1
    ):
        match = CHECKSUM_LINE.fullmatch(raw_line)
        if match is None:
            raise ValueError(f"invalid checksum manifest line {line_number}: {raw_line!r}")
        relative_path = Path(match.group(2))
        if relative_path in entries:
            raise ValueError(f"duplicate checksum entry: {relative_path}")
        entries[relative_path] = match.group(1)
    if not entries:
        raise ValueError("released migration checksum manifest is empty")
    return entries


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_files(resources_root: Path, manifest_path: Path) -> dict[Path, str]:
    entries = load_manifest(manifest_path)
    released_versions: set[int] = set()
    for relative_path, expected in entries.items():
        migration = resources_root / relative_path
        if migration.is_symlink():
            raise ValueError(f"released migration must not be a symlink: {relative_path}")
        if not migration.is_file():
            raise ValueError(f"released migration is missing: {relative_path}")
        actual = sha256(migration)
        if actual != expected:
            raise ValueError(
                f"released migration changed: {relative_path} "
                f"(expected {expected}, observed {actual})"
            )
        released_versions.add(int(MIGRATION_NAME.fullmatch(migration.name).group(1)))

    highest_released = max(released_versions)
    for migration in (resources_root / "migration").glob("V*__*.sql"):
        match = MIGRATION_NAME.fullmatch(migration.name)
        if match is None:
            continue
        version = int(match.group(1))
        relative_path = migration.relative_to(resources_root)
        if version <= highest_released and relative_path not in entries:
            raise ValueError(
                f"migration version V{version} is at or below released V{highest_released} "
                f"but is not frozen in the checksum manifest: {relative_path}"
            )
    return entries


def released_paths_changed(diff_output: str, frozen_paths: set[str]) -> list[str]:
    changed: list[str] = []
    for line in diff_output.splitlines():
        fields = line.split("\t")
        if len(fields) < 2:
            continue
        status = fields[0]
        paths = fields[1:]
        for path in paths:
            normalized = path.removeprefix("backend/src/main/resources/db/")
            if normalized in frozen_paths:
                changed.append(f"{status}: {path}")
    return changed


def verify_base_diff(repository_root: Path, base_sha: str, entries: dict[Path, str]) -> None:
    if not base_sha or set(base_sha) == {"0"}:
        return
    ancestor = subprocess.run(
        ["git", "cat-file", "-e", f"{base_sha}^{{commit}}"],
        cwd=repository_root,
        capture_output=True,
        text=True,
        check=False,
    )
    if ancestor.returncode != 0:
        raise ValueError(
            f"base commit {base_sha} is unavailable; CI checkout must fetch full history"
        )
    base_tree = subprocess.run(
        [
            "git",
            "ls-tree",
            "-r",
            "--name-only",
            base_sha,
            "--",
            "backend/src/main/resources/db/migration",
        ],
        cwd=repository_root,
        capture_output=True,
        text=True,
        check=True,
    )
    frozen_paths = {str(path) for path in entries}
    frozen_paths.update(
        path.removeprefix("backend/src/main/resources/db/")
        for path in base_tree.stdout.splitlines()
        if path
    )
    diff = subprocess.run(
        [
            "git",
            "--no-pager",
            "diff",
            "--name-status",
            "--find-renames",
            f"{base_sha}...HEAD",
            "--",
            "backend/src/main/resources/db/migration",
        ],
        cwd=repository_root,
        capture_output=True,
        text=True,
        check=True,
    )
    changed = released_paths_changed(diff.stdout, frozen_paths)
    if changed:
        raise ValueError(
            "released migrations were modified, deleted, or renamed:\n  "
            + "\n  ".join(changed)
        )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-sha", default="")
    args = parser.parse_args()

    backend_root = Path(__file__).resolve().parents[1]
    repository_root = backend_root.parent
    resources_root = backend_root / "src/main/resources/db"
    manifest_path = resources_root / "migration-checksums.sha256"
    try:
        entries = verify_files(resources_root, manifest_path)
        verify_base_diff(repository_root, args.base_sha, entries)
    except (OSError, subprocess.SubprocessError, ValueError) as error:
        print(f"Released migration verification failed: {error}", file=sys.stderr)
        return 1
    print(f"Verified {len(entries)} immutable released migrations.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
