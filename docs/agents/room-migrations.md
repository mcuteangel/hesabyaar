# Room Migrations (agent guide)

> Part of the agent guide. Start at `AGENTS.md`; it says when to read this file.

## Room Migration Checklist

Every change to the database schema or an `@Entity` class must satisfy this checklist:

1. **Database version:** Increment `version` in `@Database(...)` in `AppDatabase.kt`.
2. **Explicit migration:** Create an explicit `Migration(oldVersion, newVersion)` object and register it in the migration list. Using `fallbackToDestructiveMigration()` is strictly forbidden.
3. **Exported schema:** Keep `exportSchema = true` enabled when Room schema exports are configured. The generated JSON schema for the new version must be created or updated in the schemas directory.
4. **Migration test:** Test every migration with `MigrationTestHelper`. The test must:
   - Create the database at the old version.
   - Insert representative sample data.
   - Execute the migration to the new version.
   - Validate the new schema.
   - Validate pre-existing data integrity.
   - Validate new columns and tables.
   - Assert zero data loss.
5. **Backup compatibility:** Verify compatibility with the backup system:
   - Check the backup JSON parser.
   - Check `BACKUP_SCHEMA_VERSION`.
   - Verify backward compatibility with existing backup files.
   - The backup schema version is decoupled from the Room database version; bumping one does not imply bumping the other.
6. **Documentation consistency:** Check `docs/DATABASE_SCHEMA.md` and `docs/MIGRATION_NOTES.md` against `AppDatabase.kt`. Report any schema version mismatches.

Required completion statement:
An agent must not report a schema change as "done" without direct reference to the explicit migration definition and the passing verification tests.
