import org.gradle.api.tasks.PathSensitivity

tasks.register<Exec>("verifyStableToolchainBaseline") {
    group = "verification"
    description = "Checks the shared stable Gradle, Android, Kotlin, JVM, SDK, wrapper, and dependency baseline."
    commandLine("python3", "scripts/verify_stable_toolchain_baseline.py")
}

tasks.register<Exec>("verifyFormatting") {
    group = "verification"
    description = "Checks source files for tabs, trailing whitespace, missing final newlines, and overlong Kotlin lines."
    commandLine("python3", "scripts/verify_formatting.py")
}

tasks.register<Exec>("verifyCpov00Toolchain") {
    group = "verification"
    description = "Checks CPOV00 Gradle >= 9.7 and canonical NDK r29 toolchain contracts."
    commandLine("python3", "scripts/verify_cpov00_toolchain.py")
}

tasks.register<Exec>("verifyCpov01PublicExamProfile") {
    group = "verification"
    description = "Checks CPOV01 public-exam profile shell, ownership, onboarding and locale contracts."
    commandLine("python3", "scripts/verify_cpov01_public_exam_profile.py")
}

tasks.register<Exec>("verifyCpov02EditalImport") {
    group = "verification"
    description = "Checks CPOV02 edital parsing, preview-first apply, local document lanes and locale contracts."
    commandLine("python3", "scripts/verify_cpov02_edital_import.py")
}

tasks.register<Exec>("verifyCpov03Cpov04PublicExamProgress") {
    group = "verification"
    description = "Checks CPOV03 edital revisions and CPOV04 persistent study-cycle contracts."
    commandLine("python3", "scripts/verify_cpov03_04_public_exam_progress.py")
}

val historicalRepositoryContractTasks = (0..37).map { phase ->
    tasks.register<Exec>("verifyHistoricalPhase${phase.toString().padStart(2, '0')}") {
        group = "verification"
        description = "Runs the historical Phase $phase repository contract."
        commandLine("python3", "scripts/verify_phase_${phase}.py")
    }
}

val repositoryContractVerifierScripts = linkedMapOf(
    "verifyHistoricalDebugWorkbenchD1R2" to "scripts/verify_debug_workbench_d1_r2.py",
    "verifyHistoricalDebugWorkbenchD2" to "scripts/verify_debug_workbench_d2.py",
    "verifyHistoricalDebugWorkbenchD3" to "scripts/verify_debug_workbench_d3.py",
    "verifyHistoricalDebugWorkbenchD2D3CorrectiveSeal" to "scripts/verify_debug_workbench_d2_d3_corrective_seal.py",
    "verifyHistoricalDebugWorkbenchD4" to "scripts/verify_debug_workbench_d4.py",
    "verifyHistoricalDebugWorkbenchD5" to "scripts/verify_debug_workbench_d5.py",
    "verifyHistoricalDebugWorkbenchD6" to "scripts/verify_debug_workbench_d6.py",
    "verifyHistoricalDebugWorkbenchD7" to "scripts/verify_debug_workbench_d7.py",
    "verifyHistoricalDebugWorkbenchD8" to "scripts/verify_debug_workbench_d8.py",
    "verifyHistoricalUiUxV2" to "scripts/verify_ui_ux_v2.py",
    "verifyHistoricalPhase48_49" to "scripts/verify_phase_48_49.py",
    "verifyHistoricalPhase50_51" to "scripts/verify_phase_50_51.py",
    "verifyHistoricalPhase52_53" to "scripts/verify_phase_52_53.py",
    "verifyHistoricalPhase54_55" to "scripts/verify_phase_54_55.py",
    "verifyHistoricalFullAppReadiness" to "scripts/verify_full_app_final_user_readiness.py",
    "verifyHistoricalRemediationPhase1_2" to "scripts/verify_remediation_phase_1_2.py",
    "verifyHistoricalFinalReleaseVerdictContract" to "scripts/verify_final_release_verdict_contract.py",
)

val repositoryContractVerifierTasks = repositoryContractVerifierScripts.map { (taskName, scriptPath) ->
    tasks.register<Exec>(taskName) {
        group = "verification"
        description = "Runs $scriptPath as an individually owned repository contract."
        commandLine("python3", scriptPath)
    }
}

tasks.register("verifyArchivedRepositoryContracts") {
    group = "verification"
    description = "Runs superseded historical validators explicitly for archival/regression audits."
    dependsOn(historicalRepositoryContractTasks)
    dependsOn(repositoryContractVerifierTasks)
    dependsOn(
        "verifyPhase56_57ReleaseReadiness",
        "verifyPhase58_59WarningHygiene",
        "verifyPhase60_61ContentReportSeal",
        "verifyPhase62OnboardingSeal",
        "verifyPhase63_64ExamRunbookSprintSeal",
    )
}

tasks.register("verifyRepositoryContracts") {
    group = "verification"
    description =
        "Aggregates historical contracts without hiding duplicate verifier execution inside a shell process."
    dependsOn("verifyStableToolchainBaseline", "verifyCpov00Toolchain")
    // Historical phase/debug validators remain available as individually-invoked
    // archival tasks, but do not block the active repository contract gate.
    dependsOn(
        "verifyAndroidFocusShellFinalSeal",
        "verifyPostPhase64FinalValidationRefresh",
        "verifyFinalSourceAcceptance",
        "verifyOv26SpecializedContentDataUi",
        "verifyOv27TermuxExecutionPipelineSandboxCorrectness",
        "verifyOv28LanguageReviewDesktopSpecializedParity",
        "verifyOv29BehavioralValidationReleaseTruthGates",
        "verifyRemediationOv13",
    )
}

tasks.register<Exec>("verifyPhase56_57ReleaseReadiness") {
    group = "verification"
    description = "Checks release-facing StudyFlow source gates that do not require signing credentials."
    commandLine("python3", "scripts/verify_phase_56_57.py")
}


tasks.register<Exec>("verifyPhase58_59WarningHygiene") {
    group = "verification"
    description = "Checks Phase 58/59 warning cleanup boundaries before Android lint runs."
    commandLine("python3", "scripts/verify_phase_58_59.py")
}

tasks.register<Exec>("verifyPhase60_61ContentReportSeal") {
    group = "verification"
    description = "Checks Phase 60 smart cleanup, Phase 61 local report export, and warning cleanup."
    commandLine("python3", "scripts/verify_phase_60_61.py")
}


tasks.register<Exec>("verifyPhase62OnboardingSeal") {
    group = "verification"
    description = "Checks Phase 62 onboarding and local sample plan wizard behavior."
    commandLine("python3", "scripts/verify_phase_62.py")
}


tasks.register<Exec>("verifyPhase63_64ExamRunbookSprintSeal") {
    group = "verification"
    description = "Checks Phase 63 exam runbook and Phase 64 preview-first study sprint behavior."
    commandLine("python3", "scripts/verify_phase_63_64.py")
}


tasks.register<Exec>("verifyPostPhase64FinalValidationRefresh") {
    group = "verification"
    description = "Checks Phase 64 roadmap reconciliation and final signed-journey coverage."
    commandLine("python3", "scripts/verify_post_phase_64_final_validation.py")
}


tasks.register<Exec>("verifyAndroidFocusShellFinalSeal") {
    group = "verification"
    description = "Checks the Android UXOV-01 through UXOV-05-R2 Focus Shell and polish source contracts."
    commandLine("python3", "scripts/verify_android_focus_shell_r2.py")
}

tasks.register<Exec>("verifyCloudOptionalSyncSettings") {
    group = "verification"
    description = "Checks CSOV00/CSOV01 local-first optional cloud sync source contracts."
    commandLine("python3", "scripts/verify_cloud_optional_sync_settings.py")
}


tasks.register<Exec>("verifyCsov02SyncBundle") {
    group = "verification"
    description = "Checks CSOV02 encrypted sync checkpoint bundle source contracts."
    commandLine("python3", "scripts/verify_csov02_sync_bundle.py")
}

tasks.register<Exec>("verifyCsov03ProviderAbstraction") {
    group = "verification"
    description = "Checks CSOV03 optional sync provider abstraction source contracts."
    commandLine("python3", "scripts/verify_csov03_provider_abstraction.py")
}


tasks.register<Exec>("verifyCsov04OptionalSyncOnboarding") {
    group = "verification"
    description = "Checks CSOV04 optional sync onboarding source contracts."
    commandLine("python3", "scripts/verify_csov04_optional_sync_onboarding.py")
}


tasks.register<Exec>("verifyCsov05ManualFolderSync") {
    group = "verification"
    description = "Checks CSOV05 manual folder sync source contracts."
    commandLine("python3", "scripts/verify_csov05_manual_folder_sync.py")
}


tasks.register<Exec>("verifyCsov06SyncLedger") {
    group = "verification"
    description = "Checks CSOV06 sync device identity and ledger source contracts."
    commandLine("python3", "scripts/verify_csov06_sync_ledger.py")
}


tasks.register<Exec>("verifyCsov07MergeEngine") {
    group = "verification"
    description = "Checks CSOV07 conservative sync merge engine source contracts."
    commandLine("python3", "scripts/verify_csov07_merge_engine.py")
}


tasks.register<Exec>("verifyCsov08ConflictReview") {
    group = "verification"
    description = "Checks CSOV08 conflict review UI and presenter source contracts."
    commandLine("python3", "scripts/verify_csov08_conflict_review.py")
}


tasks.register<Exec>("verifyCsov09BackgroundSyncModes") {
    group = "verification"
    description = "Checks CSOV09 background sync mode source contracts."
    commandLine("python3", "scripts/verify_csov09_background_sync_modes.py")
}

tasks.register<Exec>("verifyCsov10RealProviders") {
    group = "verification"
    description = "Checks CSOV10 WebDAV / Nextcloud real provider lane source contracts."
    commandLine("python3", "scripts/verify_csov10_real_providers.py")
}

tasks.register<Exec>("verifyCsov11FinalSyncSeal") {
    group = "verification"
    description = "Checks CSOV11 local-first optional cloud final acceptance source contracts."
    commandLine("python3", "scripts/verify_csov11_final_sync_seal.py")
}

tasks.register<Exec>("verifyContentModeFoundation") {
    group = "verification"
    description = "Checks CMOV-01 content mode persistence, UI, backup and capability contracts."
    commandLine("python3", "scripts/verify_content_mode_foundation.py")
}




tasks.register<Exec>("verifyProgrammingCourseModel") {
    group = "verification"
    description = "Checks programming-course data model, persistence, and Termux preference contracts."
    commandLine("python3", "scripts/verify_programming_course_model.py")
}



tasks.register<Exec>("verifyCodeBlocksSyntax") {
    group = "verification"
    description = "Checks CMOV-03 reusable code block and syntax-highlighting contracts."
    commandLine("python3", "scripts/verify_code_blocks_syntax.py")
}

tasks.register<Exec>("verifyTermuxRuntimeFoundation") {
    group = "verification"
    description = "Checks CMOV-04 safe Termux runtime request/result and command-template contracts."
    commandLine("python3", "scripts/verify_termux_runtime_foundation.py")
}

tasks.register<Exec>("verifyProgrammingCourseUi") {
    group = "verification"
    description = "Checks CMOV-05 programming-course UI labels, workspace panel and code-block integration."
    commandLine("python3", "scripts/verify_programming_course_ui.py")
}

tasks.register<Exec>("verifyCodingExerciseTermuxExecution") {
    group = "verification"
    description = "Checks CMOV-06 coding-exercise Termux execution planning and result contracts."
    commandLine("python3", "scripts/verify_coding_exercise_termux_execution.py")
}

tasks.register<Exec>("verifyLanguageDeckIdiomModel") {
    group = "verification"
    description = "Checks CMOV-07 language deck, expression, idiom and vocabulary model contracts."
    commandLine("python3", "scripts/verify_language_deck_idiom_model.py")
}

tasks.register<Exec>("verifyLanguageReviewModes") {
    group = "verification"
    description = "Checks CMOV-08 language review prompts, evaluation and spaced-review bridge contracts."
    commandLine("python3", "scripts/verify_language_review_modes.py")
}

tasks.register<Exec>("verifyImportTemplates") {
    group = "verification"
    description = "Checks CMOV-09 programming-course and language idiom import template contracts."
    commandLine("python3", "scripts/verify_import_templates.py")
}


tasks.register<Exec>("verifyModeAwareAnalytics") {
    group = "verification"
    description = "Checks CMOV-10 mode-aware analytics profiles, labels and UI routing."
    commandLine("python3", "scripts/verify_mode_aware_analytics.py")
}


tasks.register<Exec>("verifyContentModesFinalIntegrationSeal") {
    group = "verification"
    description = "Checks the audited CMOV-01..CMOV-10 integration seal, including Android Termux bridge readiness."
    commandLine("python3", "scripts/verify_content_modes_final_integration.py")
}

tasks.register<Exec>("verifyFinalSourceAcceptance") {
    group = "verification"
    description = "Checks source/static final acceptance only; it does not claim release artifact or device acceptance."
    commandLine("python3", "scripts/verify_final_app_acceptance.py")
}

// Final host gate fails closed when signing inputs are absent: missingSigningVariables.
// Connected Room migration evidence gate: "core_database_migrations".
val studyFlowReleaseSigningVariables = listOf(
    "STUDYFLOW_RELEASE_STORE_FILE",
    "STUDYFLOW_RELEASE_STORE_PASSWORD",
    "STUDYFLOW_RELEASE_KEY_ALIAS",
    "STUDYFLOW_RELEASE_KEY_PASSWORD",
)

tasks.register<Delete>("prepareDevtoolValidationArtifacts") {
    group = "verification"
    description =
        "Removes stale unsigned Release outputs before ordinary Devtool validation when signing is unavailable."
    onlyIf { studyFlowReleaseSigningVariables.any { name -> System.getenv(name).isNullOrBlank() } }
    delete(
        layout.projectDirectory.dir("app/build/outputs/apk/release"),
        layout.projectDirectory.dir("app/build/outputs/bundle/release"),
    )
}

