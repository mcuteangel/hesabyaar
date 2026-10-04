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
    "**/DaggerHesabyarApp*.*",
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
val jacocoSourceRoots = listOf("src/main/java", "src/main/kotlin")

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

// Fail-fast guard for the rust-package allow-list above. This is a dedicated
// task that testDebugUnitTest and testDebugUnitTestRust depend on, so it runs
// before the test suites and fails in seconds if a hand-written file is added
// to the rust package without being matched by handWrittenRustBridgePrefixes.
// It walks jacocoSourceRoots recursively and also checks file contents for
// 'package io.github.mojri.hesabyar.rust' to prevent package/directory drift.
val checkRustBridgeCoverageScope =
  tasks.register("checkRustBridgeCoverageScope") {
    group = "verification"
    description =
      "Fail if a hand-written file in the rust package is not covered by handWrittenRustBridgePrefixes."
    doLast {
      val declarationRegex =
        Regex(
          """^(?:@\w+(?:\([^)]*\))?\s+)*(?:internal\s+|private\s+|public\s+|abstract\s+|sealed\s+|data\s+|enum\s+|value\s+|open\s+)*(?:class|object|interface)\s+(\w+)""",
          RegexOption.MULTILINE
        )

      val unrecognized = mutableListOf<String>()

      jacocoSourceRoots
        .map { file(it) }
        .filter { it.exists() }
        .flatMap { root -> root.walkTopDown().toList() }
        .filter { f -> f.isFile && (f.extension == "kt" || f.extension == "java") && f.name != "hesabyar_core.kt" }
        .forEach { f ->
          val inDir = f.invariantSeparatorsPath.contains(rustPackageSegment)
          var declaresRustPackage = inDir
          if (!declaresRustPackage) {
            f.useLines { lines ->
              for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("package ")) {
                  val pkg = trimmed.removePrefix("package ").trimEnd(';', ' ').trim()
                  if (pkg == "io.github.mojri.hesabyar.rust") {
                    declaresRustPackage = true
                  }
                  break
                }
              }
            }
          }

          if (declaresRustPackage) {
            val fileMatches = handWrittenRustBridgePrefixes.any { f.name.startsWith(it) }
            val text = f.readText()
            val hasForbiddenJvmName = text.contains("@file:JvmName")
            val declarations = declarationRegex.findAll(text).map { it.groupValues[1] }.toList()
            val allDeclarationsMatch =
              declarations.isNotEmpty() &&
                declarations.all { decl -> handWrittenRustBridgePrefixes.any { decl.startsWith(it) } }

            if (!fileMatches || hasForbiddenJvmName || !allDeclarationsMatch) {
              unrecognized.add(
                "${f.invariantSeparatorsPath} (fileMatches=$fileMatches, jvmName=$hasForbiddenJvmName, declarations=$declarations)"
              )
            }
          }
        }

      if (unrecognized.isNotEmpty()) {
        throw org.gradle.api.GradleException(
          "Unrecognized hand-written file(s) or declaration(s) in $rustPackageSegment: $unrecognized. " +
            "Ensure file name and top-level class/interface/object declarations start with one of " +
            "$handWrittenRustBridgePrefixes so JaCoCo handWrittenClassSpec measures them."
        )
      }
    }
  }

tasks.matching { it.name == "testDebugUnitTest" || it.name == "testDebugUnitTestRust" }.configureEach {
  dependsOn(checkRustBridgeCoverageScope)
}

tasks.register(
  "jacocoTestReport",
  org.gradle.testing.jacoco.tasks.JacocoReport::class.java
) {
  // Coverage must include both the fast non-Rust tests and the isolated
  // Rust-bridge tests (testDebugUnitTestRust) — executionData below globs
  // every build/jacoco/*.exec, so both tasks must run before the report.
  // The test tasks in turn depend on checkRustBridgeCoverageScope (fail-fast).
  dependsOn(checkRustBridgeCoverageScope, "testDebugUnitTest", "testDebugUnitTestRust")
  // Guard against the filter failing open: handWrittenClassSpec only filters
  // when a class path contains rustPackageSegment. If a package rename or AGP
  // output reorg ever removes that segment from compiled paths, generated
  // UniFFI classes would silently re-enter the denominator. Fail instead.
  // Missing class roots also fail: generating a report with zero classes would
  // otherwise look like a green coverage upload while measuring nothing.
  doFirst {
    val classRootPaths =
      listOf(
        "build/intermediates/javac/debug/compileDebugJavaWithJavac/classes",
        "build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
      )
    val classRoots = classRootPaths.map { file(it) }
    val missingRoots = classRootPaths.zip(classRoots).filter { !it.second.exists() }
    if (missingRoots.isNotEmpty()) {
      throw org.gradle.api.GradleException(
        "Missing compiled class root(s): ${missingRoots.map { it.first }}. " +
          "Cannot confirm rust package filtering, so the coverage denominator is unknown. " +
          "Run the debug test compile first, or confirm the AGP class output layout in " +
          "gradle/jacoco-coverage.gradle.kts."
      )
    }
    val classFiles =
      classRoots.flatMap { root -> root.walkTopDown().filter { it.isFile }.toList() }
    if (classFiles.none { it.invariantSeparatorsPath.contains(rustPackageSegment) }) {
      throw org.gradle.api.GradleException(
        "No compiled class under $rustPackageSegment. handWrittenClassSpec would filter nothing. " +
          "The Kotlin package or AGP class output layout changed — update " +
          "rustPackageSegment in gradle/jacoco-coverage.gradle.kts."
      )
    }
  }
  executionData.setFrom(fileTree("build/jacoco") { include("*.exec") })
  sourceDirectories.setFrom(jacocoSourceRoots)
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
