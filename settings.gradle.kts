pluginManagement {
  resolutionStrategy {
    eachPlugin {
      if (requested.id.id == "com.android.application") {
        useModule("com.android.tools.build:gradle:" + requested.version)
      } else if (requested.id.id == "com.google.devtools.ksp") {
        useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:" + requested.version)
      }
    }
  }
  repositories {
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
    google()
    mavenCentral()
  }
}

rootProject.name = "SMS Center"

include(":app")