tasks.register<Exec>("verifyOv19PromiseAudit") {
    group = "verification"
    description = "Checks the OV19 public-exam promise audit and backlog-closure contracts."
    commandLine("python3", "scripts/verify_ov19_promise_audit.py")
}

tasks.register<Exec>("verifyOv20FinalIntegration") {
    group = "verification"
    description = "Runs the fail-closed OV20 public-exam integration/source seal."
    commandLine("python3", "scripts/verify_ov20_final_integration.py")
}


tasks.register<Exec>("verifyStudyFlowTvArchitecture") {
    group = "verification"
    description = "Checks TVOV00 shared-core, app-tv boundary, and Devtool TV target contracts."
    commandLine("python3", "scripts/verify_tvov00_architecture.py")
}

tasks.register<Exec>("verifyStudyFlowTvSizeBudget") {
    group = "verification"
    description = "Checks TV size-budget, dependency-boundary, and placeholder target contracts."
    commandLine("python3", "scripts/verify_tv_size_budget.py")
}

tasks.register<Exec>("verifyStudyFlowTvControlCast") {
    group = "verification"
    description = "Checks phone-to-TV control and cast boundary contracts."
    commandLine("python3", "scripts/verify_tv_control_cast_contract.py")
}


tasks.register<Exec>("verifyStudyFlowTvAppModule") {
    group = "verification"
    description = "Checks TVOV01 minimal app-tv module, leanback manifest, and size-first dependency boundary."
    commandLine("python3", "scripts/verify_tvov01_app_module.py")
}

tasks.register<Exec>("verifyStudyFlowTvDependencyBudget") {
    group = "verification"
    description = "Checks TVOV02 app-tv dependency budget, lint-clean manifest, and size-safe resources."
    commandLine("python3", "scripts/verify_tvov02_dependency_budget.py")
}

tasks.register<Exec>("verifyStudyFlowTvPairingControlCastProtocol") {
    group = "verification"
    description = "Checks TVOV03 shared phone-to-TV pairing, control, and cast protocol contracts."
    commandLine("python3", "scripts/verify_tvov03_pairing_control_cast_protocol.py")
}

tasks.register<Exec>("verifyStudyFlowTvFocusPrimitives") {
    group = "verification"
    description = "Checks TVOV04 native Android TV D-pad/focus primitive contracts."
    commandLine("python3", "scripts/verify_tvov04_focus_primitives.py")
}


tasks.register<Exec>("verifyStudyFlowTvNavigationShell") {
    group = "verification"
    description = "Checks TVOV05 Android TV navigation shell, destination model, and D-pad-aware rail contracts."
    commandLine("python3", "scripts/verify_tvov05_navigation_shell.py")
}

tasks.register<Exec>("verifyStudyFlowTvHomeSurface") {
    group = "verification"
    description = "Checks TVOV06 Android TV Home surface, warning cleanup, and paired-phone entry contracts."
    commandLine("python3", "scripts/verify_tvov06_home_surface.py")
}


tasks.register<Exec>("verifyStudyFlowTvTodayWorkspace") {
    group = "verification"
    description = "Checks TVOV07 Android TV Today workspace, controls, focus items, and shell rendering contracts."
    commandLine("python3", "scripts/verify_tvov07_today_workspace.py")
}

tasks.register<Exec>("verifyStudyFlowTvStudySessionExperience") {
    group = "verification"
    description = "Checks TVOV08 Android TV study-session experience, remote/phone controls, and cast-state contracts."
    commandLine("python3", "scripts/verify_tvov08_study_session.py")
}

tasks.register("verifyFinalAppAcceptanceSeal") {
    group = "verification"
    description = "Compatibility alias for the canonical final source/static acceptance contract."
    dependsOn("verifyFinalSourceAcceptance")
}

tasks.register<Exec>("verifyRemediationOv13") {
    group = "verification"
    description = "Checks OV-13 release-seal source contracts without synthesizing runtime evidence."
    commandLine("python3", "scripts/verify_remediation_ov_13.py")
}

tasks.register<Exec>("verifyFinalReleaseHostGate") {
    group = "verification"
    description = "Runs the signed host-side Release regression gate and records APK/AAB evidence."
    dependsOn(
        "checkAll",
        ":app:verifyPhoneReleaseSigningPreflight",
        ":app:compileReleaseAndroidTestKotlin",
        ":app:processReleaseResources",
        ":app:assembleRelease",
        ":app:bundleRelease",
    )
    commandLine("python3", "scripts/ov01_release_gate.py", "host")
}

tasks.register<Exec>("verifyFinalReleaseArtifact") {
    group = "verification"
    description = "Verifies exact signed APK/AAB identity, hashes, and signatures for this host session."
    dependsOn("verifyFinalReleaseHostGate")
    mustRunAfter(
        ":core:data:connectedDebugAndroidTest",
        ":core:database:connectedDebugAndroidTest",
        ":feature:plans:connectedDebugAndroidTest",
        ":feature:practice:connectedDebugAndroidTest",
        ":feature:schedule:connectedDebugAndroidTest",
        ":feature:workspace:connectedDebugAndroidTest",
        ":app:connectedReleaseAndroidTest",
    )
    commandLine("python3", "scripts/verify_final_release_artifact.py")
}

tasks.register<Exec>("recordConnectedReleaseTestGates") {
    group = "verification"
    description = "Runs required connected Android suites and records PASS only after every suite succeeds."
    dependsOn(
        "verifyFinalReleaseArtifact",
        ":core:data:connectedDebugAndroidTest",
        ":core:database:connectedDebugAndroidTest",
        ":feature:plans:connectedDebugAndroidTest",
        ":feature:practice:connectedDebugAndroidTest",
        ":feature:schedule:connectedDebugAndroidTest",
        ":feature:workspace:connectedDebugAndroidTest",
        ":app:connectedReleaseAndroidTest",
    )
    commandLine("python3", "scripts/ov01_release_gate.py", "connected")
}

tasks.register<Exec>("recordFullJourneyEvidence") {
    group = "verification"
    description = "Runs the exact signed Release restore/restart journey and records bound Android evidence."
    dependsOn("recordConnectedReleaseTestGates")
    commandLine("python3", "scripts/record_full_journey_evidence.py")
}

tasks.register<Exec>("verifyFinalReleaseAutomatedGate") {
    group = "verification"
    description = "Mandatory automated final gate: signed artifacts, connected suites, and restore/restart journey."
    dependsOn("recordFullJourneyEvidence")
    commandLine("python3", "scripts/ov01_release_gate.py", "automated")
}

tasks.register<Exec>("verifyFinalReleaseVerdict") {
    group = "verification"
    description = "Fails closed until automated and manual final Release evidence all PASS."
    commandLine("python3", "scripts/verify_final_release_verdict.py")
}

tasks.register<Exec>("verifyStudyFlowDesktopFoundation") {
    group = "verification"
    description = "Checks StudyFlow desktop foundation wiring before the desktop app shell exists."
    commandLine("python3", "scripts/verify_studyflow_desktop_foundation.py")
}


tasks.register<Exec>("verifyStudyFlowDesktopShellContracts") {
    group = "verification"
    description = "Checks the Compose Desktop shell and shared-module boundary contracts."
    commandLine("python3", "scripts/verify_studyflow_desktop_shell_contracts.py")
}


tasks.register<Exec>("verifyStudyFlowDesktopBootstrapScreens") {
    group = "verification"
    description = "Checks desktop bootstrap, domain wiring, dashboard, and library screens."
    commandLine("python3", "scripts/verify_studyflow_desktop_bootstrap_screens.py")
}


tasks.register<Exec>("verifyStudyFlowDesktopWorkflows") {
    group = "verification"
    description = "Checks desktop planner, practice, analytics, search, and settings workflow contracts."
    commandLine("python3", "scripts/verify_studyflow_desktop_workflows.py")
}


tasks.register<Exec>("verifyDesktopDov08PersistentWorkspace") {
    group = "verification"
    description = "Checks DOV08 durable Desktop workspace persistence and production bootstrap wiring."
    commandLine("python3", "scripts/verify_desktop_dov08_persistent_workspace.py")
}


tasks.register<Exec>("verifyDesktopDov09PlanContentLifecycle") {
    group = "verification"
    description = "Checks DOV09 persistent Study Plan, Subject, and Topic lifecycle contracts."
    commandLine("python3", "scripts/verify_desktop_dov09_plan_content_lifecycle.py")
}


tasks.register<Exec>("verifyDesktopDov10PlannerAdaptiveWorkflows") {
    group = "verification"
    description = "Checks DOV10 persistent planner authoring and adaptive schedule workflows."
    commandLine("python3", "scripts/verify_desktop_dov10_planner_adaptive_workflows.py")
}


tasks.register<Exec>("verifyDesktopDov11SessionRuntime") {
    group = "verification"
    description = "Checks DOV11 durable Study Session runtime and linked planned-activity transitions."
    commandLine("python3", "scripts/verify_desktop_dov11_session_runtime.py")
}


tasks.register<Exec>("verifyDesktopDov12QuestionPracticeExecution") {
    group = "verification"
    description = "Checks DOV12 persistent Desktop question practice execution contracts."
    commandLine("python3", "scripts/verify_desktop_dov12_question_practice_execution.py")
}


tasks.register<Exec>("verifyDesktopDov13MockExamStudio") {
    group = "verification"
    description = "Checks DOV13 restart-safe Desktop Mock Exam Studio contracts."
    commandLine("python3", "scripts/verify_desktop_dov13_mock_exam_studio.py")
}


tasks.register<Exec>("verifyDesktopDov14ReviewsSrsErrorNotebook") {
    group = "verification"
    description = "Checks DOV14 persistent Desktop reviews, SRS, flashcards, and Error Notebook contracts."
    commandLine("python3", "scripts/verify_desktop_dov14_reviews_srs_error_notebook.py")
}


tasks.register<Exec>("verifyDesktopDov15LibraryFilesMaterialViewers") {
    group = "verification"
    description = "Checks DOV15 persistent Desktop library files and material viewer contracts."
    commandLine("python3", "scripts/verify_desktop_dov15_library_files_material_viewers.py")
}

tasks.register<Exec>("verifyDesktopDov16WritingWorkspace") {
    group = "verification"
    description = "Checks DOV16 persistent Desktop Writing Workspace contracts."
    commandLine("python3", "scripts/verify_desktop_dov16_writing_workspace.py")
}


tasks.register<Exec>("verifyDesktopDov17ContentModesExecution") {
    group = "verification"
    description = "Checks DOV17 persisted Desktop content modes and native execution contracts."
    commandLine("python3", "scripts/verify_desktop_dov17_content_modes_execution.py")
}

tasks.register<Exec>("verifyDesktopDov18PublicExamParity") {
    group = "verification"
    description = "Verify Desktop DOV18 Concurso Publico parity"
    commandLine("python3", "scripts/verify_desktop_dov18_public_exam_parity.py")
}

tasks.register<Exec>("verifyDesktopDov19ReadinessCoachingReports") {
    group = "verification"
    description = "Verify Desktop DOV19 readiness, coaching, cleanup, reports, runbooks, and sprints"
    commandLine("python3", "scripts/verify_desktop_dov19_readiness_coaching_reports.py")
}


tasks.register<Exec>("verifyDesktopDov20PortabilityBackupMigration") {
    group = "verification"
    description = "Verify Desktop DOV20 portable export/import, encrypted backup/restore, and migration"
    commandLine("python3", "scripts/verify_desktop_dov20_portability_backup_migration.py")
}

tasks.register<Exec>("verifyDesktopDov21EnPtBrLocalization") {
    group = "verification"
    description = "Verify Desktop DOV21 English and pt-BR localization"
    commandLine("python3", "scripts/verify_desktop_dov21_en_ptbr_localization.py")
}

tasks.register<Exec>("verifyDesktopDov22SystemLightDarkAppearance") {
    group = "verification"
    description = "Verify Desktop DOV22 System, Light, and Dark appearance"
    commandLine("python3", "scripts/verify_desktop_dov22_system_light_dark_appearance.py")
}

tasks.register<Exec>("verifyDesktopDov23NativeDesktopIntegration") {
    group = "verification"
    description = "Verify Desktop DOV23 native file, browser, notification, and deep-link integration"
    commandLine("python3", "scripts/verify_desktop_dov23_native_desktop_integration.py")
}

tasks.register<Exec>("verifyDesktopDov24FullApplicableParitySeal") {
    group = "verification"
    description = "Verify Desktop DOV24 full applicable parity acceptance seal"
    commandLine("python3", "scripts/verify_desktop_dov24_full_applicable_parity_seal.py")
}

tasks.register("verifyDesktopDov20Dov24Batch") {
    group = "verification"
    description = "Run the terminal Desktop DOV20-DOV24 portability, presentation, native, and parity seal."
    dependsOn(
        "verifyDesktopDov20PortabilityBackupMigration",
        "verifyDesktopDov21EnPtBrLocalization",
        "verifyDesktopDov22SystemLightDarkAppearance",
        "verifyDesktopDov23NativeDesktopIntegration",
        "verifyDesktopDov24FullApplicableParitySeal",
        ":desktopApp:compileKotlin",
        ":desktopApp:test",
    )
}

tasks.register("verifyDesktopDov16Dov19Batch") {
    group = "verification"
    description = "Verify Desktop DOV16-DOV19 writing, modes, public exam, and progress batch"
    dependsOn(
        "verifyDesktopDov16WritingWorkspace",
        "verifyDesktopDov17ContentModesExecution",
        "verifyDesktopDov18PublicExamParity",
        "verifyDesktopDov19ReadinessCoachingReports",
        ":desktopApp:compileKotlin",
        ":desktopApp:test",
    )
}


tasks.register("verifyDesktopDov08Dov11Batch") {
    group = "verification"
    description = "Runs the DOV08-DOV11 Desktop persistence, structure, planner, and session batch seal."
    dependsOn(
        "verifyDesktopDov08PersistentWorkspace",
        "verifyDesktopDov09PlanContentLifecycle",
        "verifyDesktopDov10PlannerAdaptiveWorkflows",
        "verifyDesktopDov11SessionRuntime",
        ":desktopApp:test",
        ":desktopApp:compileKotlin",
    )
}


