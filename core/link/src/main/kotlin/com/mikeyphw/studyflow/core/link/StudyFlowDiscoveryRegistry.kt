package com.mikeyphw.studyflow.core.link

class StudyFlowDiscoveryRegistry(
    sources: Iterable<StudyFlowDiscovery> = emptyList(),
) : StudyFlowDiscovery {
    private val sources = mutableListOf<StudyFlowDiscovery>().apply { addAll(sources) }

    fun add(source: StudyFlowDiscovery) {
        synchronized(sources) {
            if (sources.none { it === source }) sources += source
        }
    }

    fun remove(source: StudyFlowDiscovery): Boolean = synchronized(sources) { sources.remove(source) }

    override fun snapshot(nowEpochMillis: Long): List<StudyFlowDiscoveredPeer> =
        StudyFlowCompositeDiscovery(synchronized(sources) { sources.toList() }).snapshot(nowEpochMillis)
}
