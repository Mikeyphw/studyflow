pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// StudyFlow policy: Gradle tasks must execute on GitHub Actions, not locally.
// This is an accidental-execution guard, not an authentication boundary.
if (System.getenv("GITHUB_ACTIONS") != "true" ||
    System.getenv("CI") != "true" ||
    System.getenv("RUNNER_OS") != "Linux") {
    error("STUDYFLOW_GITHUB_ONLY_GRADLE: run Gradle via the GitHub Actions workflows")
}

// OV01 dependency integrity: repositories remain centralized here; project dependency locking is
// enabled in the root build; the canonical integrity refresh materializes SHA-256 verification metadata.
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
    ":core:link",
    ":core:model",
    ":core:navigation",
    ":core:portability",
    ":core:testing",
    ":core:tvcontrol",
    ":core:tvlink",
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
