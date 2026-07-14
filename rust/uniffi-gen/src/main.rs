use camino::Utf8Path;
use uniffi_bindgen::bindings::KotlinBindingGenerator;
use uniffi_bindgen::library_mode::generate_bindings;
use uniffi_bindgen::EmptyCrateConfigSupplier;

fn main() -> anyhow::Result<()> {
    let args: Vec<String> = std::env::args().collect();
    if args.len() < 3 {
        eprintln!("Usage: uniffi-gen <library-path> <out-dir>");
        std::process::exit(1);
    }

    let library_path = Utf8Path::new(&args[1]);
    let out_dir = Utf8Path::new(&args[2]);

    std::fs::create_dir_all(out_dir)?;

    let supplier = EmptyCrateConfigSupplier;

    let components = generate_bindings(
        library_path,
        None, // crate_name
        &KotlinBindingGenerator,
        &supplier,
        None, // config_file_override
        out_dir,
        true, // try_format_code
    )?;

    for comp in &components {
        println!("Generated bindings for crate: {}", comp.ci.crate_name());
    }
    println!("Kotlin bindings written to: {}", out_dir);
    Ok(())
}
