import dev.detekt.gradle.Detekt

val appId = "io.github.mojri.hesabyar"
val storePwdKey = "KEYSTORE_PASSWORD"
val keyAliasKey = "KEY_ALIAS"
val keyPasswordKey = "KEY_PASSWORD"

fun resolveCredential(key: String) =
  providers.gradleProperty(key).orNull
    ?: providers.environmentVariable(key).orNull
    ?: ""

fun runUniffiGen(
  libPath: File,
  outDir: File
): Int {
  val cmd =
    listOf(
      "cargo",
      "run",
      "--manifest-path",
      file("$rustDir/Cargo.toml").absolutePath,
      "--package",
      "uniffi-gen",
      "--",
      libPath.absolutePath,
      outDir.absolutePath,
    )
  val pb = ProcessBuilder(cmd)
  pb.directory(rustDir)
  pb.inheritIO()
  return pb.start().waitFor()
}

fun buildHostLibrary() {
  logger.lifecycle("Step 1/4: Building host-native Rust library...")
  val cargoBuild = ProcessBuilder("cargo", "build", "--release")
  cargoBuild.directory(rustDir)
  cargoBuild.inheritIO()
  if (cargoBuild.start().waitFor() != 0) {
    throw GradleException("cargo build --release failed")
  }
}

fun resolveHostArtifact(): File {
  val osName = System.getProperty("os.name").lowercase()
  return when {
    osName.contains("win") -> rustTargetDir.resolve("release/hesabyar_core.dll")
    osName.contains("mac") -> rustTargetDir.resolve("release/libhesabyar_core.dylib")
    else -> rustTargetDir.resolve("release/libhesabyar_core.so")
  }.also { hostLib ->
    if (!hostLib.exists()) {
      throw GradleException("Host library not found at: ${hostLib.absolutePath}")
    }
    logger.lifecycle("Step 2/4: Host library at ${hostLib.name}")
  }
}

fun generateBindings(
  hostLib: File,
  outDir: File,
) {
  logger.lifecycle("Step 3/4: Generating UniFFI Kotlin bindings...")
  if (outDir.exists()) outDir.deleteRecursively()
  outDir.mkdirs()
  val exitCode = runUniffiGen(hostLib, outDir)
  if (exitCode != 0) {
    throw GradleException("UniFFI binding generation failed")
  }
}

fun patchAndInstallOutput(
  tempDir: File,
  dest: File,
) {
  logger.lifecycle("Step 4/4: Patching package and installing bindings...")
  val generatedKt =
    tempDir
      .walkTopDown()
      .firstOrNull { it.isFile && it.extension == "kt" }
      ?: throw GradleException("No .kt file found in ${tempDir.absolutePath}")

  val content = generatedKt.readText(Charsets.UTF_8)
  val patched =
    content.replace(
      Regex("^package uniffi\.hesabyar_core$", RegexOption.MULTILINE),
      "package $appId.rust"
    )

  val templateFile = file("buildSrc/template/HesabyarCore.template.kt")
  val pkg = "$appId.rust"
  val compatObject = "\n" + templateFile.readText(Charsets.UTF_8).replace("__PKG__", pkg)

  dest.parentFile.mkdirs()
  dest.writeText(patched + compatObject, Charsets.UTF_8)
}

// Read version from VERSION file
val versionFile = rootProject.file("VERSION")
val versionString = if (versionFile.exists()) {
    versionFile.readText().trim()
} else {
    "0.1.0"
}

val versionParts = versionString.split(".")
val calculatedVersionCode = if (versionParts.size == 3) {
    try {
        versionParts[0].toInt() * 10000 + versionParts[1].toInt() * 100 + versionParts[2].toInt()
    } catch (e: NumberFormatException) {
        1
    }
} else {
    1
}

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.hilt)
  alias(libs.plugins.detekt)
  jacoco
}

