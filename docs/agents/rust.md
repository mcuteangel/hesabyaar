# Rust Core Changes (agent guide)

> Part of the agent guide. Start at `AGENTS.md`; it says when to read this file.

## Rust Changes Require Binding Regeneration

The Kotlin side talks to the Rust core (`rust/hesabyar-core`) through the UniFFI bindings. The bindings are generated into `app/src/main/java/io/github/mojri/hesabyar/rust/hesabyar_core.kt`.

- After any change to the Rust source (`rust/**`), regenerate the Kotlin FFI bindings and the host library. Otherwise, the build or the FFI calls do not reflect the change.
- Run `./gradlew --no-daemon :app:generateAndFixBindings`. The alias `:app:generateRustBindings` skips the package-patch/install step.
- Do not edit the generated `hesabyar_core.kt` manually. The task overwrites it.

> Doc comments are part of the UniFFI API checksum. A comment-only change to an exported (`#[uniffi::export]`) function still requires binding regeneration. Then the host library fails the `uniffiCheckApiChecksums` check at load time. Every Rust-tagged test fails with "UniFFI API checksum mismatch" before the test logic runs.
> Locally, the regeneration tasks do nothing unless you force them (`outputs.upToDateWhen`). Use `./gradlew --no-daemon :app:generateAndFixBindings --rerun-tasks`. The generated `hesabyar_core.kt` is gitignored. Regeneration leaves no git diff.
>
> This is a hand-maintained compat object. The task always appends `app/buildSrc/template/HesabyarCore.template.kt` to the generated bindings. It does not patch that template's signatures.
> When a Rust FFI function's signature changes (new, removed, or reordered parameters), update the matching line in that template. Add defaults for any new trailing parameter. Then run `:app:generateAndFixBindings` again.
> Otherwise, the repo `hesabyar_core.kt` has a stale `HesabyarCore.xxx()` wrapper. The wrapper calls the regenerated top-level function with the wrong argument count.


## Rust Core Versioning

The core is bundled with the app. It is not published separately. It has its own versioning scheme. It is independent from the Android app version (root `VERSION` file).

- The base version (`MAJOR.MINOR.PATCH`) lives in `rust/Cargo.toml` `[workspace.package].version`. Bump it manually per SemVer:
  - MAJOR — a breaking change to the FFI surface or the backup schema (`BackupPayload.version`).
  - MINOR — a backward-compatible feature or category added to the core API.
  - PATCH — a bug fix with no API or schema change.
- The build metadata (`+<hash>`) is auto-derived. The Gradle `:app:syncCoreVersion` task derives it from a SHA-256 of the `rust/hesabyar-core/src` tree. It is written to the gitignored `rust/hesabyar-core/src/generated/core_version.rs`. It is embedded through `build.rs` into the `CORE_VERSION` env. At runtime, it becomes available through `get_core_version()` (UniFFI). The metadata changes when the core source changes. The bundled core version reflects the exact build.
- Do not hand-edit `src/generated/core_version.rs`. It is regenerated on every binding or NDK build. `cargo build` and `cargo test` outside Gradle use the Cargo package version.

### Backup schema version (`version` / `appVersion`)

The backup envelope carries two version fields. They are independent from the app `VERSION` file and the core `CORE_VERSION`.

- `version` is the backup format/schema version. The single source of truth is the Rust const `BACKUP_SCHEMA_VERSION` in `hesabyar-core/src/models/mod.rs`. The Kotlin side derives `BuildConfig.BACKUP_SCHEMA_VERSION` from it at build time (see `app/build.gradle.kts`). They cannot drift. Bump it only on a breaking change to the serialized backup structure.
- `appVersion` is the app version that made the backup. At export time, it is written as `BuildConfig.VERSION_NAME` (Kotlin) or `env!("CORE_VERSION")` (Rust default). Do not hardcode a placeholder like `"1.0"`.
