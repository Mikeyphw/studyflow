package com.mikeyphw.studyflow.core.model

enum class StudySyncProviderSetupAction {
    KEEP_OFFLINE,
    CHOOSE_LOCAL_FOLDER,
    ENTER_WEBDAV_DETAILS,
    TEST_PROVIDER_CONNECTION,
    SAVE_PROVIDER,
    CLEAR_PROVIDER,
}

enum class StudySyncProviderSetupIssue {
    UNSUPPORTED_PROVIDER,
    FOLDER_URI_REQUIRED,
    ENDPOINT_REQUIRED,
    HTTPS_REQUIRED,
    USERINFO_FORBIDDEN,
    CREDENTIAL_HANDLE_REQUIRED,
    REMOTE_ENCRYPTION_REQUIRED,
}

data class StudySyncProviderSetupValidation(
    val providerId: String,
    val issues: List<StudySyncProviderSetupIssue>,
) {
    val valid: Boolean = issues.isEmpty()
    val canSave: Boolean = valid
}

data class StudySyncSecretHandle(
    val alias: String,
    val createdAtEpochMillis: Long,
) {
    init {
        require(alias.isNotBlank()) { "Secret alias cannot be blank." }
        require(createdAtEpochMillis >= 0L) { "Secret creation time cannot be negative." }
    }

    val diagnosticLabel: String = "secret-handle:${alias.takeLast(6)}"
}

data class StudySyncProviderDiagnostics(
    val providerId: String,
    val displayLocation: String?,
    val usernameLabel: String?,
    val credentialConfigured: Boolean,
    val secretLabel: String?,
) {
    override fun toString(): String = listOfNotNull(
        "provider=$providerId",
        displayLocation?.let { location -> "location=$location" },
        usernameLabel?.let { username -> "username=$username" },
        "credentialConfigured=$credentialConfigured",
        secretLabel?.let { secret -> "secret=$secret" },
    ).joinToString(", ")
}

data class StudySyncLocalFolderSetup(
    val folderUri: String,
) {
    init {
        require(folderUri.isNotBlank()) { "Folder URI cannot be blank." }
    }

    val diagnostics: StudySyncProviderDiagnostics = StudySyncProviderDiagnostics(
        providerId = StudyFlowSyncProviderIds.LOCAL_FOLDER,
        displayLocation = StudySyncProviderSetupPolicy.displayLocation(folderUri),
        usernameLabel = null,
        credentialConfigured = false,
        secretLabel = null,
    )
}

data class StudySyncWebDavSetup(
    val endpointUrl: String,
    val username: String,
    val credentialHandle: StudySyncSecretHandle?,
    val displayName: String = "WebDAV / Nextcloud",
    val allowHttpForDebug: Boolean = false,
) {
    init {
        require(endpointUrl.isNotBlank()) { "WebDAV endpoint cannot be blank." }
        require(username.isNotBlank()) { "WebDAV username cannot be blank." }
        require(displayName.isNotBlank()) { "WebDAV display name cannot be blank." }
    }

    val normalizedEndpoint: String = StudySyncProviderSetupPolicy.normalizeEndpoint(endpointUrl)
    val diagnostics: StudySyncProviderDiagnostics = StudySyncProviderSetupPolicy.diagnosticsFor(this)
}

object StudySyncProviderSetupPolicy {
    fun actions(settings: StudyFlowSyncSettings): List<StudySyncProviderSetupAction> = listOf(
        StudySyncProviderSetupAction.KEEP_OFFLINE,
        StudySyncProviderSetupAction.CHOOSE_LOCAL_FOLDER,
        StudySyncProviderSetupAction.ENTER_WEBDAV_DETAILS,
        StudySyncProviderSetupAction.TEST_PROVIDER_CONNECTION,
        StudySyncProviderSetupAction.SAVE_PROVIDER,
        StudySyncProviderSetupAction.CLEAR_PROVIDER,
    ).filter { action ->
        when (action) {
            StudySyncProviderSetupAction.TEST_PROVIDER_CONNECTION,
            StudySyncProviderSetupAction.SAVE_PROVIDER -> settings.providerConfigured
            StudySyncProviderSetupAction.CLEAR_PROVIDER -> settings.providerConfigured
            else -> true
        }
    }