tasks.register<Exec>("verifyCpov05Cpov06PriorityBancaQuestions") {
    group = "verification"
    description = "Checks CPOV05 explainable priority and CPOV06 banca question contracts."
    commandLine("python3", "scripts/verify_cpov05_06_priority_banca_questions.py")
}

tasks.register<Exec>("verifyCpov07Cpov08BancaErrorNotebook") {
    group = "verification"
    description = "Checks CPOV07 banca blueprint scoring and CPOV08 error-notebook review-policy contracts."
    commandLine("python3", "scripts/verify_cpov07_08_banca_error_notebook.py")
}

tasks.register("verifyCpovAllContracts") {
    group = "verification"
    description = "Runs every CPOV00-CPOV13 contract and the roadmap reconciliation once."
    dependsOn(
        "verifyCpov00Toolchain",
        "verifyCpov01PublicExamProfile",
        "verifyCpov02EditalImport",
        "verifyCpov03Cpov04PublicExamProgress",
        "verifyCpov05Cpov06PriorityBancaQuestions",
        "verifyCpov07Cpov08BancaErrorNotebook",
        "verifyCpov09Cpov10LegislationDashboard",
        "verifyCpov11Cpov12FinalStretchOnboarding",
        "verifyCpov13PublicExamReleaseSeal",
        "verifyCpovRoadmapReconciliation",
    )
}

tasks.register("verifyStudyFlowDesktopAllContracts") {
    group = "verification"
    description = "Runs all Desktop DOV/ATT04 static product contracts once."
    dependsOn(
        "verifyStudyFlowDesktopFoundation",
        "verifyStudyFlowDesktopBootstrapScreens",
        "verifyStudyFlowDesktopShellContracts",
        "verifyStudyFlowDesktopWorkflows",
        "verifyDesktopDov08PersistentWorkspace",
        "verifyDesktopDov09PlanContentLifecycle",
        "verifyDesktopDov10PlannerAdaptiveWorkflows",
        "verifyDesktopDov11SessionRuntime",
        "verifyDesktopDov12QuestionPracticeExecution",
        "verifyDesktopDov13MockExamStudio",
        "verifyDesktopDov14ReviewsSrsErrorNotebook",
        "verifyDesktopDov15LibraryFilesMaterialViewers",
        "verifyDesktopDov16WritingWorkspace",
        "verifyDesktopDov17ContentModesExecution",
        "verifyDesktopDov18PublicExamParity",
        "verifyDesktopDov19ReadinessCoachingReports",
        "verifyDesktopDov20PortabilityBackupMigration",
        "verifyDesktopDov21EnPtBrLocalization",
        "verifyDesktopDov22SystemLightDarkAppearance",
        "verifyDesktopDov23NativeDesktopIntegration",
        "verifyDesktopDov24FullApplicableParitySeal",
        "verifyAtt04DesktopAttentionContracts",
    )
}

tasks.register("verifyStudyFlowTvAllContracts") {
    group = "verification"
    description = "Runs every TVOV source/product contract plus TV Attention contracts once."
    dependsOn(
        "verifyStudyFlowTvDevtoolAndroidRunner",
        "verifyStudyFlowTvArchitecture",
        "verifyStudyFlowTvSizeBudget",
        "verifyStudyFlowTvControlCast",
        "verifyStudyFlowTvAppModule",
        "verifyStudyFlowTvDependencyBudget",
        "verifyStudyFlowTvPairingControlCastProtocol",
        "verifyStudyFlowTvFocusPrimitives",
        "verifyStudyFlowTvNavigationShell",
        "verifyStudyFlowTvHomeSurface",
        "verifyStudyFlowTvTodayWorkspace",
        "verifyStudyFlowTvStudySessionExperience",
        "verifyStudyFlowTvQuestionPractice",
        "verifyStudyFlowTvMockExamStudio",
        "verifyStudyFlowTvReviewNotebook",
        "verifyStudyFlowTvProgressAnalytics",
        "verifyStudyFlowTvReadOnlySchedule",
        "verifyStudyFlowTvLibraryReference",
        "verifyStudyFlowTvSettings",
        "verifyStudyFlowTvControlStateContinuity",
        "verifyStudyFlowTvAccessibilityTenFootUi",
        "verifyStudyFlowTvSizeOptimization",
        "verifyStudyFlowTvManifestPackaging",
        "verifyStudyFlowTvTargetIsolation",
        "verifyStudyFlowTvFinalAcceptanceContracts",
        "verifyStudyFlowTvPresentationProjection",
        "verifyStudyFlowTvLocalPairingTransport",
        "verifyStudyFlowTvPhoneController",
        "verifyStudyFlowTvCastStateSynchronization",
        "verifyStudyFlowTvDomainPersistenceConvergence",
        "verifyStudyFlowTvEndToEndPhysicalSealContracts",
        "verifyStudyFlowTvFullNativeMaterialViewer",
        "verifyStudyFlowTvGoalProjectionActions",
        "verifyStudyFlowTvErrorNotebookMutationParity",
        "verifyStudyFlowTvDpadSflpSearch",
        "verifyStudyFlowTvAndroidWebSemanticParity",
        "verifyStudyFlowTvRoadmapInstructions",
        "verifyAtt06TvAttentionContracts",
        "verifyAtt07CrossDeviceDeliveryContracts",
        "verifyAtt08CompactCompanionAttentionContracts",
    )
}

tasks.register("verifyStudyFlowAndroidPreflightContracts") {
    group = "verification"
    description = "Optimized Android preflight: historical, CPOV and terminal static source contracts once."
    dependsOn(
        "verifyRepositoryContracts",
        "verifyCpovAllContracts",
        "verifyOv19PromiseAudit",
        "verifyTermuxRuntimeFoundation",
        "verifyProgrammingCourseUi",
        "verifyCodingExerciseTermuxExecution",
        "verifyLanguageDeckIdiomModel",
        "verifyLanguageReviewModes",
        "verifyImportTemplates",
        "verifyModeAwareAnalytics",
        "verifyContentModesFinalIntegrationSeal",
        "verifyOv26SpecializedContentDataUi",
        "verifyOv27TermuxExecutionPipelineSandboxCorrectness",
        "verifyOv28LanguageReviewDesktopSpecializedParity",
        "verifyOv29BehavioralValidationReleaseTruthGates",
    )
}

tasks.register<Exec>("verifyStudyFlowGradleTaskGraphOptimizationV4") {
    group = "verification"
    description = "Statically checks v4 Gradle/Devtool task-graph deduplication and config invariants."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_studyflow_gradle_task_graph_optimization_v4.py")
}

tasks.register("checkAll") {
    group = "verification"
    description = "Runs repository contracts, formatting, unit tests, and Android lint."
    dependsOn(
        "verifyRepositoryContracts",
        "verifyCpov00Toolchain",
        "verifyCpov01PublicExamProfile",
        "verifyCpov02EditalImport",
        "verifyCpov03Cpov04PublicExamProgress",
        "verifyCpov05Cpov06PriorityBancaQuestions",
        "verifyCpov07Cpov08BancaErrorNotebook",
        "verifyCpov09Cpov10LegislationDashboard",
        "verifyCpov11Cpov12FinalStretchOnboarding",
        "verifyCpov13PublicExamReleaseSeal",
        "verifyCpovRoadmapReconciliation",
        "verifyPostPhase64FinalValidationRefresh",
        "verifyAndroidFocusShellFinalSeal",
        "verifyCloudOptionalSyncSettings",
        "verifyCsov02SyncBundle",
        "verifyCsov03ProviderAbstraction",
        "verifyCsov04OptionalSyncOnboarding",
        "verifyCsov05ManualFolderSync",
        "verifyCsov06SyncLedger",
        "verifyCsov07MergeEngine",
        "verifyCsov08ConflictReview",
        "verifyCsov09BackgroundSyncModes",
        "verifyCsov10RealProviders",
        "verifyCsov11FinalSyncSeal",
        "verifyCsov12ProviderSetupSecurity",
        "verifyCsov13SyncRunbook",
        "verifyCsov14OptionalSyncDeliveryAudit",
        "verifyCsov15OptionalInstrumentationValidation",
        "verifyContentModeFoundation",
        "verifyProgrammingCourseModel",
        "verifyCodeBlocksSyntax",
        "verifyTermuxRuntimeFoundation",
        "verifyProgrammingCourseUi",
        "verifyCodingExerciseTermuxExecution",
        "verifyLanguageDeckIdiomModel",
        "verifyLanguageReviewModes",
        "verifyImportTemplates",
        "verifyModeAwareAnalytics",
        "verifyContentModesFinalIntegrationSeal",
        "verifyOv19PromiseAudit",
        "verifyOv20FinalIntegration",
        "verifyFinalSourceAcceptance",
        "verifyRemediationOv13",
        "verifyFormatting",
        "verifySflp01ProtocolBaseline",
        "verifySflp02Sflp03JournalReconciliation",
        "verifySflp04Sflp05IdentityTransport",
        "verifySflp06Sflp07TopologyOfflineQueue",
        "verifySflp08Sflp09AndroidDesktopIntegration",
        "verifySflp10Sflp11WebTvGeneralization",
        "verifySflp12Sflp13WebTvHandoff",
        "verifySflp14Sflp15LiveSessionProjection",
        "verifySflp16Sflp17ControlInputDelegation",
        "verifySflp18Sflp19NotificationMediaSync",
        "verifySflp20Sflp21ShareImportConflict",
        "verifySflp22Sflp23AccountBackupValidation",
        "verifySflp24FinalReleaseSeal",
        "verifyRel01TargetReleaseValidationRunner",
        "verifyRel02ReleaseEvidenceTriage",
        "verifyRel03Rel04ArtifactInstallRollout",
        "verifyRel05Rel06ReleaseNotesRollback",
        "verifyRel07Rel08FinalReleaseOpsSeal",
        ":core:diagnostics:test",
        ":core:data:testDebugUnitTest",
        ":core:database:testDebugUnitTest",
        ":core:domain:test",
        ":core:model:test",
        ":core:navigation:test",
        ":core:portability:test",
        ":core:testing:test",
        ":app:testReleaseUnitTest",
        ":feature:dashboard:testDebugUnitTest",
        ":feature:analytics:testDebugUnitTest",
        ":feature:library:testDebugUnitTest",
        ":feature:progress:testDebugUnitTest",
        ":feature:privacy:testDebugUnitTest",
        ":feature:plans:testDebugUnitTest",
        ":feature:practice:testDebugUnitTest",
        ":feature:reminders:testDebugUnitTest",
        ":feature:reviews:testDebugUnitTest",
        ":feature:search:testDebugUnitTest",
        ":feature:schedule:testDebugUnitTest",
        ":feature:sessions:testDebugUnitTest",
        ":feature:settings:testDebugUnitTest",
        ":feature:subjects:testDebugUnitTest",
        ":feature:workspace:testDebugUnitTest",
        ":feature:workspace:lintDebug",
        ":app:lintDebug",
        ":app:compileReleaseKotlin",
        ":app:lintRelease",
        ":app:assembleRelease",
        ":app:bundleRelease",
    )
}


tasks.register<Exec>("verifyCsov12ProviderSetupSecurity") {
    group = "verification"
    description = "Checks CSOV12 provider setup and credential-redaction source contracts."
    commandLine("python3", "scripts/verify_csov12_provider_setup_security.py")
}


tasks.register<Exec>("verifyCsov13SyncRunbook") {
    group = "verification"
    description = "Checks CSOV13 manual and background sync runbook source contracts."
    commandLine("python3", "scripts/verify_csov13_sync_runbook.py")
}


tasks.register<Exec>("verifyCsov14OptionalSyncDeliveryAudit") {
    group = "verification"
    description = "Runs the cumulative CSOV00-CSOV13 optional sync delivery audit."
    commandLine("python3", "scripts/verify_csov14_optional_sync_delivery_audit.py")
}


tasks.register<Exec>("verifyCsov15OptionalInstrumentationValidation") {
    group = "verification"
    description = "Checks CSOV15 optional adb/root instrumentation validation source contracts."
    commandLine("python3", "scripts/verify_csov15_optional_instrumentation_validation.py")
}


tasks.register<Exec>("recordOptionalInstrumentationValidation") {
    group = "verification"
    description = "Optionally runs connected Android instrumentation through adb and records skip/pass/fail evidence."
    commandLine("python3", "scripts/run_optional_instrumentation_validation.py")
}

tasks.register<Exec>("verifyCpov09Cpov10LegislationDashboard") {
    group = "verification"
    description = "Verifies CPOV09 structured legislation and CPOV10 derived concurso dashboard contracts."
    commandLine("python3", "scripts/verify_cpov09_10_legislation_dashboard.py")
}


tasks.register<Exec>("verifyCpov11Cpov12FinalStretchOnboarding") {
    group = "verification"
    description = "Verifies CPOV11 final-stretch strategy and CPOV12 public-exam onboarding contracts."
    commandLine("python3", "scripts/verify_cpov11_12_final_stretch_onboarding.py")
}

tasks.register<Exec>("verifyCpov13PublicExamReleaseSeal") {
    group = "verification"
    description = "Verifies the complete CPOV migration, portability, connected-journey and release-seal contracts."
    commandLine("python3", "scripts/verify_cpov13_public_exam_release_seal.py")
}

tasks.register<Exec>("verifyCpovRoadmapReconciliation") {
    group = "verification"
    description = "Verifies reconciled CPOV00-CPOV13 roadmap promises missed by the original source seals."
    commandLine("python3", "scripts/verify_cpov_roadmap_reconciliation.py")
}



tasks.register<Exec>("verifyStudyFlowTvQuestionPractice") {
    group = "verification"
    description =
        "Checks TVOV09 Android TV question-practice source, control, focus, shell, test, and" +
        "documentation contracts."
    commandLine("python3", "scripts/verify_tvov09_question_practice.py")
}

tasks.register<Exec>("verifyStudyFlowTvMockExamStudio") {
    group = "verification"
    description =
        "Checks TVOV10 Android TV Mock Exam Studio source, control, focus, shell, test, and" +
        "documentation contracts."
    commandLine("python3", "scripts/verify_tvov10_mock_exam_studio.py")
}

