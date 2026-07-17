use std::path::Path;

fn main() {
    let version = read_generated_version().unwrap_or_else(|| env!("CARGO_PKG_VERSION").to_string());
    println!("cargo:rustc-env=CORE_VERSION={version}");
    // Re-run this build script whenever the generated version file changes so
    // the embedded CORE_VERSION is refreshed on every core source change.
    println!("cargo:rerun-if-changed=src/generated/core_version.rs");
}

/// Reads `pub const CORE_VERSION: &str = "...";` from the Gradle-generated file.
/// Falls back to the Cargo package version when the file is absent (e.g. plain
/// `cargo build`/`cargo test` outside the Gradle binding pipeline).
fn read_generated_version() -> Option<String> {
    let path = Path::new("src/generated/core_version.rs");
    let content = std::fs::read_to_string(path).ok()?;
    let start = content.find('"')?;
    let end = content[start + 1..].find('"').map(|i| start + 1 + i)?;
    Some(content[start + 1..end].to_string())
}
