pluginManagement {
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("${rootDir}/libs/maven") }
    }
    versionCatalogs {
        create("desktopLibs") { from(files("gradle/desktop.versions.toml")) }
    }
}

rootProject.name = "argosy-launcher"
include(":app")
include(":sigil")
project(":sigil").projectDir = file("sigil/bindings/android")
include(":libretrodroid")
include(":desktopApp")