tasks.register<Exec>("verifyStudyFlowTvReviewNotebook") {
    group = "verification"
    description =
        "Checks TVOV11 Android TV Review and error-notebook source, control, focus, shell," +
        "test, and documentation contracts."
    commandLine("python3", "scripts/verify_tvov11_review_notebook.py")
}

tasks.register<Exec>("verifyStudyFlowTvProgressAnalytics") {
    group = "verification"
    description =
        "Checks TVOV12 Android TV Progress and analytics source, control, focus, shell, test," +
        "and documentation contracts."
    commandLine("python3", "scripts/verify_tvov12_progress_analytics.py")
}


tasks.register<Exec>("verifyStudyFlowTvReadOnlySchedule") {
    group = "verification"
    description =
        "Checks TVOV13 Android TV read-only Schedule source, control, focus, shell, test, and" +
        "documentation contracts."
    commandLine("python3", "scripts/verify_tvov13_read_only_schedule.py")
}

tasks.register<Exec>("verifyStudyFlowTvLibraryReference") {
    group = "verification"
    description =
        "Checks TVOV14 Android TV limited Library/reference source, control, focus, shell," +
        "test, and documentation contracts."
    commandLine("python3", "scripts/verify_tvov14_library_reference.py")
}


tasks.register<Exec>("verifyStudyFlowTvSettings") {
    group = "verification"
    description = "Checks TVOV15 Android TV minimal Settings source, control, shell, test, and documentation contracts."
    commandLine("python3", "scripts/verify_tvov15_settings.py")
}

tasks.register<Exec>("verifyStudyFlowTvControlStateContinuity") {
    group = "verification"
    description = "Checks TVOV16 Android TV local/paired control and state-continuity contracts."
    commandLine("python3", "scripts/verify_tvov16_control_state_continuity.py")
}


tasks.register<Exec>("verifyStudyFlowTvAccessibilityTenFootUi") {
    group = "verification"
    description =
        "Checks TVOV17 Android TV accessibility, 10-foot typography/layout, focus visibility," +
        "tests, and shell contracts."
    commandLine("python3", "scripts/verify_tvov17_accessibility_10foot_ui.py")
}

tasks.register<Exec>("verifyStudyFlowTvSizeOptimization") {
    group = "verification"
    description =
        "Checks TVOV18 Android TV R8, resource shrinking, release budget policy, tests, and" +
        "Devtool contracts."
    commandLine("python3", "scripts/verify_tvov18_size_optimization.py")
}

tasks.register<Exec>("verifyStudyFlowTvOptimizedReleaseArtifact") {
    group = "verification"
    description =
        "Builds and verifies the optimized TV Release APK, R8 mapping evidence, ZIP" +
        "integrity, and 25 MiB target."
    dependsOn(":app-tv:assembleRelease")
    commandLine("python3", "scripts/verify_tvov18_size_optimization.py", "--require-release-artifact")
}

tasks.register<Exec>("verifyStudyFlowTvManifestPackaging") {
    group = "verification"
    description =
        "Checks TVOV19 Leanback manifest, package identity, backup/data-extraction hardening," +
        "signing contract, and banner resources."
    commandLine("python3", "scripts/verify_tvov19_manifest_packaging.py")
}

tasks.register<Exec>("verifyStudyFlowTvTargetIsolation") {
    group = "verification"
    description =
        "Checks that the studyflow_tv Devtool target contains only TV/shared-control tasks" +
        "and never phone :app or feature-module work."
    commandLine("python3", "scripts/verify_tvov19_manifest_packaging.py", "--target-isolation")
}

tasks.register<Exec>("verifyStudyFlowTvPackagedArtifacts") {
    group = "verification"
    description =
        "Builds and inspects Debug/Release TV APK packaging, Leanback identity, ZIP" +
        "integrity, and optional production signing."
    dependsOn(":app-tv:assembleDebug", ":app-tv:assembleRelease")
    commandLine("python3", "scripts/verify_tvov19_manifest_packaging.py", "--require-packaged-artifacts")
}

tasks.register<Exec>("verifyStudyFlowTvFinalAcceptanceContracts") {
    group = "verification"
    description =
        "Checks TVOV20 final release/real-device acceptance policy, tests, docs, and" +
        "validation wiring without requiring a connected device."
    commandLine("python3", "scripts/verify_tvov20_real_device_release_seal.py")
}

tasks.register<Exec>("verifyStudyFlowTvRealDeviceAcceptance") {
    group = "verification"
    description =
        "Runs TVOV20 install/upgrade, launch, Leanback-device, and D-pad smoke checks when" +
        "exactly one usable TV ADB device is connected; otherwise skips cleanly."
    dependsOn(":app-tv:assembleDebug", "verifyStudyFlowTvManifestPackaging")
    commandLine("python3", "scripts/verify_tvov20_real_device_release_seal.py", "--device-acceptance")
}

tasks.register<Exec>("verifyStudyFlowTvFinalReleaseSeal") {
    group = "verification"
    description =
        "Seals TVOV20 Release criteria from optimized artifact, package hardening, unit" +
        "tests, lint, and optional production signing evidence."
    dependsOn(
        "verifyStudyFlowTvOptimizedReleaseArtifact",
        "verifyStudyFlowTvPackagedArtifacts",
        ":core:tvcontrol:test",
        ":core:tvlink:testDebugUnitTest",
        ":core:tvlink:lintDebug",
        ":app-tv:testDebugUnitTest",
        ":app-tv:lintDebug",
        ":app-tv:lintRelease",
    )
    commandLine("python3", "scripts/verify_tvov20_real_device_release_seal.py", "--require-release-artifact")
}


tasks.register<Exec>("verifySflp01ProtocolBaseline") {
    group = "verification"
    description =
        "Checks SFLP01 platform-neutral protocol identities, capabilities, envelope/schema/golden vectors, " +
            "and the pure JVM core:link contract tests."
    dependsOn(":core:link:test")
    commandLine("python3", "scripts/verify_sflp01_protocol_baseline.py")
}


tasks.register<Exec>("verifySflp02Sflp03JournalReconciliation") {
    group = "verification"
    description =
        "Checks SFLP02/SFLP03 journal, vector-frontier reconciliation, conflicts, sync wire vectors, " +
            "and pure JVM core:link tests."
    dependsOn("verifySflp01ProtocolBaseline", ":core:link:test")
    commandLine("python3", "scripts/verify_sflp02_03_journal_reconciliation.py")
}

tasks.register<Exec>("verifySflp04Sflp05IdentityTransport") {
    group = "verification"
    description =
        "Checks SFLP04/SFLP05 generic identity/trust/pairing/reconnect plus transport/discovery contracts."
    dependsOn("verifySflp02Sflp03JournalReconciliation", ":core:link:test")
    commandLine("python3", "scripts/verify_sflp04_05_identity_transport.py")
}


tasks.register<Exec>("verifySflp06Sflp07TopologyOfflineQueue") {
    group = "verification"
    description =
        "Checks SFLP06/SFLP07 multi-peer routing, loop prevention, durable offline queue, and reconnect recovery."
    dependsOn("verifySflp04Sflp05IdentityTransport", ":core:link:test")
    commandLine("python3", "scripts/verify_sflp06_07_topology_offline_queue.py")
}


tasks.register<Exec>("verifySflp08Sflp09AndroidDesktopIntegration") {
    group = "verification"
    description =
        "Checks SFLP08/SFLP09 Android + Desktop generic peer bindings, persistence, discovery, " +
            "trust UI, and direct sync."
    dependsOn(
        "verifySflp06Sflp07TopologyOfflineQueue",
        ":core:link:test",
        ":desktopApp:test",
        ":app:testReleaseUnitTest",
    )
    commandLine("python3", "scripts/verify_sflp08_09_android_desktop_integration.py")
}


tasks.register<Exec>("verifySflp10Sflp11WebTvGeneralization") {
    group = "verification"
    description =
        "Checks SFLP10/SFLP11 Web/Web-TV peer integration and Android TV generic peer generalization."
    dependsOn(
        "verifySflp08Sflp09AndroidDesktopIntegration",
        ":core:link:test",
        ":app-tv:testDebugUnitTest",
    )
    commandLine("python3", "scripts/verify_sflp10_11_web_tv_generalization.py")
}



tasks.register<Exec>("verifySflp12Sflp13WebTvHandoff") {
    group = "verification"
    description =
        "Checks SFLP12 Web TV receiver connectivity and SFLP13 cross-device activity handoff."
    dependsOn(
        "verifySflp10Sflp11WebTvGeneralization",
        ":core:link:test",
        ":app-tv:testDebugUnitTest",
        ":desktopApp:test",
        ":app:testReleaseUnitTest",
    )
    commandLine("python3", "scripts/verify_sflp12_13_web_tv_handoff.py")
}



tasks.register<Exec>("verifySflp14Sflp15LiveSessionProjection") {
    group = "verification"
    description =
        "Checks SFLP14 live session continuity and SFLP15 universal presentation projection."
    dependsOn(
        "verifySflp12Sflp13WebTvHandoff",
        ":core:link:test",
        ":app-tv:testDebugUnitTest",
        ":desktopApp:test",
        ":app:testReleaseUnitTest",
    )
    commandLine("python3", "scripts/verify_sflp14_15_live_session_projection.py")
}



tasks.register<Exec>("verifySflp16Sflp17ControlInputDelegation") {
    group = "verification"
    description = "Checks SFLP16 control protocol and SFLP17 input delegation."
    dependsOn(
        "verifySflp14Sflp15LiveSessionProjection",
        ":core:link:test",
        ":app-tv:testDebugUnitTest",
        ":desktopApp:test",
        ":app:testReleaseUnitTest",
    )
    commandLine("python3", "scripts/verify_sflp16_17_control_input_delegation.py")
}


tasks.register<Exec>("verifySflp18Sflp19NotificationMediaContracts") {
    group = "verification"
    description = "Checks only the SFLP18/19 notification/media source contracts for focused consumers."
    commandLine("python3", "scripts/verify_sflp18_19_notification_media_sync.py")
}

tasks.register("verifySflp18Sflp19NotificationMediaSync") {
    group = "verification"
    description = "Full historical SFLP18/19 seal with predecessor and native runtime coverage."
    dependsOn(
        "verifySflp16Sflp17ControlInputDelegation",
        "verifySflp18Sflp19NotificationMediaContracts",
        ":core:link:test",
        ":app-tv:testDebugUnitTest",
        ":desktopApp:test",
        ":app:testReleaseUnitTest",
    )
}


tasks.register<Exec>("verifySflp20Sflp21ShareImportConflict") {
    group = "verification"
    description = "Checks SFLP20 clipboard/share/import bridge and SFLP21 conflict-review UX."
    dependsOn(
        "verifySflp18Sflp19NotificationMediaSync",
        ":core:link:test",
        ":app-tv:testDebugUnitTest",
        ":desktopApp:test",
        ":app:testReleaseUnitTest",
    )
    commandLine("python3", "scripts/verify_sflp20_21_share_import_conflict.py")
}


tasks.register<Exec>("verifyCrossPlatformParityBaseline") {
    group = "verification"
    description =
        "Checks PARITY01-PARITY03 cross-platform capability truth, documentation reconciliation, " +
            "and the restored OV20 integration seal."
    commandLine("python3", "scripts/verify_parity01_03_cross_platform_baseline.py")
}


tasks.register<Exec>("verifyStudyFlowTvPresentationProjection") {
    group = "verification"
    description =
        "Checks TVOV21 typed presentation snapshots, canonical phone-side projection, " +
            "fixture-free TV runtime wiring, and source-ID continuity."
    commandLine("python3", "scripts/verify_tvov21_presentation_projection.py")
}

tasks.register<Exec>("verifyStudyFlowTvLocalPairingTransport") {
    group = "verification"
    description =
        "Checks TVOV22 explicit local pairing, Android NSD discovery, framed TCP transport, " +
            "revocable sessions, and production phone/TV wiring."
    commandLine("python3", "scripts/verify_tvov22_local_pairing_transport.py")
}

tasks.register<Exec>("verifyStudyFlowTvPhoneController") {
    group = "verification"
    description =
        "Checks TVOV23 Settings-accessible Android phone TV controller, explicit pairing UI, " +
            "persisted-session resume, and shared remote-command coverage."
    commandLine("python3", "scripts/verify_tvov23_phone_controller.py")
}

tasks.register<Exec>("verifyStudyFlowTvCastStateSynchronization") {
    group = "verification"
    description =
        "Checks TVOV24 canonical presentation streaming, exact TV receipt acknowledgement, " +
            "reconnect resync, and explicit STOP_CAST lifecycle."
    commandLine("python3", "scripts/verify_tvov24_cast_state_synchronization.py")
}

tasks.register<Exec>("verifyStudyFlowTvDomainPersistenceConvergence") {
    group = "verification"
    description =
        "Checks TVOV25 canonical phone-owned mutations, TV outbox/ack flow, idempotent persistence, " +
            "and fixture-free projected results."
    commandLine("python3", "scripts/verify_tvov25_domain_persistence_convergence.py")
}



tasks.register<Exec>("verifyStudyFlowTvEndToEndPhysicalSealContracts") {
    group = "verification"
    description =
        "Checks TVOV26 fail-closed two-device Release acceptance, restart-resync, exact-artifact " +
            "evidence, and final seal wiring without claiming physical execution."
    commandLine("python3", "scripts/verify_tvov26_end_to_end_physical_seal.py")
}

tasks.register<Exec>("verifyStudyFlowTvPhysicalTwoDeviceAcceptance") {
    group = "verification"
    description =
        "Runs fail-closed TVOV26 physical acceptance on one real Android phone and one real " +
            "Leanback TV; missing devices, signing, or data are failures, never skips."
    dependsOn(
        ":app:assembleRelease",
        ":app-tv:assembleRelease",
        "verifyStudyFlowTvEndToEndPhysicalSealContracts",
        "verifyStudyFlowTvOptimizedReleaseArtifact",
        "verifyStudyFlowTvPackagedArtifacts",
    )
    commandLine("python3", "scripts/run_tvov26_two_device_acceptance.py")
}