    fun validateLocalFolder(setup: StudySyncLocalFolderSetup): StudySyncProviderSetupValidation {
        val issues = buildList {
            if (!setup.folderUri.trim().startsWith("content://")) {
                add(StudySyncProviderSetupIssue.FOLDER_URI_REQUIRED)
            }
        }
        return StudySyncProviderSetupValidation(StudyFlowSyncProviderIds.LOCAL_FOLDER, issues)
    }

    fun validateWebDav(setup: StudySyncWebDavSetup): StudySyncProviderSetupValidation {
        val endpoint = normalizeEndpoint(setup.endpointUrl)
        val issues = buildList {
            if (endpoint.isBlank()) add(StudySyncProviderSetupIssue.ENDPOINT_REQUIRED)
            if (!setup.allowHttpForDebug && !endpoint.startsWith("https://", ignoreCase = true)) {
                add(StudySyncProviderSetupIssue.HTTPS_REQUIRED)
            }
            if (endpointUserInfoPresent(endpoint)) add(StudySyncProviderSetupIssue.USERINFO_FORBIDDEN)
            if (setup.credentialHandle == null) add(StudySyncProviderSetupIssue.CREDENTIAL_HANDLE_REQUIRED)
        }
        return StudySyncProviderSetupValidation(StudyFlowSyncProviderIds.WEBDAV, issues)
    }

    fun validateSettingsForRemoteProvider(settings: StudyFlowSyncSettings): StudySyncProviderSetupValidation {
        val issues = buildList {
            if (settings.providerId !in setOf(StudyFlowSyncProviderIds.WEBDAV, StudyFlowSyncProviderIds.GOOGLE_DRIVE)) {
                add(StudySyncProviderSetupIssue.UNSUPPORTED_PROVIDER)
            }
            if (!settings.encryptRemoteBackups) add(StudySyncProviderSetupIssue.REMOTE_ENCRYPTION_REQUIRED)
        }
        return StudySyncProviderSetupValidation(settings.configuredProviderId, issues)
    }

    fun normalizeEndpoint(raw: String): String = raw
        .trim()
        .substringBefore("#")
        .substringBefore("?")
        .trimEnd('/')

    fun diagnosticsFor(setup: StudySyncWebDavSetup): StudySyncProviderDiagnostics = StudySyncProviderDiagnostics(
        providerId = StudyFlowSyncProviderIds.WEBDAV,
        displayLocation = displayLocation(setup.normalizedEndpoint),
        usernameLabel = redactUsername(setup.username),
        credentialConfigured = setup.credentialHandle != null,
        secretLabel = setup.credentialHandle?.diagnosticLabel,
    )

    fun displayLocation(location: String): String {
        val normalized = normalizeEndpoint(location)
        val withoutScheme = normalized.substringAfter("://", normalized)
        val authority = withoutScheme.substringBefore('/').substringAfter('@')
        val path = withoutScheme.substringAfter('/', missingDelimiterValue = "")
        return if (path.isBlank()) authority else "$authority/..."
    }

    fun redactUsername(username: String): String {
        val trimmed = username.trim()
        if (trimmed.isBlank()) return "<blank>"
        val first = trimmed.first()
        val suffix = trimmed.substringAfter('@', missingDelimiterValue = "")
        return if (suffix.isBlank()) "$first***" else "$first***@$suffix"
    }

    private fun endpointUserInfoPresent(endpoint: String): Boolean {
        val afterScheme = endpoint.substringAfter("://", missingDelimiterValue = "")
        if (afterScheme.isBlank()) return false
        return '@' in afterScheme.substringBefore('/')
    }
}
