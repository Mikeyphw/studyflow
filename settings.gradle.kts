pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "StudyFlow"

include(
    ":app",
    ":core:data",
    ":core:designsystem",
    ":core:domain",
    ":core:i18n",
    ":core:model",
    ":core:navigation",
    ":core:testing",
    ":feature:dashboard",
    ":feature:plans",
    ":feature:settings",
)