tasks.register<Exec>("verifyStudyFlowTvFinalProductSeal") {
    group = "verification"
    description =
        "Consumes real TVOV26 two-device evidence fail-closed and seals the complete " +
            "TVOV00-TVOV26 product track."
    dependsOn(
        "verifyStudyFlowTvPhysicalTwoDeviceAcceptance",
        "verifyStudyFlowTvFinalReleaseSeal",
        ":core:tvcontrol:test",
        ":core:tvlink:testDebugUnitTest",
        ":app-tv:testDebugUnitTest",
        ":app-tv:lintRelease",
    )
    commandLine("python3", "scripts/verify_tvov26_end_to_end_physical_seal.py", "--require-evidence")
}

tasks.register<Exec>("verifyStudyFlowTvFullNativeMaterialViewer") {
    group = "verification"
    description = "Checks TVOV27 native text/image/PDF/media material viewing and presentation projection."
    commandLine("python3", "scripts/verify_tvov27_full_native_material_viewer.py")
}

tasks.register<Exec>("verifyStudyFlowTvGoalProjectionActions") {
    group = "verification"
    description = "Checks TVOV28 canonical goal projection and complete/pause/resume actions."
    commandLine("python3", "scripts/verify_tvov28_goal_projection_actions.py")
}

tasks.register<Exec>("verifyStudyFlowTvErrorNotebookMutationParity") {
    group = "verification"
    description = "Checks TVOV29 Error Notebook reclassify/resolve/reopen parity."
    commandLine("python3", "scripts/verify_tvov29_error_notebook_mutation_parity.py")
}

tasks.register<Exec>("verifyStudyFlowTvDpadSflpSearch") {
    group = "verification"
    description = "Checks TVOV30 D-pad search and SFLP delegated text input."
    commandLine("python3", "scripts/verify_tvov30_dpad_sflp_search.py")
}

tasks.register<Exec>("verifyStudyFlowTvAndroidWebSemanticParity") {
    group = "verification"
    description = "Checks TVOV31 Android-TV/Web-TV common semantic fixture parity."
    commandLine("python3", "scripts/verify_tvov31_android_web_tv_semantic_parity.py")
}

tasks.register("verifyStudyFlowTvTvov27Tvov31Batch") {
    group = "verification"
    description = "Runs the terminal TVOV27-TVOV31 convergence seal."
    dependsOn(
        "verifyStudyFlowTvFullNativeMaterialViewer",
        "verifyStudyFlowTvGoalProjectionActions",
        "verifyStudyFlowTvErrorNotebookMutationParity",
        "verifyStudyFlowTvDpadSflpSearch",
        "verifyStudyFlowTvAndroidWebSemanticParity",
    )
}

tasks.register<Exec>("verifyStudyFlowTvRoadmapInstructions") {
    group = "verification"
    description =
        "Checks the full Android TV roadmap, Devtool target rules, and structured-overlay " +
            "implementation preferences."
    commandLine("python3", "scripts/verify_tv_roadmap_instructions.py")
}

tasks.register<Exec>("verifySflp22Sflp23AccountBackupValidation") {
    group = "verification"
    description = "Checks SFLP22 account/backup handoff and SFLP23 release validation consolidation."
    dependsOn(
        "verifySflp20Sflp21ShareImportConflict",
        ":core:link:test",
        ":app-tv:testDebugUnitTest",
        ":desktopApp:test",
        ":app:testReleaseUnitTest",
    )
    commandLine("python3", "scripts/verify_sflp22_23_account_backup_validation.py")
}

tasks.register<Exec>("verifySflp24FinalReleaseSeal") {
    group = "verification"
    description = "Checks the final SFLP01-SFLP23 consolidated release validation seal."
    dependsOn(
        "verifySflp22Sflp23AccountBackupValidation",
        ":core:link:test",
        ":app-tv:testDebugUnitTest",
        ":desktopApp:test",
        ":app:testReleaseUnitTest",
    )
    commandLine("python3", "scripts/verify_sflp24_final_release_seal.py")
}

tasks.register<Exec>("verifyRel01TargetReleaseValidationRunner") {
    group = "verification"
    description = "Checks the post-SFLP target-machine release validation runner and evidence plan."
    commandLine("python3", "scripts/verify_rel01_target_release_validation_runner.py")
}

tasks.register<Exec>("verifyRel02ReleaseEvidenceTriage") {
    group = "verification"
    description = "Checks the REL02 release evidence exporter and failure triage bundle."
    dependsOn("verifyRel01TargetReleaseValidationRunner")
    commandLine("python3", "scripts/verify_rel02_release_evidence_triage.py")
}


tasks.register<Exec>("verifyRel03Rel04ArtifactInstallRollout") {
    group = "verification"
    description = "Checks REL03 signed artifact/install-smoke evidence and REL04 rollout manifest gating."
    dependsOn("verifyRel02ReleaseEvidenceTriage")
    commandLine("python3", "scripts/verify_rel03_04_artifact_install_rollout.py")
}


tasks.register<Exec>("verifyRel05Rel06ReleaseNotesRollback") {
    group = "verification"
    description = "Checks REL05 release-notes export and REL06 rollback package manifest generation."
    dependsOn("verifyRel03Rel04ArtifactInstallRollout")
    commandLine("python3", "scripts/verify_rel05_06_release_notes_rollback.py")
}


tasks.register<Exec>("verifyRel07Rel08FinalReleaseOpsSeal") {
    group = "verification"
    description = "Checks REL07 release checklist gate and REL08 final release-ops evidence seal."
    dependsOn("verifyRel05Rel06ReleaseNotesRollback")
    commandLine("python3", "scripts/verify_rel07_08_final_release_ops_seal.py")
}

tasks.register<Exec>("verifyRel09KotlinTestClasspathHotfix") {
    group = "verification"
    description = "Checks REL09 Kotlin test classpath hotfix wiring for core/link and desktopApp."
    commandLine("python3", "scripts/verify_rel09_kotlin_test_classpath_hotfix.py")
}



tasks.register<Exec>("verifyWeb20SharedSemanticGoldenVectors") {
    group = "verification"
    description = "Run the WEB20 language-neutral semantic golden-vector contract."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_web20_shared_semantic_golden_vectors.py")
}

tasks.register<Exec>("verifyWeb20RoadmapTruth") {
    group = "verification"
    description =
        "Verify WEB20 closes only the canonical roadmap/ledger track and leaves Presence/Parity ordering intact."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_roadmap01_cross_platform_truth_reconciliation.py")
}

tasks.register("verifyWeb20WebLint") {
    group = "verification"
    description = "Compatibility alias for the shared Web runtime/release-validation gate."
    dependsOn("verifyAttentionWebRuntimeParity", "verifyStudyFlowWebReleaseValidation")
}

tasks.register("verifyWeb20WebTests") {
    group = "verification"
    description = "Compatibility alias for the shared complete Web regression gate."
    dependsOn("verifyStudyFlowWebFullRegression")
}

tasks.register("verifyWeb20WebRegression") {
    group = "verification"
    description = "Run WEB20 Web type/runtime and regression checks."
    dependsOn("verifyWeb20WebLint", "verifyWeb20WebTests")
}

tasks.register("verifyWeb20WebRelease") {
    group = "verification"
    description = "Compatibility alias for the shared Web release-integrity gate."
    dependsOn("verifyStudyFlowWebReleaseIntegrity")
}

tasks.register("verifyWeb20TerminalSeal") {
    group = "verification"
    description = "Terminal WEB20 seal across Kotlin/core, Desktop, and TypeScript/Web."
    dependsOn(
        "verifyWeb20SharedSemanticGoldenVectors",
        "verifyWeb20RoadmapTruth",
        "verifyWeb20WebRegression",
        "verifyWeb20WebRelease",
        ":core:domain:test",
        ":core:link:test",
        ":core:portability:test",
        ":desktopApp:compileKotlin",
        ":desktopApp:test",
    )
}

tasks.register<Exec>("verifyPresence01PeerActivityPresence") {
    group = "verification"
    description =
        "Checks PRESENCE01 ephemeral multi-client peer/activity presence, capability visibility, " +
            "expiry, reconnect, and loop safety."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_presence01_peer_activity_presence.py")
}

tasks.register("verifyPresence01WebLint") {
    group = "verification"
    description = "Compatibility alias for the shared Web runtime/release-validation gate."
    dependsOn("verifyAttentionWebRuntimeParity", "verifyStudyFlowWebReleaseValidation")
}

tasks.register("verifyPresence01WebTests") {
    group = "verification"
    description = "Compatibility alias for the shared complete Web regression gate."
    dependsOn("verifyStudyFlowWebFullRegression")
}

tasks.register<Exec>("verifyPresence01LanRelayStability") {
    group = "verification"
    description = "Exercise the browser-TV LAN relay with load-tolerant startup/readiness handling."
    workingDir = file("web")
    commandLine(
        "python3",
        "-m",
        "unittest",
        "tests.test_wtv06_07_history_control_relay." +
            "Wtv0607HistoryControlRelayTest.test_lan_relay_pair_command_cast_revoke",
    )
}

tasks.register("verifyPresence01WebRegression") {
    group = "verification"
    description = "Run PRESENCE01 Web/Web-TV type/runtime and regression checks."
    dependsOn("verifyPresence01WebLint", "verifyPresence01WebTests", "verifyPresence01LanRelayStability")
}

tasks.register("verifyPresence01WebRelease") {
    group = "verification"
    description = "Compatibility alias for the shared Web release-integrity gate."
    dependsOn("verifyStudyFlowWebReleaseIntegrity")
}

tasks.register("verifyPresence01TerminalSeal") {
    group = "verification"
    description = "Terminal PRESENCE01 seal across shared link runtime and all five StudyFlow client surfaces."
    dependsOn(
        "verifyPresence01PeerActivityPresence",
        "verifyWeb20SharedSemanticGoldenVectors",
        "verifyWeb20RoadmapTruth",
        "verifyPresence01WebRegression",
        "verifyPresence01WebRelease",
        ":core:link:test",
        ":desktopApp:compileKotlin",
        ":desktopApp:test",
        ":app:compileDebugKotlin",
        ":app:testReleaseUnitTest",
        ":app:assembleDebug",
        ":app-tv:compileDebugKotlin",
        ":app-tv:testDebugUnitTest",
        ":app-tv:assembleDebug",
    )
}

tasks.register<Exec>("verifyParity04FiveClientFullMesh") {
    group = "verification"
    description =
        "Checks PARITY04 five-client full-mesh, convergence, exactly-once, capability, semantic, " +
            "and portability contracts."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_parity04_five_client_full_mesh.py")
}

tasks.register<Exec>("verifyParity04WebAcceptance") {
    group = "verification"
    description = "Runs the Web/Web-TV PARITY04 acceptance corpus against production runtimes."
    workingDir = file("web")
    commandLine("node", "tests/parity04_runner.mjs")
}

tasks.register("verifyParity04TerminalSeal") {
    group = "verification"
    description =
        "Terminal five-client PARITY04 acceptance seal across shared core, Desktop, Web, Android, and Android TV."
    dependsOn(
        "verifyParity04FiveClientFullMesh",
        "verifyParity04WebAcceptance",
        "verifyWeb20SharedSemanticGoldenVectors",
        "verifyPresence01PeerActivityPresence",
        "verifyPresence01WebRegression",
        "verifyPresence01WebRelease",
        ":core:domain:test",
        ":core:link:test",
        ":core:portability:test",
        ":desktopApp:compileKotlin",
        ":desktopApp:test",
        ":app:compileDebugKotlin",
        ":app:testReleaseUnitTest",
        ":app:assembleDebug",
        ":app-tv:compileDebugKotlin",
        ":app-tv:testDebugUnitTest",
        ":app-tv:assembleDebug",
    )
}

tasks.register<Exec>("verifyRelFinalRoadmapPromiseAudit") {
    group = "verification"
    description = "Checks the permanent REL-FINAL 213-verifier promise ledger and repaired product truth."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_rel_final_roadmap_promise_audit.py")
}

tasks.register<Exec>("runRelFinalExhaustiveRoadmapAudit") {
    group = "verification"
    description =
        "Standalone REL-FINAL exhaustive roadmap audit; run outside concurrent Gradle compilation."
    workingDir = rootProject.projectDir
    commandLine(
        "python3",
        "scripts/run_rel_final_roadmap_audit.py",
        "--timeout-seconds",
        "180",
        "--workers",
        "4",
    )
}

tasks.register<Exec>("verifyRelFinalDevtoolReleaseRunner") {
    group = "verification"
    description =
        "Checks that TV and REL-FINAL Android builds route through Devtool's Termux-safe Android runner."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_rel_final_devtool_release_runner.py")
}

tasks.register<Exec>("prepareRelFinalEvidencePlan") {
    group = "verification"
    description = "Prepares REL-FINAL target-host/device command templates without claiming release readiness."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/run_rel_final_release_campaign.py", "--mode", "plan")
}

tasks.register<Exec>("runRelFinalReleaseCampaign") {
    group = "verification"
    description = "Executes the real REL-FINAL target-host evidence campaign; fails until all evidence closes."
    workingDir = rootProject.projectDir
    commandLine(
        "python3",
        "scripts/run_rel_final_release_campaign.py",
        "--mode",
        "execute",
        "--channel",
        "production",
    )
}

tasks.register<Exec>("finalizeRelFinalReleaseCampaign") {
    group = "verification"
    description = "Rebuilds REL05-REL08 outputs and final verdict after real device/operator evidence is recorded."
    workingDir = rootProject.projectDir
    commandLine(
        "python3",
        "scripts/run_rel_final_release_campaign.py",
        "--mode",
        "finalize",
        "--channel",
        "production",
    )
}

tasks.register("verifyRelFinalTerminalSeal") {
    group = "verification"
    description = "Terminal product/static seal before the separate real REL-FINAL evidence execution campaign."
    dependsOn(
        "verifyRelFinalRoadmapPromiseAudit",
        "verifyRelFinalDevtoolReleaseRunner",
        "prepareRelFinalEvidencePlan",
        "verifyParity04TerminalSeal",
        "verifySflp24FinalReleaseSeal",
        "verifyRel07Rel08FinalReleaseOpsSeal",
        "verifyFinalSourceAcceptance",
    )
}


