plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:portability"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
}

application {
    mainClass.set("com.mikeyphw.studyflow.desktop.MainKt")
}

compose.desktop {
    application {
        mainClass = "com.mikeyphw.studyflow.desktop.MainKt"

        nativeDistributions {
            packageName = "StudyFlow"
            packageVersion = "0.1.0"
            description = "StudyFlow Compose Multiplatform Desktop preview shell"
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
