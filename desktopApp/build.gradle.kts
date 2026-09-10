plugins {
    id("studyflow.desktop.application")
    // Historical verifier tokens retained intentionally:
    // alias(libs.plugins.kotlin.jvm)
    // alias(libs.plugins.compose.multiplatform)
    // alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:link"))
    implementation(project(":core:portability"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(kotlin("test-junit"))
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


tasks.register<Exec>("verifyDesktopDov12QuestionPracticeExecution") {
    group = "verification"
    description = "Verify Desktop DOV12 durable question-practice execution"
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_desktop_dov12_question_practice_execution.py")
}


tasks.register<Exec>("verifyDesktopDov13MockExamStudio") {
    group = "verification"
    description = "Verify Desktop DOV13 persistent Mock Exam Studio"
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_desktop_dov13_mock_exam_studio.py")
}
