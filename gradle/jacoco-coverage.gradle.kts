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

fun scanTripleQuoteString(input: String, start: Int, n: Int, sb: StringBuilder): Int {
  val end = input.indexOf("\"\"\"", start + 3)
  sb.append("\"\"")
  val limit =
    if (end == -1) {
      val nextLine = input.indexOf('\n', start + 3)
      if (nextLine == -1) n else nextLine
    } else {
      end + 3
    }
  for (k in start until limit) {
    if (input[k] == '\n') {
      sb.append('\n')
    }
  }
  return limit
}

fun scanCharLiteral(input: String, start: Int, n: Int, sb: StringBuilder): Int {
  var j = start + 1
  while (j < n) {
    if (input[j] == '\\') {
      j += 2
    } else if (input[j] == '\'') {
      j++
      break
    } else if (input[j] == '\n') {
      break
    } else {
      j++
    }
  }
  sb.append("''")
  return j
}

fun scanRegularString(input: String, start: Int, n: Int, sb: StringBuilder): Int {
  var j = start + 1
  while (j < n) {
    if (input[j] == '\\') {
      j += 2
    } else if (input[j] == '"') {
      j++
      break
    } else if (input[j] == '\n') {
      break
    } else {
      j++
    }
  }
  sb.append("\"\"")
  return j
}

fun scanBlockComment(
  input: String,
  start: Int,
  n: Int,
  isKotlin: Boolean,
  sb: StringBuilder
): Int {
  var depth = 1
  var j = start + 2
  while (j < n && depth > 0) {
    if (input[j] == '\n') {
      sb.append('\n')
    }
    if (isKotlin && j + 1 < n && input[j] == '/' && input[j + 1] == '*') {
      depth++
      j += 2
    } else if (j + 1 < n && input[j] == '*' && input[j + 1] == '/') {
      depth--
      j += 2
    } else {
      j++
    }
  }
  sb.append(' ')
  return j
}

fun scanLineComment(input: String, start: Int, n: Int, sb: StringBuilder): Int {
  val j = input.indexOf('\n', start + 2)
  return if (j == -1) {
    n
  } else {
    sb.append(input[j])
    j + 1
  }
}

fun stripCommentsAndStrings(input: String, isKotlin: Boolean): String {
  val sb = StringBuilder(input.length)
  var i = 0
  val n = input.length
  while (i < n) {
    if (i + 2 < n && input[i] == '"' && input[i + 1] == '"' && input[i + 2] == '"') {
      i = scanTripleQuoteString(input, i, n, sb)
    } else if (input[i] == '\'') {
      i = scanCharLiteral(input, i, n, sb)
    } else if (input[i] == '"') {
      i = scanRegularString(input, i, n, sb)
    } else if (i + 1 < n && input[i] == '/' && input[i + 1] == '*') {
      i = scanBlockComment(input, i, n, isKotlin, sb)
    } else if (i + 1 < n && input[i] == '/' && input[i + 1] == '/') {
      i = scanLineComment(input, i, n, sb)
    } else {
      sb.append(input[i])
      i++
    }
  }
  return sb.toString()
}

val checkRustBridgeCoverageScope =
  tasks.register("checkRustBridgeCoverageScope") {
    group = "verification"
    description =
      "Fail if a hand-written file in the rust package is not covered by handWrittenRustBridgePrefixes."
    doLast {

      // Match top-level declarations strictly anchored at column 0.
      // Both Kotlin/Java forms and @interface (with modifiers) share the column-0 anchor '^'.
      val declarationRegex =
        Regex(
          """^(?:(?:@\w+(?:\((?:[^()]|\([^()]*\))*\))?[\t ]*)*(?:(?:internal|private|public|protected|abstract|sealed|data|enum|value|open|annotation|fun|inline|expect|actual|final|external)\s+)*(?:class|object|interface|enum|record)\s+(\w+)|(?:(?:public|protected|private|abstract|static|final|sealed|strictfp)\s+)*@interface\s+(\w+))""",
          RegexOption.MULTILINE
        )

      val jvmNameRegex =
        Regex(
          """^[ \t]*@file:JvmName\b""",
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
                if (trimmed.startsWith("package") && (trimmed.length == 7 || trimmed[7].isWhitespace())) {
                  val pkg = trimmed.substring(7).trim().removeSuffix(";").trim()
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
            val isKotlin = f.extension == "kt"
            val cleanCode = stripCommentsAndStrings(text, isKotlin)

            // File annotations must appear before package declaration.
            // Deriving from cleanCode ensures comments/strings in header or doc
            // cannot trigger @file:JvmName or hide it.
            val fileHeader =
              cleanCode
                .lineSequence()
                .takeWhile {
                  val trimmed = it.trim()
                  !(trimmed.startsWith("package") && (trimmed.length == 7 || trimmed[7].isWhitespace()))
                }
                .joinToString("\n")
            val hasForbiddenJvmName = jvmNameRegex.containsMatchIn(fileHeader)

            val declarations =
              declarationRegex
                .findAll(cleanCode)
                .map { m -> if (m.groupValues[1].isNotEmpty()) m.groupValues[1] else m.groupValues[2] }
                .toList()
            // An empty declaration set means the Kotlin file holds functions
            // only. Its facade carries the file name, which fileMatches
            // already covers. Java has no function-only facade, so an empty
            // Java match set (enum, record, @interface) fails the build.
            // Only a declaration whose name misses the prefixes fails.
            val mismatchedDeclarations =
              declarations.filter { decl -> handWrittenRustBridgePrefixes.none { decl.startsWith(it) } }

            val emptyDeclarationsFail = declarations.isEmpty() && f.extension == "java"
            if (!fileMatches || hasForbiddenJvmName || mismatchedDeclarations.isNotEmpty() || emptyDeclarationsFail) {
              unrecognized.add(
                "${f.invariantSeparatorsPath} (fileMatches=$fileMatches, jvmName=$hasForbiddenJvmName, mismatchedDeclarations=$mismatchedDeclarations)"
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
  // A report with zero class roots would also publish an empty denominator as
  // green coverage, so fail when no root exists. One missing root is fine:
  // the javac root exists only while the module has Java sources.
  doFirst {
    val classRootPaths =
      listOf(
        "build/intermediates/javac/debug/compileDebugJavaWithJavac/classes",
        "build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
      )
    val classRoots = classRootPaths.map { file(it) }
    // One root may legitimately be absent (the javac root exists only while
    // the module produces Java sources or BuildConfig). The guard needs one
    // root that contains the rust package, not every root.
    val existingRoots = classRoots.filter { it.exists() }
    if (existingRoots.isEmpty()) {
      throw org.gradle.api.GradleException(
        "No compiled class root found. Looked for: $classRootPaths. " +
          "Cannot confirm rust package filtering, so the coverage denominator is unknown. " +
          "Run the debug test compile first, or confirm the AGP class output layout in " +
          "gradle/jacoco-coverage.gradle.kts."
      )
    }
    val classFiles =
      existingRoots.flatMap { root -> root.walkTopDown().filter { it.isFile }.toList() }
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