android {
  namespace = appId
  compileSdk = 37

  defaultConfig {
    applicationId = appId
    minSdk = 26
    targetSdk = 36
    versionCode = calculatedVersionCode
    versionName = versionString
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      storeFile = file("$rootDir/my-upload-key.jks")
      storePassword = resolveCredential(storePwdKey)
      keyAlias = resolveCredential(keyAliasKey)
      keyPassword = resolveCredential(keyPasswordKey)
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      isDebuggable = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      isDebuggable = true
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }
  lint {
    baseline = file("lint-baseline.xml")
  }
}

android.sourceSets.named("main") {
  java.srcDir("src/main/java/${appId.replace(".", "/")}/rust")
  jniLibs.srcDir("src/main/jniLibs")
}

val rustReleaseDir = file("${rootProject.projectDir}/rust/target/release")
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
  jvmArgs(
    "-Djna.library.path=${rustReleaseDir.absolutePath}",
    "-Djava.library.path=${rustReleaseDir.absolutePath}"
  )
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

tasks.register("checkSigningConfig") {
  group = "signing"
  description = "Validates that release signing credentials are configured"
  doFirst {
    val storePassword = resolveCredential(storePwdKey)
    val keyAlias = resolveCredential(keyAliasKey)
    val keyPassword = resolveCredential(keyPasswordKey)
    val keystoreFile = file("$rootDir/my-upload-key.jks")
    val issues = mutableListOf<String>()
    if (storePassword.isBlank()) issues.add("$storePwdKey is not set")
    if (keyAlias.isBlank()) issues.add("$keyAliasKey is not set")
    if (keyPassword.isBlank()) issues.add("$keyPasswordKey is not set")
    if (!keystoreFile.exists()) issues.add("Keystore file not found: my-upload-key.jks")
    if (issues.isNotEmpty()) {
      logger.warn("warning Signing configuration issues:")
      issues.forEach { logger.warn("  - $it") }
      logger.warn("Add signing credentials to your local .env file. See .env.example for reference.")
    } else {
      logger.lifecycle("check Signing configuration is valid.")
    }
  }
}

tasks.register<JacocoReport>("jacocoTestReport") {
  dependsOn("testDebugUnitTest")
  executionData.setFrom(fileTree("build/jacoco") { include("*.exec") })
  sourceDirectories.setFrom("src/main/java", "src/main/kotlin")
  classDirectories.setFrom(
    fileTree("build/intermediates/javac/debug/compileDebugJavaWithJavac/classes") { include("**/*.class") },
    fileTree("build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes") { include("**/*.class") }
  )
  reports {
    xml.required = true
    html.required = false
    csv.required = false
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.text.google.fonts)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.hilt.navigation.compose)
  implementation(libs.sqlcipher)
  implementation(libs.biometric)
  implementation("net.java.dev.jna:jna:5.17.0@aar")
  testImplementation("net.java.dev.jna:jna:5.17.0")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  testImplementation(libs.mockwebserver)
  testImplementation("org.json:json:20231013")
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  ksp(libs.androidx.room.compiler)
  ksp(libs.moshi.kotlin.codegen)
}

plugins {
  id("com.diffplug.spotless") version "6.25.0"
}

spotless {
  kotlin {
    target("**/*.kt")
    ktlint()
  }
  kotlinGradle {
    target("*.gradle.kts")
    ktlint()
  }
}

val rustDir = file("${rootProject.projectDir}/rust")
val rustTargetDir = file("${rootProject.projectDir}/rust/target")

data class RustTarget(
  val abi: String,
  val triple: String,
  val jniLib: String,
)

val rustTargets = listOf(
  RustTarget("arm64-v8a", "aarch64-linux-android", "libhesabyar_core.so"),
  RustTarget("armeabi-v7a", "armv7-linux-androideabi", "libhesabyar_core.so"),
  RustTarget("x86_64", "x86_64-linux-android", "libhesabyar_core.so"),
  RustTarget("x86", "i686-linux-android", "libhesabyar_core.so"),
)

