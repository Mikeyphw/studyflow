plugins {
    `kotlin-dsl`
}

group = "com.mikeyphw.studyflow.buildlogic"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation("com.android.tools.build:gradle:9.3.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.10")
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "studyflow.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "studyflow.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "studyflow.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "studyflow.kotlin.library"
            implementationClass = "KotlinLibraryConventionPlugin"
        }
    }
}
