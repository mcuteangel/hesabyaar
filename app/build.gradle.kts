plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.hilt)
  jacoco
}

android {
  namespace = "io.github.mojri.hesabyar"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "io.github.mojri.hesabyar"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "io.github.mojri.hesabyar.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      storeFile = file("$rootDir/my-upload-key.jks")
      storePassword = providers.gradleProperty("KEYSTORE_PASSWORD").orNull
        ?: providers.environmentVariable("KEYSTORE_PASSWORD").orNull
        ?: ""
      keyAlias = providers.gradleProperty("KEY_ALIAS").orNull
        ?: providers.environmentVariable("KEY_ALIAS").orNull
        ?: ""
      keyPassword = providers.gradleProperty("KEY_PASSWORD").orNull
        ?: providers.environmentVariable("KEY_PASSWORD").orNull
        ?: ""
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
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

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

tasks.register("checkSigningConfig") {
  group = "signing"
  description = "Validates that release signing credentials are configured"
  doFirst {
    val storePassword =
      providers.gradleProperty("KEYSTORE_PASSWORD").orNull
        ?: providers.environmentVariable("KEYSTORE_PASSWORD").orNull
        ?: ""
    val keyAlias =
      providers.gradleProperty("KEY_ALIAS").orNull
        ?: providers.environmentVariable("KEY_ALIAS").orNull
        ?: ""
    val keyPassword =
      providers.gradleProperty("KEY_PASSWORD").orNull
        ?: providers.environmentVariable("KEY_PASSWORD").orNull
        ?: ""
    val keystoreFile = file("$rootDir/my-upload-key.jks")

    val issues = mutableListOf<String>()
    if (storePassword.isBlank()) issues.add("KEYSTORE_PASSWORD is not set")
    if (keyAlias.isBlank()) issues.add("KEY_ALIAS is not set")
    if (keyPassword.isBlank()) issues.add("KEY_PASSWORD is not set")
    if (!keystoreFile.exists()) issues.add("Keystore file not found: my-upload-key.jks")

    if (issues.isNotEmpty()) {
      logger.warn("⚠ Signing configuration issues:")
      issues.forEach { logger.warn("  - $it") }
      logger.warn("Add signing credentials to your local .env file. See .env.example for reference.")
    } else {
      logger.lifecycle("✓ Signing configuration is valid.")
    }
  }
}

tasks.register<JacocoReport>("jacocoTestReport") {
  dependsOn("testDebugUnitTest")
  executionData.setFrom(fileTree("build/jacoco") { include("*.exec") })
  sourceDirectories.setFrom("src/main/java", "src/main/kotlin")
  classDirectories.setFrom(
    fileTree("build/intermediates/javac/debug/compileDebugJavaWithJavac/classes") { include("**/*.class") },
    fileTree("build/tmp/kotlin-classes/debug") { include("**/*.class") }
  )
  reports {
    xml.required = true
    html.required = false
    csv.required = false
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.text.google.fonts)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.hilt.navigation.compose)
  implementation(libs.sqlcipher)
  implementation(libs.biometric)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

ktlint {
  android = true
}

tasks.register("generateKeystore") {
  group = "signing"
  description = "Generates a release keystore for signing. Run manually: ./gradlew generateKeystore"
  doFirst {
    val storePassword =
      providers.gradleProperty("KEYSTORE_PASSWORD").orNull
        ?: providers.environmentVariable("KEYSTORE_PASSWORD").orNull
        ?: ""
    val keyPassword =
      providers.gradleProperty("KEY_PASSWORD").orNull
        ?: providers.environmentVariable("KEY_PASSWORD").orNull
        ?: ""
    val keyAlias =
      providers.gradleProperty("KEY_ALIAS").orNull
        ?: providers.environmentVariable("KEY_ALIAS").orNull
        ?: "mojrico"
    if (storePassword.isBlank() || keyPassword.isBlank()) {
      throw GradleException(
        "KEYSTORE_PASSWORD and KEY_PASSWORD must be set.\n" +
          "Add them to your local .env file or set as environment variables.\n" +
          "See .env.example for reference."
      )
    }
    val keystoreFile = File(rootDir, "my-upload-key.jks")
    if (!keystoreFile.exists()) {
      println("Generating release keystore...")
      val pb =
        ProcessBuilder(
          "keytool", "-genkey", "-noprompt",
          "-alias", keyAlias,
          "-dname", "CN=Hesabyar, OU=None, O=None, L=None, S=None, C=IR",
          "-keystore", keystoreFile.absolutePath,
          "-storepass", storePassword,
          "-keypass", keyPassword,
          "-keyalg", "RSA",
          "-keysize", "2048",
          "-validity", "10000"
        )
      val proc = pb.start()
      proc.waitFor()
      println("Keystore generated successfully at: ${keystoreFile.absolutePath}")
    } else {
      println("Keystore already exists, skipping generation.")
    }
  }
}
