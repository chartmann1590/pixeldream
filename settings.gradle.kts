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

rootProject.name = "PixelDream"

include(":app")
include(":core-model")
include(":core-data")
include(":core-billing")
include(":core-ui")
include(":stablediffusion")
