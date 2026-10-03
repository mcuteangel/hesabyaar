// JaCoCo coverage configuration for the app module (Issue #231).
// Applied from app/build.gradle.kts with:
//   apply(from = "$rootDir/gradle/jacoco-coverage.gradle.kts")
//
// The report denominator must contain only hand-written production code.
// Generated boilerplate is excluded by pattern. The UniFFI-generated classes
// are excluded by a dynamic filter, because their class names change with the
// FFI surface and a hard-coded list would drift.

// Generated code excluded by class-name pattern.
val jacocoReportExcludes =
  listOf(
    // Android generated classes
    "**/R.class",
    "**/R\$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    // Room generated DAO and database implementations
    "**/*_Impl.class",
    "**/*_Impl\$*.class",
    // Hilt and Dagger generated dependency injection code
    "**/hilt_aggregated_deps/**",
    "**/dagger/**",
    "**/*_HiltModules*.*",
    "**/*_Factory*.*",
    "**/*_MembersInjector*.*",
    "**/Hilt_*.*",
    "**/Dagger*.*",
    "**/HesabyarApp_HiltComponents*.*",
    // UniFFI generated file facades and support classes
    "**/hesabyar_core*.*",
    "**/Hesabyar_core*.*",
    "**/Uniffi*.*",
    "**/FfiConverter*.*"
  )

// The package io.github.mojri.hesabyar.rust holds two kinds of classes:
//   - hand-written bridge code from RustBridge*.kt and RustMappers.kt
//   - UniFFI-generated code from hesabyar_core.kt (top-level classes such as
//     Transaction, AnalyticsData, RustBuffer — names tied to the FFI surface)
// Keep a rust-package class only when its file name starts with a hand-written
// bridge prefix. This needs no maintenance when the FFI surface changes.
val rustPackageSegment = "/io/github/mojri/hesabyar/rust/"
val handWrittenRustBridgePrefixes = listOf("RustBridge", "RustMappers")

val handWrittenClassSpec =
  org.gradle.api.specs.Spec<File> { file ->
    val path = file.invariantSeparatorsPath
    if (!path.contains(rustPackageSegment)) {
      true
    } else {
      val fileName = path.substringAfterLast('/')
      handWrittenRustBridgePrefixes.any { fileName.startsWith(it) }
    }
  }

tasks.register(
  "jacocoTestReport",
  org.gradle.testing.jacoco.tasks.JacocoReport::class.java
) {
  // Coverage must include both the fast non-Rust tests and the isolated
  // Rust-bridge tests (testDebugUnitTestRust) — executionData below globs
  // every build/jacoco/*.exec, so both tasks must run before the report.
  dependsOn("testDebugUnitTest", "testDebugUnitTestRust")
  executionData.setFrom(fileTree("build/jacoco") { include("*.exec") })
  sourceDirectories.setFrom("src/main/java", "src/main/kotlin")
  classDirectories.setFrom(
    files(
      fileTree("build/intermediates/javac/debug/compileDebugJavaWithJavac/classes") {
        include("**/*.class")
        exclude(jacocoReportExcludes)
      },
      fileTree("build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes") {
        include("**/*.class")
        exclude(jacocoReportExcludes)
      }
    ).filter(handWrittenClassSpec)
  )
  reports {
    xml.required.set(true)
    html.required.set(false)
    csv.required.set(false)
  }
}