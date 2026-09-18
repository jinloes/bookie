from __future__ import annotations

import importlib.util
import shutil
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("verify_released_migrations.py")
SPEC = importlib.util.spec_from_file_location("verify_released_migrations", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class ReleasedMigrationVerificationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.resources = Path(self.temp_dir.name) / "db"
        source = SCRIPT.parents[1] / "src/main/resources/db"
        shutil.copytree(source, self.resources)
        self.manifest = self.resources / "migration-checksums.sha256"

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def test_unchanged_released_migrations_pass(self) -> None:
        entries = MODULE.verify_files(self.resources, self.manifest)
        self.assertEqual(18, len(entries))

    def test_changed_released_migration_fails(self) -> None:
        migration = self.resources / "migration/V7__financial_activities.sql"
        migration.write_text(migration.read_text(encoding="utf-8") + "\n", encoding="utf-8")

        with self.assertRaisesRegex(ValueError, "released migration changed"):
            MODULE.verify_files(self.resources, self.manifest)

    def test_deleted_released_migration_fails(self) -> None:
        (self.resources / "migration/V3__income_payer_and_venmo.sql").unlink()

        with self.assertRaisesRegex(ValueError, "released migration is missing"):
            MODULE.verify_files(self.resources, self.manifest)

    def test_new_higher_migration_is_allowed(self) -> None:
        (self.resources / "migration/V19__additive.sql").write_text(
            "CREATE TABLE additive_example(id BIGINT);\n", encoding="utf-8"
        )

        MODULE.verify_files(self.resources, self.manifest)

    def test_base_diff_detects_edit_delete_and_rename(self) -> None:
        diff = "\n".join(
            [
                "M\tbackend/src/main/resources/db/migration/V1__init.sql",
                "D\tbackend/src/main/resources/db/migration/V2__backfill_history_versions.sql",
                "R100\tbackend/src/main/resources/db/migration/V3__income_payer_and_venmo.sql"
                "\tbackend/src/main/resources/db/migration/V3__renamed.sql",
                "A\tbackend/src/main/resources/db/migration/V19__additive.sql",
            ]
        )
        frozen = {
            "migration/V1__init.sql",
            "migration/V2__backfill_history_versions.sql",
            "migration/V3__income_payer_and_venmo.sql",
        }

        changed = MODULE.released_paths_changed(diff, frozen)

        self.assertEqual(3, len(changed))
        self.assertFalse(any("V19" in item for item in changed))

    def test_base_tree_path_stays_frozen_if_manifest_entry_is_removed(self) -> None:
        frozen = MODULE.frozen_paths_from_base_tree(
            "backend/src/main/resources/db/migration/V10__released.sql"
        )
        changed = MODULE.released_paths_changed(
            "M\tbackend/src/main/resources/db/migration/V10__released.sql",
            frozen,
        )

        self.assertEqual(
            ["M: backend/src/main/resources/db/migration/V10__released.sql"], changed
        )

    def test_migrations_first_added_after_base_are_not_treated_as_modified(self) -> None:
        frozen = MODULE.frozen_paths_from_base_tree(
            "\n".join(
                [
                    "backend/src/main/resources/db/migration/V1__init.sql",
                    "backend/src/main/resources/db/migration/V6__released.sql",
                ]
            )
        )
        diff = "\n".join(
            [
                "A\tbackend/src/main/resources/db/migration/V7__new.sql",
                "A\tbackend/src/main/resources/db/migration/V10__new.sql",
            ]
        )

        self.assertEqual([], MODULE.released_paths_changed(diff, frozen))


if __name__ == "__main__":
    unittest.main()
