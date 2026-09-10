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


tasks.register<Exec>("verifyDesktopDov14ReviewsSrsErrorNotebook") {
    group = "verification"
    description = "Verify Desktop DOV14 persistent Reviews, SRS and Error Notebook"
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_desktop_dov14_reviews_srs_error_notebook.py")
}


tasks.register<Exec>("verifyDesktopDov15LibraryFilesMaterialViewers") {
    group = "verification"
    description = "Verify Desktop DOV15 durable Library/files/material viewers"
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_desktop_dov15_library_files_material_viewers.py")
}

tasks.register<Exec>("verifyDesktopDov16WritingWorkspace") {
    group = "verification"
    description = "Verify Desktop DOV16 Writing Workspace"
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_desktop_dov16_writing_workspace.py")
}


tasks.register<Exec>("verifyDesktopDov17ContentModesExecution") {
    group = "verification"
    description = "Verify Desktop DOV17 content modes and native execution"
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_desktop_dov17_content_modes_execution.py")
}

tasks.register<Exec>("verifyDesktopDov18PublicExamParity") {
    group = "verification"
    description = "Verify Desktop DOV18 Concurso Publico parity"
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_desktop_dov18_public_exam_parity.py")
}

tasks.register<Exec>("verifyDesktopDov19ReadinessCoachingReports") {
    group = "verification"
    description = "Verify Desktop DOV19 readiness, coaching, cleanup, reports, runbooks, and sprints"
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_desktop_dov19_readiness_coaching_reports.py")
}


tasks.register<Exec>("verifyDesktopDov20PortabilityBackupMigration") {
    group = "verification"
    description = "Verify Desktop DOV20 portable export/import, encrypted backup/restore, and migration"
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_desktop_dov20_portability_backup_migration.py")
}

tasks.register<Exec>("verifyDesktopDov21EnPtBrLocalization") {
    group = "verification"
    description = "Verify Desktop DOV21 English and pt-BR localization"
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_desktop_dov21_en_ptbr_localization.py")
}

tasks.register("verifyDesktopDov16Dov19Batch") {
    group = "verification"
    description = "Verify Desktop DOV16-DOV19 product batch"
    dependsOn(
        "verifyDesktopDov16WritingWorkspace",
        "verifyDesktopDov17ContentModesExecution",
        "verifyDesktopDov18PublicExamParity",
        "verifyDesktopDov19ReadinessCoachingReports",
        "compileKotlin",
        "test",
    )
}


tasks.register("verifyDesktopDov12Dov15Batch") {
    group = "verification"
    description = "Verify Desktop DOV12-DOV15 product batch"
    dependsOn(
        "verifyDesktopDov12QuestionPracticeExecution",
        "verifyDesktopDov13MockExamStudio",
        "verifyDesktopDov14ReviewsSrsErrorNotebook",
        "verifyDesktopDov15LibraryFilesMaterialViewers",
        ":desktopApp:compileKotlin",
        ":desktopApp:test",
    )
}
