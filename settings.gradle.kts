pluginManagement {
    repositories {
        google()
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

rootProject.name = "2d3d"

include(":app")
include(":core:drawing2d")
include(":core:domain")
include(":core:storage")
include(":feature:camera")
include(":feature:editor")
include(":feature:ar")
