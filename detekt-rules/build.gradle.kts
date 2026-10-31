plugins {
  kotlin("jvm") version "2.4.21"
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