rustTargets.forEach { target ->
  val taskName = "assembleRust_${target.abi.replace("-", "_").replace(".", "_")}"
  val outDir = file("$projectDir/src/main/jniLibs/${target.abi}")
  val outputLib = file("$outDir/${target.jniLib}")
  tasks.register(taskName) {
    group = "rust"
    description = "Build Rust .so for ${target.abi}"
    inputs.dir(rustDir.resolve("hesabyar-core/src"))
    inputs.file(rustDir.resolve("hesabyar-core/Cargo.toml"))
    inputs.file(rustDir.resolve("Cargo.toml"))
    inputs.file(rustDir.resolve("Cargo.lock"))
    inputs.file(rustDir.resolve("hesabyar-core/build.rs"))
    outputs.file(outputLib)
    doLast {
      val ndkHome = System.getenv("ANDROID_NDK_HOME")
      if (ndkHome.isNullOrBlank()) {
        throw GradleException(
          "ANDROID_NDK_HOME is not set. Install the Android NDK and set ANDROID_NDK_HOME to its root directory. Example: export ANDROID_NDK_HOME=~/Android/Sdk/ndk/27.0.12077973"
        )
      }
      if (outDir.exists()) outDir.deleteRecursively()
      outDir.mkdirs()
      val cmd = listOf("cargo", "ndk", "-t", target.triple, "-o", outDir.absolutePath, "build", "--release")
      val pb = ProcessBuilder(cmd)
      pb.directory(rustDir)
      pb.inheritIO()
      val exitCode = pb.start().waitFor()
      if (exitCode != 0) throw GradleException("cargo ndk failed for ${target.abi} (exit $exitCode)")
      val foundLib = outDir.walkTopDown().firstOrNull { it.name == target.jniLib }
          ?: throw GradleException("Expected native library ${target.jniLib} not found for ${target.abi} at ${outDir.absolutePath}")
      if (foundLib != outputLib) {
        foundLib.copyTo(outputLib, overwrite = true)
        val nestedDir = File(outDir, target.abi)
        if (nestedDir.exists() && nestedDir != outputLib.parentFile) {
          nestedDir.deleteRecursively()
        }
      }
    }
  }
}

tasks.register("assembleRust") {
  group = "rust"
  description = "Cross-compile Rust shared core for all Android ABIs via cargo-ndk"
  rustTargets.forEach { target ->
    val taskName = "assembleRust_${target.abi.replace("-", "_").replace(".", "_")}"
    dependsOn(taskName)
  }
}

tasks.register("compileRustCore") {
  group = "rust"
  description = "Recompile Rust core before Kotlin compilation when sources change"
  dependsOn("assembleRust")
  inputs.dir(rustDir.resolve("hesabyar-core/src"))
  inputs.file(rustDir.resolve("hesabyar-core/Cargo.toml"))
  inputs.file(rustDir.resolve("Cargo.toml"))
  inputs.file(rustDir.resolve("Cargo.lock"))
  inputs.file(rustDir.resolve("hesabyar-core/build.rs"))
  outputs.dir(file("$projectDir/src/main/jniLibs"))
}

if (System.getenv("ANDROID_NDK_HOME").isNullOrBlank()) {
  logger.lifecycle("ANDROID_NDK_HOME not set skipping automatic Rust cross-compile before Kotlin compilation.")
} else {
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn("compileRustCore")
  }
  tasks.configureEach {
    if (name.contains("merge", ignoreCase = true) && name.contains("JniLibFolders", ignoreCase = true)) {
      dependsOn("compileRustCore")
    }
  }
}

