pluginManagement {
  repositories {
    // Iranian/Chinese mirrors for local builds where google()/mavenCentral()
    // are unreachable. Opt-in only (HESABYAR_USE_MIRRORS=1): when listed first
    // unconditionally, Gradle takes a module's POM from the mirror and then
    // fails because the mirror lacks the JAR/AAR (#389).
    // A settings script cannot share this check with the
    // dependencyResolutionManagement block, so the line is repeated there.
    val useMirrors =
      System.getenv("HESABYAR_USE_MIRRORS")?.lowercase() in setOf("1", "true", "yes")
    if (useMirrors) {
      maven("https://en-mirror.ir")
      maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
    }
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    // Same mirror opt-in as pluginManagement. See the comment there for why.
    val useMirrors =
      System.getenv("HESABYAR_USE_MIRRORS")?.lowercase() in setOf("1", "true", "yes")
    if (useMirrors) {
      maven("https://en-mirror.ir")
      maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
    }
    google()
    mavenCentral()
  }
}

rootProject.name = "Hesabyar"

include(":app")
