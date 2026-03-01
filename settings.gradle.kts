pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    
    // Добавить версии плагинов
    plugins {
        id("com.android.application") version "8.1.4"
        id("com.android.library") version "8.1.4"
        id("org.jetbrains.kotlin.android") version "1.9.20"
        id("org.jetbrains.kotlin.jvm") version "1.9.20"
        id("org.jetbrains.kotlin.plugin.serialization") version "1.9.20"
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

include(":core:drawing2d")
include(":core:domain")
include(":core:validation")