tasks.register<Exec>("verifyStudyFlowTvDevtoolAndroidRunner") {
    group = "verification"
    description = "Checks that the StudyFlow TV Devtool target uses the Android runner and TV-only debug tasks."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_tv_devtool_android_runner.py")
}

// ATT01 — Unified Attention Domain + SFLP Correctness Foundation
tasks.register<Exec>("verifyAtt01AttentionContracts") {
    group = "verification"
    description =
        "Checks ATT01 attention-domain, SFLP compatibility, persistence, migration, " +
            "diagnostics, and documentation contracts."
    commandLine("python3", "scripts/verify_att01_unified_attention_foundation.py")
}

// ATT12 v4 / OV01: preserve deterministic heavy-test ordering without projectsEvaluated.
// Task matching is lazy, so it remains compatible with configuration cache and plugin-created tasks.
project(":feature:settings").tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
    mustRunAfter(":feature:reminders:testDebugUnitTest")
}
project(":app").tasks.matching { it.name == "testReleaseUnitTest" }.configureEach {
    mustRunAfter(":feature:settings:testDebugUnitTest")
}
project(":app-tv").tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
    mustRunAfter(":app:testReleaseUnitTest")
}
project(":desktopApp").tasks.matching { it.name == "test" }.configureEach {
    mustRunAfter(":app-tv:testDebugUnitTest")
}

tasks.register<Exec>("verifyAttentionWebRuntimeParity") {
    group = "verification"
    description = "Runs the single shared TypeScript compile/runtime-parity gate for ATT01–ATT12."
    workingDir(rootProject.file("web"))
    commandLine("node", "scripts/compile_runtime.mjs")
    // Under Devtool --parallel, keep the only TypeScript compiler process away from the
    // memory-heavy Kotlin/JVM test workers. This avoids duplicate tsc processes and
    // preserves one authoritative runtime-parity result for every Attention seal.
    mustRunAfter(
        ":core:link:test",
        ":core:diagnostics:test",
        ":core:domain:test",
        ":feature:reminders:testDebugUnitTest",
        ":feature:settings:testDebugUnitTest",
        ":app:testReleaseUnitTest",
        ":app-tv:testDebugUnitTest",
        ":desktopApp:test",
    )
}

// Shared Web validation gates.  Historical WEB20/PRESENCE/ATT aliases delegate here so
// one Gradle invocation cannot spawn the same TypeScript/tests/release checks repeatedly.
tasks.register<Exec>("verifyStudyFlowWebFullRegression") {
    group = "verification"
    description = "Runs all Web pytest regressions once after the shared TypeScript runtime-parity gate."
    dependsOn("verifyAttentionWebRuntimeParity")
    workingDir(rootProject.file("web"))
    environment("STUDYFLOW_EXTERNAL_RUNTIME_PARITY", "1")
    commandLine("python3", "-m", "pytest", "tests", "-q")
}

tasks.register<Exec>("verifyStudyFlowWebReleaseValidation") {
    group = "verification"
    description = "Runs release-mode Web validation once for all terminal seals."
    dependsOn("verifyAttentionWebRuntimeParity")
    workingDir(rootProject.file("web"))
    commandLine("python3", "scripts/validate_web.py", "--release")
}

tasks.register<Exec>("verifyStudyFlowWebReleaseIntegrity") {
    group = "verification"
    description = "Checks committed Web release/cache/runtime integrity once for all terminal seals."
    dependsOn("verifyAttentionWebRuntimeParity")
    workingDir(rootProject.file("web"))
    commandLine("node", "scripts/check_release.mjs")
}

tasks.register("verifyStudyFlowWebReleaseGate") {
    group = "verification"
    description = "Shared full Web regression, release validation and release-integrity aggregate."
    dependsOn(
        "verifyStudyFlowWebFullRegression",
        "verifyStudyFlowWebReleaseValidation",
        "verifyStudyFlowWebReleaseIntegrity",
    )
}

tasks.register<Exec>("verifyAtt01WebAttentionRuntime") {
    group = "verification"
    description =
        "Checks ATT01 Web durable attention runtime behavior; TypeScript parity is shared " +
            "by the Attention seal."
    workingDir(rootProject.file("web"))
    commandLine(
        "python3", "-m", "unittest",
        "tests.test_att01_attention_runtime",
        "tests.test_sflp18_19_notification_media",
    )
}

tasks.named("verifyAtt01WebAttentionRuntime") {
    mustRunAfter("verifyAttentionWebRuntimeParity")
}

tasks.register("verifyAtt01UnifiedAttentionFoundation") {
    group = "verification"
    description =
        "Focused ATT01 production gate across shared, Android, Desktop, Web, and Android TV " +
            "attention foundations."
    dependsOn(
        "verifyAtt01AttentionContracts",
        "verifyAtt01WebAttentionRuntime",
        "verifyAttentionWebRuntimeParity",
        ":core:link:test",
        ":core:diagnostics:test",
        ":desktopApp:test",
        ":app:testReleaseUnitTest",
        ":app-tv:testDebugUnitTest",
    )
}

// ATT02 — Android Attention System 2.0
tasks.register<Exec>("verifyAtt02AndroidAttentionContracts") {
    group = "verification"
    description =
        "Checks ATT02 Android policy, scheduling, channels, actions, recovery, reliability, " +
            "diagnostics, tests and documentation contracts."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_att02_android_attention_system.py")
}

tasks.register("verifyAtt02AndroidAttentionSystem") {
    group = "verification"
    description = "Focused ATT02 production gate for the complete Android attention system slice."
    dependsOn(
        "verifyAtt02AndroidAttentionContracts",
        ":core:domain:test",
        ":feature:reminders:testDebugUnitTest",
        ":feature:settings:testDebugUnitTest",
        ":app:testReleaseUnitTest",
    )
}

// ATT03 — Android Widget Suite 2.0
tasks.register<Exec>("verifyAtt03AndroidWidgetContracts") {
    group = "verification"
    description =
        "Checks ATT03 widget modes, per-instance configuration, freshness scheduling, actions, " +
            "diagnostics, migration, tests and documentation contracts."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_att03_android_widget_suite.py")
}

tasks.register("verifyAtt03AndroidWidgetSuite") {
    group = "verification"
    description = "Focused ATT03 production gate for the complete Android Widget Suite 2.0 slice."
    dependsOn(
        "verifyAtt03AndroidWidgetContracts",
        ":app:testReleaseUnitTest",
    )
}


// ATT04 — Desktop Native Attention + Tray Product
tasks.register<Exec>("verifyAtt04DesktopAttentionContracts") {
    group = "verification"
    description =
        "Checks ATT04 Desktop scheduler, persistence, native notification/tray, recovery, settings, " +
            "diagnostics, tests and documentation contracts."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_att04_desktop_attention_runtime.py")
}

tasks.register("verifyAtt04DesktopAttentionRuntime") {
    group = "verification"
    description = "Focused ATT04 production gate for the complete Desktop native attention and tray slice."
    dependsOn(
        "verifyAtt04DesktopAttentionContracts",
        ":core:domain:test",
        ":core:link:test",
        ":desktopApp:compileKotlin",
        ":desktopApp:test",
    )
}

// ATT05 — Web/PWA Notification & Attention Runtime
tasks.register<Exec>("verifyAtt05WebAttentionContracts") {
    group = "verification"
    description =
        "Checks ATT05 Web/PWA attention runtime, Notification Center, service worker, Push foundation, " +
            "docs and source contracts."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_att05_web_pwa_attention_runtime.py")
}

tasks.register<Exec>("verifyAtt05WebAttentionLint") {
    group = "verification"
    description = "Run Web source/release validation without spawning a duplicate TypeScript compiler."
    workingDir = file("web")
    commandLine("python3", "scripts/validate_web.py", "--all")
}

tasks.register<Exec>("verifyAtt05WebAttentionTests") {
    group = "verification"
    description =
        "Run the complete Web regression suite; TypeScript parity is handled once by " +
            "verifyAttentionWebRuntimeParity."
    workingDir = file("web")
    environment("STUDYFLOW_EXTERNAL_RUNTIME_PARITY", "1")
    commandLine("python3", "-m", "unittest", "discover", "-s", "tests", "-p", "test_*.py")
}

tasks.named("verifyAtt05WebAttentionLint") {
    mustRunAfter("verifyAttentionWebRuntimeParity")
}
tasks.named("verifyAtt05WebAttentionTests") {
    mustRunAfter("verifyAttentionWebRuntimeParity")
}

tasks.register("verifyAtt05WebPwaAttentionRuntime") {
    group = "verification"
    description = "Focused ATT05 production gate for the complete Web/PWA notification and attention runtime slice."
    dependsOn(
        "verifyAtt05WebAttentionContracts",
        "verifyAtt05WebAttentionLint",
        "verifyAtt05WebAttentionTests",
        "verifyAttentionWebRuntimeParity",
        "verifyAtt01UnifiedAttentionFoundation",
        "verifySflp18Sflp19NotificationMediaSync",
    )
}



// ATT06 — Android TV + Web TV Attention Experience
tasks.register<Exec>("verifyAtt06TvAttentionContracts") {
    group = "verification"
    description = "Checks ATT06 Android TV/Web TV foreground attention, capability truth, docs and source contracts."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_att06_tv_attention_experience.py")
}

tasks.register<Exec>("verifyAtt06WebTvAttentionTests") {
    group = "verification"
    description = "Run the Web TV ATT06 foreground-attention regression tests."
    workingDir = file("web")
    commandLine("python3", "-m", "unittest", "tests/test_att06_web_tv_attention.py")
}

tasks.register("verifyAtt06TvAttentionExperience") {
    group = "verification"
    description = "Focused ATT06 production gate for the complete Android TV + Web TV attention experience slice."
    dependsOn(
        "verifyAtt06TvAttentionContracts",
        "verifyAtt06WebTvAttentionTests",
        "verifyAtt05WebPwaAttentionRuntime",
        "verifyAtt04DesktopAttentionRuntime",
        "verifyStudyFlowTvAndroidWebSemanticParity",
        ":app-tv:testDebugUnitTest",
    )
}


// ATT07 — Cross-Device Delivery Router
tasks.register<Exec>("verifyAtt07CrossDeviceDeliveryContracts") {
    group = "verification"
    description =
        "Checks ATT07 owner/mirror/suppression routing, device capability bridges, Web parity, " +
            "docs and source contracts."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_att07_cross_device_delivery_router.py")
}

tasks.register<Exec>("verifyAtt07WebDeliveryRouterTests") {
    group = "verification"
    description = "Run the Web ATT07 cross-device attention router regression tests."
    workingDir = file("web")
    commandLine("python3", "-m", "unittest", "tests/test_att07_attention_router.py")
}

tasks.register("verifyAtt07CrossDeviceDeliveryRouter") {
    group = "verification"
    description = "Focused ATT07 production gate for cross-device Attention delivery routing."
    dependsOn(
        "verifyAtt07CrossDeviceDeliveryContracts",
        "verifyAtt07WebDeliveryRouterTests",
        "verifyAtt06TvAttentionExperience",
        "verifyAtt05WebPwaAttentionRuntime",
        ":core:link:test",
        ":desktopApp:test",
        ":app:testReleaseUnitTest",
        ":app-tv:testDebugUnitTest",
    )
}


tasks.register<Exec>("verifyAtt08CompactCompanionAttentionContracts") {
    group = "verification"
    description = "Checks ATT08 compact companion routing, bridges, Web runtime parity and docs."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_att08_compact_companion_attention.py")
}

tasks.register("verifyAtt08CompactCompanionAttention") {
    group = "verification"
    description = "Focused ATT08 compact companion attention gate."
    dependsOn(
        "verifyAtt08CompactCompanionAttentionContracts",
        ":core:link:test",
        ":app-tv:testDebugUnitTest",
        ":desktopApp:test",
    )
}

// ATT09 — Calendar / Schedule Export Attention Integration
tasks.register<Exec>("verifyAtt09CalendarAttentionContracts") {
    group = "verification"
    description = "Checks ATT09 calendar export attention contracts, platform bridges, Web runtime parity and docs."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_att09_calendar_schedule_export_attention.py")
}

tasks.register<Exec>("verifyAtt09WebCalendarAttentionTests") {
    group = "verification"
    description = "Run the Web ATT09 calendar attention export regression tests."
    workingDir = file("web")
    commandLine("python3", "-m", "unittest", "tests/test_att09_calendar_attention.py")
}

tasks.register("verifyAtt09CalendarScheduleExportAttention") {
    group = "verification"
    description = "Focused ATT09 gate for calendar/schedule export attention integration."
    dependsOn(
        "verifyAtt09CalendarAttentionContracts",
        "verifyAtt09WebCalendarAttentionTests",
        "verifyAtt08CompactCompanionAttention",
        "verifyAtt07CrossDeviceDeliveryRouter",
        "verifyAtt06TvAttentionExperience",
        "verifyAtt05WebPwaAttentionRuntime",
        ":core:link:test",
        ":app:testReleaseUnitTest",
        ":desktopApp:test",
        ":app-tv:testDebugUnitTest",
    )
}

// ATT10 — Quiet Hours / Focus Profiles / Escalation Policy
tasks.register<Exec>("verifyAtt10QuietHoursFocusPolicyContracts") {
    group = "verification"
    description =
        "Checks ATT10 focus-policy domain, ATT01/ATT07 integration, platform projections, " +
            "Web parity and docs."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_att10_quiet_hours_focus_profiles_escalation_policy.py")
}

tasks.register<Exec>("verifyAtt10WebFocusPolicyTests") {
    group = "verification"
    description = "Runs ATT10 Web focus-policy, TV truthfulness, and delivery-router regression tests."
    workingDir = file("web")
    commandLine(
        "python3", "-m", "unittest",
        "tests/test_att10_focus_policy.py",
        "tests/test_att06_web_tv_attention.py",
        "tests/test_att07_attention_router.py",
    )
}

