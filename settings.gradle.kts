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
    ":core:database",
    ":core:designsystem",
    ":core:domain",
    ":core:i18n",
    ":core:model",
    ":core:navigation",
    ":core:portability",
    ":core:testing",
    ":feature:dashboard",
    ":feature:library",
    ":feature:plans",
    ":feature:practice",
    ":feature:reviews",
    ":feature:schedule",
    ":feature:sessions",
    ":feature:settings",
    ":feature:subjects",
)
