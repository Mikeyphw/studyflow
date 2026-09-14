package com.mikeyphw.studyflow.core.link

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class StudyFlowWorkspaceAccess {
    @SerialName("read") READ,
    @SerialName("write") WRITE,
}

@Serializable
data class StudyFlowWorkspaceMember(
    val workspaceId: StudyFlowWorkspaceId,
    val deviceId: StudyFlowDeviceId,
    val access: StudyFlowWorkspaceAccess,
    val grantedAtEpochMillis: Long,
) {
    init { require(grantedAtEpochMillis >= 0L) }
}

interface StudyFlowWorkspaceMembershipStore {
    fun list(): List<StudyFlowWorkspaceMember>
    fun put(member: StudyFlowWorkspaceMember)
    fun remove(workspaceId: StudyFlowWorkspaceId, deviceId: StudyFlowDeviceId): Boolean
}

class InMemoryStudyFlowWorkspaceMembershipStore : StudyFlowWorkspaceMembershipStore {
    private val members = linkedMapOf<Pair<StudyFlowWorkspaceId, StudyFlowDeviceId>, StudyFlowWorkspaceMember>()
    override fun list() = synchronized(this) { members.values.toList() }
    override fun put(member: StudyFlowWorkspaceMember) { synchronized(this) { members[member.workspaceId to member.deviceId] = member } }
    override fun remove(workspaceId: StudyFlowWorkspaceId, deviceId: StudyFlowDeviceId) = synchronized(this) { members.remove(workspaceId to deviceId) != null }
}

class StudyFlowWorkspaceMembershipManager(
    private val localDeviceId: StudyFlowDeviceId,
    private val store: StudyFlowWorkspaceMembershipStore,
) {
    fun grant(workspaceId: StudyFlowWorkspaceId, deviceId: StudyFlowDeviceId, access: StudyFlowWorkspaceAccess, now: Long) =
        StudyFlowWorkspaceMember(workspaceId, deviceId, access, now).also(store::put)
    fun revoke(workspaceId: StudyFlowWorkspaceId, deviceId: StudyFlowDeviceId) = store.remove(workspaceId, deviceId)
    fun revokeDevice(deviceId: StudyFlowDeviceId) = store.list().filter { it.deviceId == deviceId }.count { store.remove(it.workspaceId, deviceId) }
    fun members(workspaceId: StudyFlowWorkspaceId) = store.list().filter { it.workspaceId == workspaceId }
    fun canRead(workspaceId: StudyFlowWorkspaceId, deviceId: StudyFlowDeviceId) =
        deviceId == localDeviceId || members(workspaceId).any { it.deviceId == deviceId }
    fun canWrite(workspaceId: StudyFlowWorkspaceId, deviceId: StudyFlowDeviceId) =
        deviceId == localDeviceId || members(workspaceId).any { it.deviceId == deviceId && it.access == StudyFlowWorkspaceAccess.WRITE }
}

/** Product mutation bridge used by native data-owning clients. */
interface StudyFlowProductStateBridge {
    fun captureLocalMutations(workspaceId: StudyFlowWorkspaceId): List<StudyFlowProductMutation>
    fun projectResolved(workspaceId: StudyFlowWorkspaceId, operations: List<StudyFlowDomainOperation>): Int
}

data class StudyFlowProductMutation(
    val operationId: StudyFlowOperationId,
    val entityId: StudyFlowEntityId,
    val operationType: String,
    val tombstone: Boolean = false,
    val payload: kotlinx.serialization.json.JsonObject,
)

object StudyFlowProductStateContract {
    val WorkspaceId = StudyFlowWorkspaceId("product-state-v1")
    const val RowUpsert = "product.row_upsert"
    const val RowDelete = "product.row_delete"
    const val TvDomainAction = "tv.domain_action_requested"
}
