package com.mikeyphw.studyflow.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySyncProviderSetupTest {
    @Test
    fun `keep offline remains an available setup action on a fresh install`() {
        val actions = StudySyncProviderSetupPolicy.actions(StudyFlowSyncSettings.LocalFirstDefault)

        assertTrue(StudySyncProviderSetupAction.KEEP_OFFLINE in actions)
        assertTrue(StudySyncProviderSetupAction.CHOOSE_LOCAL_FOLDER in actions)
        assertFalse(StudySyncProviderSetupAction.SAVE_PROVIDER in actions)
    }

    @Test
    fun `local folder setup accepts SAF tree URIs without an account`() {
        val setup = StudySyncLocalFolderSetup(
            "content://com.android.externalstorage.documents/tree/primary%3AStudyFlow",
        )
        val validation = StudySyncProviderSetupPolicy.validateLocalFolder(setup)

        assertTrue(validation.valid)
        assertEquals(StudyFlowSyncProviderIds.LOCAL_FOLDER, setup.diagnostics.providerId)
        assertFalse(setup.diagnostics.credentialConfigured)
    }

    @Test
    fun `webdav setup requires https credential handles and no url embedded secrets`() {
        val invalid = StudySyncWebDavSetup(
            endpointUrl = "http://user:password@example.invalid/remote.php/dav/files/me/studyflow",
            username = "me@example.invalid",
            credentialHandle = null,
        )
        val validation = StudySyncProviderSetupPolicy.validateWebDav(invalid)

        assertFalse(validation.valid)
        assertTrue(StudySyncProviderSetupIssue.HTTPS_REQUIRED in validation.issues)
        assertTrue(StudySyncProviderSetupIssue.USERINFO_FORBIDDEN in validation.issues)
        assertTrue(StudySyncProviderSetupIssue.CREDENTIAL_HANDLE_REQUIRED in validation.issues)
    }

    @Test
    fun `provider diagnostics never expose raw webdav credentials`() {
        val setup = StudySyncWebDavSetup(
            endpointUrl = "https://nextcloud.example/remote.php/dav/files/me/studyflow?token=secret",
            username = "mikey@example.invalid",
            credentialHandle = StudySyncSecretHandle("studyflow-webdav-main", 100L),
        )
        val diagnostics = setup.diagnostics.toString()

        assertTrue(StudySyncProviderSetupPolicy.validateWebDav(setup).valid)
        assertTrue(diagnostics.contains("nextcloud.example/..."))
        assertTrue(diagnostics.contains("m***@example.invalid"))
        assertFalse(diagnostics.contains("token=secret"))
        assertFalse(diagnostics.contains("mikey@example.invalid"))
    }

    @Test
    fun `remote providers require encrypted remote backups before saving`() {
        val validation = StudySyncProviderSetupPolicy.validateSettingsForRemoteProvider(
            StudyFlowSyncSettings(
                syncEnabled = true,
                providerId = StudyFlowSyncProviderIds.WEBDAV,
                encryptRemoteBackups = false,
            ),
        )

        assertFalse(validation.valid)
        assertTrue(StudySyncProviderSetupIssue.REMOTE_ENCRYPTION_REQUIRED in validation.issues)
    }
}
