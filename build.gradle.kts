// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.ktlint) apply false
  alias(libs.plugins.hilt) apply false
  alias(libs.plugins.detekt) apply false
}

tasks.register("copyGitHooks") {
  description = "Copies git hooks from scripts/ to .git/hooks/"
  doLast {
    val hooksDir = file("${rootDir}/.git/hooks")
    listOf("pre-commit", "pre-push").forEach { name ->
      val hook = file("${rootDir}/scripts/$name")
      if (hook.exists()) {
        copy {
          from(hook)
          into(hooksDir)
        }
        file("${hooksDir}/$name").setExecutable(true)
        logger.lifecycle("✓ $name hook installed")
      } else {
        logger.warn("⚠ scripts/$name not found, skipping hook installation")
      }
    }
  }
}

afterEvaluate {
  tasks.matching {
    it.name.contains("prepare", ignoreCase = true) || it.name == "preBuild"
  }.configureEach {
    dependsOn("copyGitHooks")
  }
  // The :app module is a separate Gradle project: its own preBuild/prepare
  // tasks are invisible to the root tasks.matching above, so wire them here.
  // Use findProject to avoid hard failure if module is renamed or restructured.
  findProject(":app")?.let { appProject ->
    appProject.tasks.matching {
      it.name.contains("prepare", ignoreCase = true) || it.name == "preBuild"
    }.configureEach {
      dependsOn(rootProject.tasks.named("copyGitHooks"))
    }
  }
}
