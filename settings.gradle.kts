pluginManagement {
  repositories {
    maven("https://en-mirror.ir")
    maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
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
    maven("https://en-mirror.ir")
    maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
    google()
    mavenCentral()
  }
}

rootProject.name = "Hesabyar"

include(":app")
