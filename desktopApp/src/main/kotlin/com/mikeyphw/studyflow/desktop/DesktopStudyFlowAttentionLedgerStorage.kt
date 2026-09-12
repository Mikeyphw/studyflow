package com.mikeyphw.studyflow.desktop

import com.mikeyphw.studyflow.core.link.StudyFlowAttentionLedgerStorage
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class DesktopStudyFlowAttentionLedgerStorage(
    root: Path = defaultDesktopWorkspaceRoot(),
) : StudyFlowAttentionLedgerStorage {
    private val ledgerFile = root.resolve("attention-ledger.json")

    override fun readEncodedSnapshot(): String? = synchronized(this) {
        if (!Files.isRegularFile(ledgerFile)) return@synchronized null
        Files.readString(ledgerFile)
    }

    override fun writeEncodedSnapshot(encoded: String) = synchronized(this) {
        Files.createDirectories(ledgerFile.parent)
        val temporary = Files.createTempFile(ledgerFile.parent, "attention-ledger-", ".tmp")
        try {
            Files.writeString(temporary, encoded)
            try {
                Files.move(
                    temporary,
                    ledgerFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, ledgerFile, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }
}
