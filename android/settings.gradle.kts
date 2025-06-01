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
    }
}

rootProject.name = "MMCP-Android"
include(":example")
include(":mmcp-client-android")
include(":mmcp-server-android")
includeBuild("../shared/MMCPCore") {
    dependencySubstitution {
        substitute(module("io.rosenpin.mcp:mmcp")).using(project(":mmcp"))
    }
}
include(":phonemcpserver")