tasks.register("verifyAtt10QuietHoursFocusProfilesEscalationPolicy") {
    group = "verification"
    description = "Focused ATT10 gate for quiet hours, focus profiles, escalation and auditable routing policy."
    dependsOn(
        "verifyAtt10QuietHoursFocusPolicyContracts",
        "verifyAtt10WebFocusPolicyTests",
        "verifyAtt09CalendarScheduleExportAttention",
        ":core:link:test",
        ":app:testReleaseUnitTest",
        ":desktopApp:test",
        ":app-tv:testDebugUnitTest",
    )
}



// ATT11 — Notification Reliability Evidence / Delivery Audit
tasks.register<Exec>("verifyAtt11NotificationReliabilityContracts") {
    group = "verification"
    description = "Checks ATT11 canonical ledger evidence, route audit, platform truth, exports and docs."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_att11_notification_reliability_evidence_audit.py")
}

tasks.register<Exec>("verifyAtt11WebReliabilityTests") {
    group = "verification"
    description = "Runs ATT11 Web reliability/export plus ATT01/05/06/07/10 attention regressions."
    workingDir = file("web")
    commandLine(
        "python3", "-m", "unittest",
        "tests/test_att11_attention_reliability.py",
        "tests/test_att10_focus_policy.py",
        "tests/test_att07_attention_router.py",
        "tests/test_att06_web_tv_attention.py",
        "tests/test_att05_web_attention_runtime.py",
        "tests/test_att01_attention_runtime.py",
    )
}

tasks.register("verifyAtt11NotificationReliabilityEvidenceAudit") {
    group = "verification"
    description =
        "Focused ATT11 gate for reliability evidence, delivery analytics, cross-device route " +
            "audit and export."
    dependsOn(
        "verifyAtt11NotificationReliabilityContracts",
        "verifyAtt11WebReliabilityTests",
        "verifyAtt10QuietHoursFocusProfilesEscalationPolicy",
        ":core:link:test",
        ":app:testReleaseUnitTest",
        ":desktopApp:test",
        ":app-tv:testDebugUnitTest",
    )
}

// ATT12 — Attention Release Final Seal / Roadmap Reconciliation
tasks.register<Exec>("verifyAtt12AttentionReleaseContracts") {
    group = "verification"
    description =
        "Checks ATT01–ATT12 roadmap promises, production reachability, capability truth, final docs " +
            "and validation wiring."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_att12_attention_release_final_seal.py")
}

tasks.register("verifyAtt12WebAttentionReleaseTests") {
    group = "verification"
    description = "Compatibility alias for the complete shared Web regression gate."
    dependsOn("verifyStudyFlowWebFullRegression")
}

tasks.register("verifyAtt12WebRuntimeParity") {
    group = "verification"
    description = "Compatibility alias for the single shared TypeScript runtime-parity gate."
    dependsOn("verifyAttentionWebRuntimeParity")
}

tasks.register("verifyAtt12WebReleaseValidation") {
    group = "verification"
    description = "Compatibility alias for shared Web release validation."
    dependsOn("verifyStudyFlowWebReleaseValidation")
}

tasks.register("verifyAtt12WebReleaseIntegrity") {
    group = "verification"
    description = "Compatibility alias for shared Web release integrity."
    dependsOn("verifyStudyFlowWebReleaseIntegrity")
}


tasks.register("verifyAttentionAllContracts") {
    group = "verification"
    description = "Runs ATT01-ATT12 source/product contract verifiers without recursively repeating focused test lanes."
    dependsOn(
        "verifyAtt01AttentionContracts",
        "verifyAtt02AndroidAttentionContracts",
        "verifyAtt03AndroidWidgetContracts",
        "verifyAtt04DesktopAttentionContracts",
        "verifyAtt05WebAttentionContracts",
        "verifyAtt06TvAttentionContracts",
        "verifyAtt07CrossDeviceDeliveryContracts",
        "verifyAtt08CompactCompanionAttentionContracts",
        "verifyAtt09CalendarAttentionContracts",
        "verifyAtt10QuietHoursFocusPolicyContracts",
        "verifyAtt11NotificationReliabilityContracts",
        "verifyAtt12AttentionReleaseContracts",
    )
}

tasks.register("verifyAtt12AttentionReleaseFinalSeal") {
    group = "verification"
    description = "Optimized ATT01-ATT12 final seal: contracts once, Web once, native suites once."
    dependsOn(
        "verifyAttentionAllContracts",
        "verifyStudyFlowWebReleaseGate",
        "verifySflp18Sflp19NotificationMediaContracts",
        "verifyWeb20SharedSemanticGoldenVectors",
        ":core:link:test",
        ":core:diagnostics:test",
        ":core:domain:test",
        ":feature:reminders:testDebugUnitTest",
        ":feature:settings:testDebugUnitTest",
        ":app:testReleaseUnitTest",
        ":desktopApp:test",
        ":app-tv:testDebugUnitTest",
    )
}

// Post-ATT12 Attention roadmap completion gate — production reachability, not token existence.
tasks.register<Exec>("verifyAttentionRoadmapPostsealContracts") {
    group = "verification"
    description = "Rejects the production-reachability gaps found by the post-ATT12 Attention roadmap audit."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_attention_roadmap_postseal_completion.py")
}

tasks.register("verifyAttentionRoadmapFullWebRegression") {
    group = "verification"
    description = "Compatibility alias for the complete shared Web regression gate."
    dependsOn("verifyStudyFlowWebFullRegression")
}

tasks.register("verifyAttentionRoadmapPostsealCompletion") {
    group = "verification"
    description = "Terminal optimized ATT01-ATT12 roadmap gate with production reachability and task-graph checks."
    dependsOn(
        "verifyAtt12AttentionReleaseFinalSeal",
        "verifyAttentionRoadmapPostsealContracts",
        "verifyStudyFlowGradleTaskGraphOptimizationV4",
    )
}

// OV01 — repository/Gradle/Devtool truth baseline.
// Lock every resolvable project configuration. Lock state is refreshed intentionally with
// `./gradlew --write-locks ...`; ordinary builds consume the committed state when present.
allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}

val repositoryVerificationInputs = files(
    fileTree(rootDir) {
        include(
            "*.gradle.kts",
            "*.properties",
            "*.toml",
            "*.md",
            "gradle/**",
            "build-logic/**",
            "scripts/**",
            "tests/**",
            "app/**",
            "app-tv/**",
            "core/**",
            "feature/**",
            "desktopApp/**",
            "web/**",
            "protocol/**",
            "docs/**",
        )
        exclude(
            "**/build/**",
            "**/.gradle/**",
            "**/.idea/**",
            "**/.kotlin/**",
            "**/node_modules/**",
            ".devtool/**",
        )
    },
)

val volatileVerifierNameFragments = listOf(
    "Artifact",
    "Evidence",
    "Device",
    "Physical",
    "Campaign",
    "Verdict",
    "Packaged",
    "Install",
    "Rollout",
    "Connected",
    "Release",
    "Runtime",
    "External",
)

// Give every root verification Exec an explicit, reproducible source-input boundary and a
// unique stamp output. Pure source verifiers become UP-TO-DATE/cacheable; runtime/evidence
// verifiers retain declared I/O but deliberately execute every time.
tasks.withType<Exec>().configureEach {
    if (project == rootProject && group == "verification") {
        val originalCommand = commandLine.map(Any::toString)
        val alreadyWrapped = originalCommand.getOrNull(1)?.endsWith("run_gradle_verifier.py") == true
        if (originalCommand.isNotEmpty() && !alreadyWrapped) {
            val stampFile = layout.buildDirectory.file("verification-stamps/$name.ok")
            inputs.files(repositoryVerificationInputs)
                .withPropertyName("repositoryVerificationInputs")
                .withPathSensitivity(PathSensitivity.RELATIVE)
            inputs.property("verificationCommand", originalCommand.joinToString("\u0000"))
            outputs.file(stampFile).withPropertyName("verificationStamp")

            val scriptPath = originalCommand.getOrNull(1).orEmpty()
            val isPureSourceVerifier =
                originalCommand.firstOrNull() == "python3" &&
                    scriptPath.startsWith("scripts/verify_") &&
                    volatileVerifierNameFragments.none(name::contains)
            if (isPureSourceVerifier) {
                outputs.cacheIf("source-only repository verifier") { true }
            } else {
                outputs.upToDateWhen { false }
                outputs.cacheIf("runtime, report-producing, or environment-sensitive verifier") { false }
            }

            commandLine(
                listOf(
                    "python3",
                    rootProject.file("scripts/run_gradle_verifier.py").absolutePath,
                    "--stamp",
                    stampFile.get().asFile.absolutePath,
                    "--",
                ) + originalCommand,
            )
        }
    }
}

tasks.register<Exec>("verifyGradleDependencyIntegrityPolicy") {
    group = "verification"
    description = "Checks Gradle dependency locking, pinned wrapper provenance, and SHA-256 materialization policy."
    commandLine("python3", "scripts/verify_gradle_dependency_integrity.py")
}

tasks.register<Exec>("verifyOv01RepositoryGradleDevtoolTruth") {
    group = "verification"
    description = "Behaviorally checks OV01 repository, Gradle, Devtool, wrapper, dependency and metadata contracts."
    dependsOn("verifyGradleDependencyIntegrityPolicy")
    commandLine("python3", "scripts/verify_ov01_repository_gradle_devtool_truth.py")
}

// OV09 — SFLP native/Web interoperability transport.
tasks.register<Exec>("verifyOv09SflpNativeWebInterop") {
    group = "verification"
    description = "Validates OV09 native/Web SFLP WebSocket, pairing, journal, identity and routed-RPC interoperability."
    dependsOn(
        ":core:link:test",
        ":app:compileDebugKotlin",
        ":app-tv:compileDebugKotlin",
        ":desktopApp:compileKotlin",
    )
    commandLine("bash", "scripts/validate_ov09_sflp_native_web_interop.sh")
}


// OV10 — SFLP trust, pairing, authorization & transport security.
tasks.register<Exec>("verifyOv10SflpTrustSecurity") {
    group = "verification"
    description = "Validates OV10 SFLP trust, encrypted pairing, protocol/capability authorization and LAN credential hardening."
    dependsOn(
        ":core:link:test",
        ":core:tvcontrol:test",
        ":core:tvlink:compileDebugKotlin",
        ":app:compileDebugKotlin",
        ":app-tv:compileDebugKotlin",
        ":desktopApp:compileKotlin",
    )
    commandLine("bash", "scripts/validate_ov10_sflp_security.sh")
}


// OV11 — SFLP reconciliation, frontier, retry & corruption recovery.
tasks.register<Exec>("verifyOv11SflpReconciliationRecovery") {
    group = "verification"
    description = "Validates OV11 contiguous frontiers, persisted retry gating, peer isolation, corruption recovery and real browser/native interop."
    dependsOn(
        ":core:link:test",
        ":desktopApp:test",
        ":app:compileDebugKotlin",
        ":app-tv:compileDebugKotlin",
    )
    commandLine("bash", "scripts/validate_ov11_sflp_reconciliation_recovery.sh")
}


// OV12 — SFLP live session, projection, material, share & backup RPC semantics.
tasks.register<Exec>("verifyOv12SflpAdvancedRpcSemantics") {
    group = "verification"
    description = "Validates OV12 advanced SFLP RPC semantics for live-session fencing, projection, material streaming, share/import, Web-TV input and account/backup targets."
    dependsOn(
        ":core:link:test",
        ":core:tvcontrol:test",
        ":core:tvlink:compileDebugKotlin",
        ":app:compileDebugKotlin",
        ":app-tv:compileDebugKotlin",
        ":desktopApp:compileKotlin",
    )
    commandLine("bash", "scripts/validate_ov12_sflp_advanced_rpc_semantics.sh")
}

// OV13 — Android peer lifecycle & LAN discovery recovery.
tasks.register<Exec>("verifyOv13AndroidPeerLifecycleLanRecovery") {
    group = "verification"
    description = "Validates OV13 Android peer lifecycle, LAN permission gating, restartable NSD/discovery, IPv6 scoped endpoints, QR reachability, and SAF grant cleanup."
    dependsOn(
        ":core:link:test",
        ":core:tvlink:compileDebugKotlin",
        ":app:compileDebugKotlin",
        ":app:testReleaseUnitTest",
    )
    commandLine("bash", "scripts/validate_ov13_android_peer_lifecycle_lan_recovery.sh")
}

// OV14 — Android receiver, deep-link, widget & device-target durability.
tasks.register<Exec>("verifyOv14AndroidReceiverWidgetDurability") {
    group = "verification"
    description = "Validates OV14 receiver-bound TV sessions, durable reminder/widget broadcast handling, deep-link persistence, configured-plan widgets, and collision-proof domain action keys."
    dependsOn(
        ":core:tvcontrol:test",
        ":core:tvlink:compileDebugKotlin",
        ":app:compileDebugKotlin",
        ":app:testReleaseUnitTest",
        ":app-tv:compileDebugKotlin",
    )
    commandLine("bash", "scripts/validate_ov14_android_receiver_widget_durability.sh")
}


// OV15 — Android product-flow and UI/domain honesty.
tasks.register<Exec>("verifyOv15AndroidProductFlowHonesty") {
    group = "verification"
    description = "Validates OV15 Android question editor/practice honesty, active-session plan guards, partial load states, local date countdowns and bounded imports."
    dependsOn(
        ":feature:practice:testDebugUnitTest",
        ":feature:plans:testDebugUnitTest",
        ":feature:workspace:testDebugUnitTest",
        ":feature:subjects:testDebugUnitTest",
        ":app:compileDebugKotlin",
    )
    commandLine("bash", "scripts/validate_ov15_android_product_flow_honesty.sh")
}


// OV16 — Attention scheduler, quiet-hours & durable wakeups.
tasks.register<Exec>("verifyOv16AttentionSchedulerDurableWakeups") {
    group = "verification"
    description = "Validates OV16 ATT10-first quiet-hours rearm, mirror escalation wakeups, future review alarms, exact session/widget scheduling and permission-denied delivery truth."
    dependsOn(
        ":core:link:test",
        ":core:domain:test",
        ":app:compileDebugKotlin",
        ":app:testReleaseUnitTest",
    )
    commandLine("bash", "scripts/validate_ov16_attention_scheduler_wakeups.sh")
}


