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
    ":app-tv",
    ":core:data",
    ":core:diagnostics",
    ":core:database",
    ":core:designsystem",
    ":core:domain",
    ":core:i18n",
    ":core:model",
    ":core:navigation",
    ":core:portability",
    ":core:testing",
    ":core:tvcontrol",
    ":feature:dashboard",
    ":feature:analytics",
    ":feature:library",
    ":feature:progress",
    ":feature:privacy",
    ":feature:plans",
    ":feature:practice",
    ":feature:reminders",
    ":feature:reviews",
    ":feature:search",
    ":feature:schedule",
    ":feature:sessions",
    ":feature:settings",
    ":feature:subjects",
    ":feature:workspace",
    ":desktopApp",
)
