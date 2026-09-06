package com.mikeyphw.studyflow.feature.workspace

import com.mikeyphw.studyflow.core.model.StudySession
import com.mikeyphw.studyflow.core.model.SubjectId
import com.mikeyphw.studyflow.core.model.TopicId

/**
 * A topic-specific session belongs to exactly that topic. A subject-only session
 * contributes to every topic in the subject only when no topic was recorded.
 */
internal fun StudySession.contributesToTopicEvidence(
    topicId: TopicId,
    subjectId: SubjectId,
): Boolean = this.topicId == topicId || (this.topicId == null && this.subjectId == subjectId)
