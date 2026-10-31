plugins {
  // No version: org.jetbrains.kotlin.jvm is already on the classpath via
  // the root kotlin.compose plugin; a versioned request fails CI with
  // "already on the classpath with an unknown version" (head 35c35c23).
  kotlin("jvm")
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation("dev.detekt:detekt-api:2.0.0-alpha.6")
  testImplementation("dev.detekt:detekt-test:2.0.0-alpha.6")
  testImplementation("dev.detekt:detekt-test-utils:2.0.0-alpha.6")
  testImplementation(libs.junit)
}
