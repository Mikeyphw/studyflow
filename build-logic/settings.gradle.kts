pluginManagement {
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

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "studyflow-build-logic"
include(":convention")