// OV17 — Attention delivery, widget resilience & remote policy.
tasks.register<Exec>("verifyOv17AttentionDeliveryWidgetRemotePolicy") {
    group = "verification"
    description = "Validates OV17 per-widget isolation, recipient ATT10 remote delivery policy, timezone refresh, expired snooze purge, stable notification IDs and self-healing invalidation observers."
    dependsOn(
        ":core:link:test",
        ":core:domain:test",
        ":app:compileDebugKotlin",
        ":app:testReleaseUnitTest",
    )
    commandLine("bash", "scripts/validate_ov17_attention_delivery_widget_remote_policy.sh")
}


tasks.register<Exec>("verifyOv18NativeTvDurableStateActionJournal") {
    group = "verification"
    description = "Validates OV18 native TV durable command sequencing, outbox persistence, explicit backpressure, wall-clock-free admission, UI-state checkpoints and commit-before-success pairing."
    dependsOn(
        ":core:tvcontrol:test",
        ":core:tvlink:compileDebugKotlin",
        ":app-tv:compileDebugKotlin",
        ":app-tv:testDebugUnitTest",
    )
    commandLine("bash", "scripts/validate_ov18_native_tv_durable_state_action_journal.sh")
}


// OV19 — Native TV practice, mock, review & schedule truth.
tasks.register<Exec>("verifyOv19NativeTvPracticeMockReviewScheduleTruth") {
    group = "verification"
    description = "Validates OV19 native TV practice reachability, mock execution semantics, review grade intent, schedule labels and canonical source IDs."
    dependsOn(
        ":core:domain:test",
        ":core:tvcontrol:test",
        ":app:compileDebugKotlin",
        ":app:testReleaseUnitTest",
        ":app-tv:compileDebugKotlin",
        ":app-tv:testDebugUnitTest",
    )
    commandLine("bash", "scripts/validate_ov19_native_tv_practice_mock_review_schedule_truth.sh")
}

// OV20 — Native TV materials, focus, layout & projection resilience.
tasks.register<Exec>("verifyOv20NativeTvMaterialsFocusProjectionResilience") {
    group = "verification"
    description = "Validates OV20 TV-transferable materials, renderer-backed PDF navigation, captured media playback position, dynamic focus wiring, scroll-safe rows and failure-isolated presentation feeds."
    dependsOn(
        ":core:tvcontrol:test",
        ":app:compileDebugKotlin",
        ":app-tv:compileDebugKotlin",
        ":app-tv:testDebugUnitTest",
    )
    commandLine("bash", "scripts/validate_ov20_native_tv_materials_focus_projection_resilience.sh")
}

// OV21 — Unified TV control, pairing, relay, cast & attention plane.
tasks.register<Exec>("verifyOv21UnifiedTvControlPlane") {
    group = "verification"
    description = "Validates OV21 unified TV control plane envelopes across local TCP, browser relay, cast, domain acknowledgements and TV attention ingress."
    dependsOn(
        ":core:tvcontrol:test",
        ":core:tvlink:compileDebugKotlin",
        ":app:compileDebugKotlin",
        ":app-tv:compileDebugKotlin",
    )
    commandLine("bash", "scripts/validate_ov21_unified_tv_control_plane.sh")
}
// OV22 — Desktop persistence, atomicity, restore & file safety.
tasks.register<Exec>("verifyOv22DesktopPersistenceAtomicRestoreFileSafety") {
    group = "verification"
    description = "Validates OV22 Desktop nonblocking UI actions, referential cleanup, atomic linked commits, point-in-time restore reload, schema migration, workspace locking and file secrecy."
    dependsOn(
        ":core:domain:test",
        ":core:portability:test",
        ":desktopApp:compileKotlin",
        ":desktopApp:test",
    )
    commandLine("bash", "scripts/validate_ov22_desktop_persistence_atomic_restore_file_safety.sh")
}

// OV23 — Desktop runtime, UX, tray & SFLP correctness.
tasks.register<Exec>("verifyOv23DesktopRuntimeUxSflpCorrectness") {
    group = "verification"
    description = "Validates OV23 Desktop runtime copy, tray action isolation, SFLP endpoint truth, retry-aware sync status and local-time labels."
    dependsOn(
        ":core:link:test",
        ":desktopApp:compileKotlin",
        ":desktopApp:test",
    )
    commandLine("bash", "scripts/validate_ov23_desktop_runtime_ux_sflp_correctness.sh")
}

// OV24 — Web persistence, bootstrap, portable import & secret hygiene.
tasks.register<Exec>("verifyOv24WebPersistenceBootstrapPortableSecretHygiene") {
    group = "verification"
    description = "Validates OV24 IndexedDB durability/CAS, visible bootstrap failures, BFCache recovery, bounded Web imports, attachment cleanup, async success truth, portable focus sanitization and password hygiene."
    commandLine("bash", "scripts/validate_ov24_web_persistence_bootstrap_portable_secret_hygiene.sh")
}

// OV25 — Web attention, push, Web-TV timing & route safety.
tasks.register<Exec>("verifyOv25WebAttentionPushTvTimingRouteSafety") {
    group = "verification"
    description = "Validates OV25 relay-backed Web Push truth, durable Browser-TV mock checkpoints, pairing throttling, acknowledgement-based attention drain, notification admission, terminal action fencing, internal routes and visible foreground fallback."
    commandLine("bash", "scripts/validate_ov25_web_attention_push_tv_timing_route_safety.sh")
}

// OV28 — Language review correctness & Desktop specialized parity.
tasks.register<Exec>("verifyOv28LanguageReviewDesktopSpecializedParity") {
    group = "verification"
    description = "Validates OV28 language review correctness, deterministic choice ordering and Desktop specialized graph persistence/execution parity."
    commandLine("bash", "scripts/validate_ov28_language_review_desktop_specialized_parity.sh")
}

// OV26 — Specialized content production data & UI.
tasks.register<Exec>("verifyOv26SpecializedContentDataUi") {
    group = "verification"
    description = "Validates OV26 Room-backed specialized repositories, UI snapshots, safe command templates, analytics and import apply workflows."
    commandLine("bash", "scripts/validate_ov26_specialized_content_data_ui.sh")
}

// OV27 — Termux execution pipeline & sandbox correctness.
tasks.register<Exec>("verifyOv27TermuxExecutionPipelineSandboxCorrectness") {
    group = "verification"
    description = "Validates OV27 four-stage durable Termux execution, quote-safe tokenization, workspace confinement and exact request/result correlation."
    commandLine("bash", "scripts/validate_ov27_termux_execution_pipeline_sandbox_correctness.sh")
}

tasks.register<Exec>("verifyOv29ExhaustiveVerifierInventory") {
    group = "verification"
    description = "Builds and checks the exhaustive OV29 verifier inventory against the optimized Gradle/Devtool graph."
    commandLine("python3", "scripts/verify_ov29_exhaustive_verifier_inventory.py")
}

tasks.register<Exec>("verifyWebStaleDistReleaseIntegrity") {
    group = "verification"
    description = "Rejects stale Web release dist assets whose runtime hashes no longer match the compiled runtime output."
    commandLine("python3", "scripts/verify_web_stale_dist_release_integrity.py")
}

// OV29 — Behavioral validation, debug labs & release-truth gates.
tasks.register<Exec>("verifyOv29BehavioralValidationReleaseTruthGates") {
    group = "verification"
    description = "Validates OV29 behavioral CMOV gates, Debug Workbench lab semantics, exhaustive verifier inventory, Web dist freshness and release-truth wiring."
    dependsOn("verifyOv29ExhaustiveVerifierInventory", "verifyWebStaleDistReleaseIntegrity")
    commandLine("bash", "scripts/validate_ov29_behavioral_validation_release_truth_gates.sh")
}

// OV30 — Android client terminal target seal.
tasks.register<Exec>("verifyOv30StudyflowAndroidTargetSeal") {
    group = "verification"
    description = "Android client terminal target seal."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_ov30_studyflow_android_target_seal.py")
}

// OV30 — Desktop client terminal target seal.
tasks.register<Exec>("verifyOv30StudyflowDesktopTargetSeal") {
    group = "verification"
    description = "Desktop client terminal target seal."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_ov30_studyflow_desktop_target_seal.py")
}

// OV30 — Web/PWA terminal target seal.
tasks.register<Exec>("verifyOv30StudyflowWebTargetSeal") {
    group = "verification"
    description = "Web/PWA terminal target seal."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_ov30_studyflow_web_target_seal.py")
}

// OV30 — Android TV debug terminal target seal.
tasks.register<Exec>("verifyOv30StudyflowTvTargetSeal") {
    group = "verification"
    description = "Android TV debug terminal target seal."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_ov30_studyflow_tv_target_seal.py")
}

// OV30 — Android release evidence target seal.
tasks.register<Exec>("verifyOv30StudyflowReleaseTargetSeal") {
    group = "verification"
    description = "Android release evidence target seal."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_ov30_studyflow_release_target_seal.py")
}

// OV30 — Android TV release evidence target seal.
tasks.register<Exec>("verifyOv30StudyflowTvReleaseTargetSeal") {
    group = "verification"
    description = "Android TV release evidence target seal."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_ov30_studyflow_tv_release_target_seal.py")
}

// OV30 — Quality target terminal seal.
tasks.register<Exec>("verifyOv30StudyflowQualityTargetSeal") {
    group = "verification"
    description = "Quality target terminal seal."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_ov30_studyflow_quality_target_seal.py")
}

// OV30 — Parity aggregate terminal target seal.
tasks.register<Exec>("verifyOv30StudyflowParityTargetSeal") {
    group = "verification"
    description = "Parity aggregate terminal target seal."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_ov30_studyflow_parity_target_seal.py")
}

// OV30 — OV30 one-overlay-per-target aggregate split seal.
tasks.register<Exec>("verifyOv30AllTargetOverlaySplit") {
    group = "verification"
    description = "OV30 one-overlay-per-target aggregate split seal."
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/verify_ov30_all_target_overlay_split.py")
}

// OV30R2 — OV30R2 studyflow_android proper validation seal.
tasks.register<Exec>("verifyOv30R2StudyflowAndroidProperValidationSeal") {
    group = "verification"
    description = "OV30R2 studyflow_android proper validation seal."
    workingDir = rootProject.projectDir
    commandLine("bash", "scripts/validate_ov30r2_studyflow_android_proper_validation_seal.sh")
}

// OV30R2 — OV30R2 studyflow_desktop proper validation seal.
tasks.register<Exec>("verifyOv30R2StudyflowDesktopProperValidationSeal") {
    group = "verification"
    description = "OV30R2 studyflow_desktop proper validation seal."
    workingDir = rootProject.projectDir
    commandLine("bash", "scripts/validate_ov30r2_studyflow_desktop_proper_validation_seal.sh")
}

// OV30R2 — OV30R2 studyflow_web proper validation seal.
tasks.register<Exec>("verifyOv30R2StudyflowWebProperValidationSeal") {
    group = "verification"
    description = "OV30R2 studyflow_web proper validation seal."
    workingDir = rootProject.projectDir
    commandLine("bash", "scripts/validate_ov30r2_studyflow_web_proper_validation_seal.sh")
}

// OV30R2 — OV30R2 studyflow_tv proper validation seal.
tasks.register<Exec>("verifyOv30R2StudyflowTvProperValidationSeal") {
    group = "verification"
    description = "OV30R2 studyflow_tv proper validation seal."
    workingDir = rootProject.projectDir
    commandLine("bash", "scripts/validate_ov30r2_studyflow_tv_proper_validation_seal.sh")
}

// OV30R2 — OV30R2 studyflow_release proper validation seal.
tasks.register<Exec>("verifyOv30R2StudyflowReleaseProperValidationSeal") {
    group = "verification"
    description = "OV30R2 studyflow_release proper validation seal."
    workingDir = rootProject.projectDir
    commandLine("bash", "scripts/validate_ov30r2_studyflow_release_proper_validation_seal.sh")
}

// OV30R2 — OV30R2 studyflow_tv_release proper validation seal.
tasks.register<Exec>("verifyOv30R2StudyflowTvReleaseProperValidationSeal") {
    group = "verification"
    description = "OV30R2 studyflow_tv_release proper validation seal."
    workingDir = rootProject.projectDir
    commandLine("bash", "scripts/validate_ov30r2_studyflow_tv_release_proper_validation_seal.sh")
}

// OV30R2 — OV30R2 studyflow_quality proper validation seal.
tasks.register<Exec>("verifyOv30R2StudyflowQualityProperValidationSeal") {
    group = "verification"
    description = "OV30R2 studyflow_quality proper validation seal."
    workingDir = rootProject.projectDir
    commandLine("bash", "scripts/validate_ov30r2_studyflow_quality_proper_validation_seal.sh")
}

// OV30R2 — OV30R2 studyflow_parity proper validation seal.
tasks.register<Exec>("verifyOv30R2StudyflowParityProperValidationSeal") {
    group = "verification"
    description = "OV30R2 studyflow_parity proper validation seal."
    workingDir = rootProject.projectDir
    commandLine("bash", "scripts/validate_ov30r2_studyflow_parity_proper_validation_seal.sh")
}

// OV30R2 — all target proper validation seals.
tasks.register("verifyOv30R2AllTargetProperValidationSeals") {
    group = "verification"
    description = "Runs every OV30R2 per-target proper validation seal."
    dependsOn("verifyOv30R2StudyflowAndroidProperValidationSeal", "verifyOv30R2StudyflowDesktopProperValidationSeal", "verifyOv30R2StudyflowWebProperValidationSeal", "verifyOv30R2StudyflowTvProperValidationSeal", "verifyOv30R2StudyflowReleaseProperValidationSeal", "verifyOv30R2StudyflowTvReleaseProperValidationSeal", "verifyOv30R2StudyflowQualityProperValidationSeal", "verifyOv30R2StudyflowParityProperValidationSeal")
}


// "app-tv" is an explicit quality source root.
