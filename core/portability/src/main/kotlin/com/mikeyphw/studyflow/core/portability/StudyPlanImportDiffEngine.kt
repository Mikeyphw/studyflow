package com.mikeyphw.studyflow.core.portability

object StudyPlanImportDiffEngine {
    fun diffProjected(
        incoming: StudyPlanPackagePayload,
        current: StudyPlanPackagePayload?,
        strategy: StudyPlanImportStrategy,
        ignoredRowsByTable: Map<String, Int> = emptyMap(),
    ): List<StudyPlanImportTableDiff> {
        val incomingTables = incoming.tables.associateBy(PortableTable::name)
        val currentTables = current?.tables.orEmpty().associateBy(PortableTable::name)
        val names = linkedSetOf<String>().apply {
            addAll(incoming.tables.map(PortableTable::name)); addAll(current?.tables.orEmpty().map(PortableTable::name))
        }
        return names.map { name ->
            val a = incomingTables[name]?.rows.orEmpty(); val b = currentTables[name]?.rows.orEmpty()
            val ignored = ignoredRowsByTable[name] ?: 0
            if (strategy == StudyPlanImportStrategy.MERGE_COMPATIBLE && ignored > 0) {
                StudyPlanImportTableDiff(name, a.size, b.size, 0, 0, 0, b.size, ignored)
            } else if (strategy == StudyPlanImportStrategy.CREATE_AS_NEW) {
                StudyPlanImportTableDiff(name, a.size, 0, a.size, 0, 0, 0, ignored)
            } else {
                val ak = keyedProjected(name, a); val bk = keyedProjected(name, b)
                val shared = ak.keys intersect bk.keys
                val changed = shared.count { ak.getValue(it) != bk.getValue(it) }
                StudyPlanImportTableDiff(
                    name, a.size, b.size, (ak.keys - bk.keys).size,
                    if (strategy == StudyPlanImportStrategy.REPLACE_EXISTING) (bk.keys - ak.keys).size else 0,
                    changed, shared.size - changed, ignored,
                )
            }
        }
    }

    private fun keyedProjected(tableName: String, rows: List<PortableRow>): Map<String, String> =
        rows.mapIndexed { index, row ->
            val key = row.values["id"]?.value ?: row.values["studyPlanId"]?.value?.let { "$tableName:$it" }
                ?: "$tableName:index:$index"
            key to canonical(row)
        }.toMap()
    fun diff(
        incoming: StudyPlanPackagePayload,
        current: StudyPlanPackagePayload?,
        strategy: StudyPlanImportStrategy,
        targetStudyPlanId: String?,
    ): List<StudyPlanImportTableDiff> {
        val incomingTables = incoming.tables.associateBy(PortableTable::name)
        val currentTables = current?.tables.orEmpty().associateBy(PortableTable::name)
        val tableNames = linkedSetOf<String>().apply {
            addAll(incoming.tables.map(PortableTable::name))
            addAll(current?.tables.orEmpty().map(PortableTable::name))
        }
        return tableNames.map { tableName ->
            val incomingRows = incomingTables[tableName]?.rows.orEmpty()
            val currentRows = currentTables[tableName]?.rows.orEmpty()
            when (strategy) {
                StudyPlanImportStrategy.CREATE_AS_NEW -> StudyPlanImportTableDiff(
                    tableName = tableName,
                    incomingRows = incomingRows.size,
                    currentRows = 0,
                    addedRows = incomingRows.size,
                    removedRows = 0,
                    changedRows = 0,
                    unchangedRows = 0,
                )
                StudyPlanImportStrategy.MERGE_COMPATIBLE -> mergeDiff(tableName, incomingRows, currentRows)
                StudyPlanImportStrategy.REPLACE_EXISTING -> replaceDiff(
                    tableName = tableName,
                    incomingRows = incomingRows,
                    currentRows = currentRows,
                    sourceStudyPlanId = incoming.manifest.sourceStudyPlanId,
                    targetStudyPlanId = requireNotNull(targetStudyPlanId) {
                        "Replace diff requires a target Study Plan id."
                    },
                )
            }
        }
    }

    private fun mergeDiff(
        tableName: String,
        incomingRows: List<PortableRow>,
        currentRows: List<PortableRow>,
    ): StudyPlanImportTableDiff {
        val ignored = if (tableName == "study_plans" || tableName in StudyPlanImportCompatibility.MergeSkippedTables) {
            incomingRows.size
        } else {
            0
        }
        return StudyPlanImportTableDiff(
            tableName = tableName,
            incomingRows = incomingRows.size,
            currentRows = currentRows.size,
            addedRows = if (ignored == 0) incomingRows.size else 0,
            removedRows = 0,
            changedRows = 0,
            unchangedRows = currentRows.size,
            ignoredRows = ignored,
        )
    }

    private fun replaceDiff(
        tableName: String,
        incomingRows: List<PortableRow>,
        currentRows: List<PortableRow>,
        sourceStudyPlanId: String,
        targetStudyPlanId: String,
    ): StudyPlanImportTableDiff {
        val incomingByKey = keyedRows(tableName, incomingRows, sourceStudyPlanId, targetStudyPlanId)
        val currentByKey = keyedRows(tableName, currentRows, targetStudyPlanId, targetStudyPlanId)
        val addedKeys = incomingByKey.keys - currentByKey.keys
        val removedKeys = currentByKey.keys - incomingByKey.keys
        val sharedKeys = incomingByKey.keys intersect currentByKey.keys
        val changed = sharedKeys.count { key -> incomingByKey.getValue(key) != currentByKey.getValue(key) }
        val unchanged = sharedKeys.size - changed
        return StudyPlanImportTableDiff(
            tableName = tableName,
            incomingRows = incomingRows.size,
            currentRows = currentRows.size,
            addedRows = addedKeys.size,
            removedRows = removedKeys.size,
            changedRows = changed,
            unchangedRows = unchanged,
        )
    }

    private fun keyedRows(
        tableName: String,
        rows: List<PortableRow>,
        sourceStudyPlanId: String,
        targetStudyPlanId: String,
    ): Map<String, String> = rows.mapIndexed { index, row ->
        val normalized = normalize(row, sourceStudyPlanId, targetStudyPlanId)
        val key = normalized.values["id"]?.value
            ?: normalized.values["studyPlanId"]?.value?.let { "$tableName:$it" }
            ?: "$tableName:index:$index"
        key to canonical(normalized)
    }.toMap()

    private fun normalize(
        row: PortableRow,
        sourceStudyPlanId: String,
        targetStudyPlanId: String,
    ): PortableRow = PortableRow(
        row.values.mapValues { (_, value) ->
            if (value.type == PortableValueType.TEXT && value.value == sourceStudyPlanId) {
                value.copy(value = targetStudyPlanId)
            } else {
                value
            }
        },
    )

    private fun canonical(row: PortableRow): String =
        row.values.toSortedMap().entries.joinToString("|") { (name, value) ->
            "$name=${value.type}:${value.value.orEmpty()}"
        }
}