tasks.register("generateRustBindings") {
  group = "rust"
  description = "Generate UniFFI Kotlin bindings from a host-native build"
  inputs.dir(rustDir.resolve("hesabyar-core/src"))
  inputs.file(rustDir.resolve("hesabyar-core/Cargo.toml"))
  inputs.file(rustDir.resolve("Cargo.toml"))
  inputs.file(rustDir.resolve("hesabyar-core/build.rs"))
  val generatedDir = file("$projectDir/src/main/java/${appId.replace(".", "/")}/rust/generated")
  outputs.dir(generatedDir)
  doLast {
    buildHostLibrary()
    val hostLib = resolveHostArtifact()
    generatedDir.mkdirs()
    val exitCode = runUniffiGen(hostLib, generatedDir)
    if (exitCode != 0) throw GradleException("Binding generation failed (exit $exitCode)")
    logger.lifecycle("Kotlin bindings generated at: ${generatedDir.absolutePath}")
  }
}

tasks.register("generateAndFixBindings") {
  group = "rust"
  description = "Generate UniFFI Kotlin bindings, fix package, and install to source tree"
  inputs.dir(rustDir.resolve("hesabyar-core/src"))
  inputs.file(rustDir.resolve("hesabyar-core/Cargo.toml"))
  inputs.file(rustDir.resolve("Cargo.toml"))
  inputs.file(rustDir.resolve("hesabyar-core/build.rs"))
  inputs.file(file("buildSrc/template/HesabyarCore.template.kt"))
  val dest = file("src/main/java/${appId.replace(".", "/")}/rust/hesabyar_core.kt")
  outputs.file(dest)
  doLast {
    buildHostLibrary()
    val hostLib = resolveHostArtifact()
    val tempDir = file("${rootProject.buildDir}/tmp/uniffi-bindings")
    generateBindings(hostLib, tempDir)
    patchAndInstallOutput(tempDir, dest)
    tempDir.deleteRecursively()
    logger.lifecycle("Bindings installed at: ${dest.absolutePath}")
    logger.lifecycle("generateAndFixBindings completed successfully.")
  }
}

tasks.register("generateKeystore") {
  group = "signing"
  description = "Generates a release keystore for signing. Run manually: ./gradlew generateKeystore"
  doFirst {
    val storePassword = resolveCredential(storePwdKey)
    val keyPassword = resolveCredential(keyPasswordKey)
    val keyAlias = providers.gradleProperty(keyAliasKey).orNull
        ?: providers.environmentVariable(keyAliasKey).orNull
        ?: "mojrico"
    if (storePassword.isBlank() || keyPassword.isBlank()) {
      throw GradleException(
        "$storePwdKey and $keyPasswordKey must be set. Add them to your local .env file or set as environment variables. See .env.example for reference."
      )
    }
    val keystoreFile = File(rootDir, "my-upload-key.jks")
    if (!keystoreFile.exists()) {
      println("Generating release keystore...")
      val pb = ProcessBuilder(
        "keytool", "-genkey", "-noprompt", "-alias", keyAlias,
        "-dname", "CN=Hesabyar, OU=None, O=None, L=None, S=None, C=IR",
        "-keystore", keystoreFile.absolutePath,
        "-storepass", storePassword,
        "-keypass", keyPassword,
        "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000"
      )
      val proc = pb.start()
      proc.waitFor()
      println("Keystore generated successfully at: ${keystoreFile.absolutePath}")
    } else {
      println("Keystore already exists, skipping generation.")
    }
  }
}

detekt {
  config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
  baseline = file("$rootDir/config/detekt/detekt-baseline.xml")
  buildUponDefaultConfig = true
  allRules = false
  autoCorrect = false
  ignoredBuildTypes = listOf("release")
}

ktlint {
  filter {
    exclude("**/rust/uniffi/**", "**/generated/**", "**/rust/hesabyar_core.kt")
  }
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
  jvmTarget = "17"
  exclude("**/rust/uniffi/**", "**/generated/**", "**/rust/hesabyar_core.kt")
}
tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
  jvmTarget = "17"
  exclude("**/rust/uniffi/**", "**/generated/**", "**/rust/hesabyar_core.kt")
}
