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
        self.assertEqual(len(MODULE.load_manifest(self.manifest)), len(entries))
        self.assertIn(Path("migration/V1__init.sql"), entries)

    def test_changed_released_migration_fails(self) -> None:
        migration = self.resources / "migration/V7__financial_activities.sql"
        migration.write_text(migration.read_text(encoding="utf-8") + "\n", encoding="utf-8")

        with self.assertRaisesRegex(ValueError, "released migration changed"):
            MODULE.verify_files(self.resources, self.manifest)

    def test_deleted_released_migration_fails(self) -> None:
        (self.resources / "migration/V3__income_payer_and_venmo.sql").unlink()

        with self.assertRaisesRegex(ValueError, "released migration is missing"):
            MODULE.verify_files(self.resources, self.manifest)

    def _highest_released(self, location: str) -> int:
        return max(
            int(MODULE.MIGRATION_NAME.fullmatch(path.name).group(1))
            for path in MODULE.load_manifest(self.manifest)
            if path.parts[0] == location
        )

    def _write(self, relative_path: str) -> None:
        (self.resources / relative_path).write_text(
            "CREATE TABLE example(id BIGINT);\n", encoding="utf-8"
        )

    def test_sql_server_baseline_is_frozen(self) -> None:
        entries = MODULE.verify_files(self.resources, self.manifest)

        self.assertIn(Path("migration-sqlserver/V24__sqlserver_baseline.sql"), entries)

    def test_changed_sql_server_migration_fails(self) -> None:
        migration = self.resources / "migration-sqlserver/V24__sqlserver_baseline.sql"
        migration.write_text(migration.read_text(encoding="utf-8") + "\n", encoding="utf-8")

        with self.assertRaisesRegex(ValueError, "released migration changed"):
            MODULE.verify_files(self.resources, self.manifest)

    def test_new_higher_migration_in_both_locations_is_allowed(self) -> None:
        self._write("migration/V9999__additive.sql")
        self._write("migration-sqlserver/V9999__additive.sql")

        MODULE.verify_files(self.resources, self.manifest)

    def test_new_migration_missing_in_sql_server_location_fails(self) -> None:
        self._write("migration/V9999__additive.sql")

        with self.assertRaisesRegex(
            ValueError, "V9999 exist in migration but are missing in migration-sqlserver"
        ):
            MODULE.verify_files(self.resources, self.manifest)

    def test_new_migration_missing_in_h2_location_fails(self) -> None:
        self._write("migration-sqlserver/V9999__additive.sql")

        with self.assertRaisesRegex(
            ValueError, "V9999 exist in migration-sqlserver but are missing in migration"
        ):
            MODULE.verify_files(self.resources, self.manifest)

    def test_h2_versions_before_sql_server_baseline_need_no_counterpart(self) -> None:
        self.assertTrue(any((self.resources / "migration").glob("V22__*.sql")))
        self.assertFalse(any((self.resources / "migration-sqlserver").glob("V22__*.sql")))

        MODULE.verify_files(self.resources, self.manifest)

    def test_unfrozen_migration_at_highest_released_version_fails(self) -> None:
        self._write(f"migration/V{self._highest_released('migration')}__unfrozen.sql")

        with self.assertRaisesRegex(ValueError, "is not frozen in the checksum manifest"):
            MODULE.verify_files(self.resources, self.manifest)

    def test_unfrozen_sql_server_migration_at_released_version_fails(self) -> None:
        self._write(
            f"migration-sqlserver/V{self._highest_released('migration-sqlserver')}__unfrozen.sql"
        )

        with self.assertRaisesRegex(ValueError, "is not frozen in the checksum manifest"):
            MODULE.verify_files(self.resources, self.manifest)

    def test_manifest_rejects_unknown_location(self) -> None:
        self.manifest.write_text(
            f"{'0' * 64}  migration-other/V1__init.sql\n", encoding="utf-8"
        )

        with self.assertRaisesRegex(ValueError, "invalid checksum manifest line"):
            MODULE.load_manifest(self.manifest)

    def test_base_diff_detects_edit_delete_and_rename(self) -> None:
        diff = "\n".join(
            [
                "M\tbackend/src/main/resources/db/migration/V1__init.sql",
                "D\tbackend/src/main/resources/db/migration/V2__backfill_history_versions.sql",
                "R100\tbackend/src/main/resources/db/migration/V3__income_payer_and_venmo.sql"
                "\tbackend/src/main/resources/db/migration/V3__renamed.sql",
                "A\tbackend/src/main/resources/db/migration/V9999__additive.sql",
            ]
        )
        frozen = {
            "migration/V1__init.sql",
            "migration/V2__backfill_history_versions.sql",
            "migration/V3__income_payer_and_venmo.sql",
        }

        changed = MODULE.released_paths_changed(diff, frozen)

        self.assertEqual(3, len(changed))
        self.assertFalse(any("V9999" in item for item in changed))

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

    def test_base_diff_detects_sql_server_edit(self) -> None:
        frozen = MODULE.frozen_paths_from_base_tree(
            "backend/src/main/resources/db/migration-sqlserver/V24__sqlserver_baseline.sql"
        )

        changed = MODULE.released_paths_changed(
            "M\tbackend/src/main/resources/db/migration-sqlserver/V24__sqlserver_baseline.sql",
            frozen,
        )

        self.assertEqual(1, len(changed))


if __name__ == "__main__":
    unittest.main()
