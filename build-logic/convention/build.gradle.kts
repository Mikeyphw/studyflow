plugins {
    `kotlin-dsl`
}

group = "com.mikeyphw.studyflow.buildlogic"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation("com.android.tools.build:gradle:9.3.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.4.10")
    implementation("org.jetbrains.compose:compose-gradle-plugin:1.9.3")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.4.10")
    implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.11")
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
        register("androidLibraryRoom") {
            id = "studyflow.android.library.room"
            implementationClass = "AndroidLibraryRoomConventionPlugin"
        }
        register("desktopApplication") {
            id = "studyflow.desktop.application"
            implementationClass = "DesktopApplicationConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "studyflow.kotlin.library"
            implementationClass = "KotlinLibraryConventionPlugin"
        }
        register("kotlinLibrarySerialization") {
            id = "studyflow.kotlin.library.serialization"
            implementationClass = "KotlinLibrarySerializationConventionPlugin"
        }
    }
}


// OV01: included build dependencies are locked independently from the main build.
dependencyLocking {
    lockAllConfigurations()
}
