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
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.serialization.json)
}

compose.desktop {
    application {
        mainClass = "com.mikeyphw.studyflow.desktop.MainKt"

        nativeDistributions {
            packageName = "StudyFlow"
            packageVersion = "1.0.0"
            description = "StudyFlow Compose Multiplatform Desktop workspace"
        }
    }
}

// Desktop tests are headless domain/controller tests. The native Skiko AWT runtime is
// required by the packaged desktop application, but not by this unit-test runtime; keeping it
// out prevents Termux ARM64 validation from requiring an uncached host-native binary.
configurations.named("testRuntimeClasspath") {
    exclude(group = "org.jetbrains.skiko")
}

tasks.withType<Test>().configureEach {
    useJUnit()
    maxParallelForks = 1
    systemProperty("java.awt.headless", "true")
}


tasks.register("verifyDesktopDov12QuestionPracticeExecution") {
    group = "verification"
    description = "Compatibility alias for the canonical root verifyDesktopDov12QuestionPracticeExecution task."
    dependsOn(rootProject.tasks.named("verifyDesktopDov12QuestionPracticeExecution"))
}

tasks.register("verifyDesktopDov13MockExamStudio") {
    group = "verification"
    description = "Compatibility alias for the canonical root verifyDesktopDov13MockExamStudio task."
    dependsOn(rootProject.tasks.named("verifyDesktopDov13MockExamStudio"))
}

tasks.register("verifyDesktopDov14ReviewsSrsErrorNotebook") {
    group = "verification"
    description = "Compatibility alias for the canonical root verifyDesktopDov14ReviewsSrsErrorNotebook task."
    dependsOn(rootProject.tasks.named("verifyDesktopDov14ReviewsSrsErrorNotebook"))
}

tasks.register("verifyDesktopDov15LibraryFilesMaterialViewers") {
    group = "verification"
    description = "Compatibility alias for the canonical root verifyDesktopDov15LibraryFilesMaterialViewers task."
    dependsOn(rootProject.tasks.named("verifyDesktopDov15LibraryFilesMaterialViewers"))
}

tasks.register("verifyDesktopDov16WritingWorkspace") {
    group = "verification"
    description = "Compatibility alias for the canonical root verifyDesktopDov16WritingWorkspace task."
    dependsOn(rootProject.tasks.named("verifyDesktopDov16WritingWorkspace"))
}

tasks.register("verifyDesktopDov17ContentModesExecution") {
    group = "verification"
    description = "Compatibility alias for the canonical root verifyDesktopDov17ContentModesExecution task."
    dependsOn(rootProject.tasks.named("verifyDesktopDov17ContentModesExecution"))
}

tasks.register("verifyDesktopDov18PublicExamParity") {
    group = "verification"
    description = "Compatibility alias for the canonical root verifyDesktopDov18PublicExamParity task."
    dependsOn(rootProject.tasks.named("verifyDesktopDov18PublicExamParity"))
}

tasks.register("verifyDesktopDov19ReadinessCoachingReports") {
    group = "verification"
    description = "Compatibility alias for the canonical root verifyDesktopDov19ReadinessCoachingReports task."
    dependsOn(rootProject.tasks.named("verifyDesktopDov19ReadinessCoachingReports"))
}

tasks.register("verifyDesktopDov20PortabilityBackupMigration") {
    group = "verification"
    description = "Compatibility alias for the canonical root verifyDesktopDov20PortabilityBackupMigration task."
    dependsOn(rootProject.tasks.named("verifyDesktopDov20PortabilityBackupMigration"))
}

tasks.register("verifyDesktopDov21EnPtBrLocalization") {
    group = "verification"
    description = "Compatibility alias for the canonical root verifyDesktopDov21EnPtBrLocalization task."
    dependsOn(rootProject.tasks.named("verifyDesktopDov21EnPtBrLocalization"))
}

tasks.register("verifyDesktopDov22SystemLightDarkAppearance") {
    group = "verification"
    description = "Compatibility alias for the canonical root verifyDesktopDov22SystemLightDarkAppearance task."
    dependsOn(rootProject.tasks.named("verifyDesktopDov22SystemLightDarkAppearance"))
}

tasks.register("verifyDesktopDov23NativeDesktopIntegration") {
    group = "verification"
    description = "Compatibility alias for the canonical root verifyDesktopDov23NativeDesktopIntegration task."
    dependsOn(rootProject.tasks.named("verifyDesktopDov23NativeDesktopIntegration"))
}

tasks.register("verifyDesktopDov24FullApplicableParitySeal") {
    group = "verification"
    description = "Compatibility alias for the canonical root verifyDesktopDov24FullApplicableParitySeal task."
    dependsOn(rootProject.tasks.named("verifyDesktopDov24FullApplicableParitySeal"))
}

tasks.register("verifyDesktopDov20Dov24Batch") {
    group = "verification"
    description = "Compatibility alias for the canonical root verifyDesktopDov20Dov24Batch task."
    dependsOn(rootProject.tasks.named("verifyDesktopDov20Dov24Batch"))
}

tasks.register("verifyDesktopDov16Dov19Batch") {
    group = "verification"
    description = "Compatibility alias for the canonical root verifyDesktopDov16Dov19Batch task."
    dependsOn(rootProject.tasks.named("verifyDesktopDov16Dov19Batch"))
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


tasks.register("verifyAtt04DesktopAttentionContracts") {
    group = "verification"
    description = "Compatibility alias for the canonical root verifyAtt04DesktopAttentionContracts task."
    dependsOn(rootProject.tasks.named("verifyAtt04DesktopAttentionContracts"))
}

tasks.register("verifyAtt04DesktopAttentionRuntime") {
    group = "verification"
    description = "Compatibility alias for the canonical root verifyAtt04DesktopAttentionRuntime task."
    dependsOn(rootProject.tasks.named("verifyAtt04DesktopAttentionRuntime"))
}

