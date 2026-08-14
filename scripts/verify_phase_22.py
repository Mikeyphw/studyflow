#!/usr/bin/env python3
from pathlib import Path
import json
import re
import sqlite3
import sys

from phase_version import phase_version_at_least

ROOT = Path(__file__).resolve().parents[1]
failures = []


def require(path, *markers):
    file = ROOT / path
    if not file.is_file():
        failures.append(f"missing Phase 22 file: {path}")
        return
    text = file.read_text(encoding="utf-8")
    for marker in markers:
        if marker not in text:
            failures.append(f"{path} missing marker: {marker}")


def verify_discovery_sql():
    schema_path = (
        ROOT
        / "core/database/schemas/com.mikeyphw.studyflow.core.database.StudyFlowDatabase/2.json"
    )
    dao_path = ROOT / "core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudyDiscoveryDao.kt"
    if not schema_path.is_file() or not dao_path.is_file():
        failures.append("cannot run Phase 22 SQL verification without schema v2 and StudyDiscoveryDao")
        return
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    connection = sqlite3.connect(":memory:")
    try:
        for entity in schema["database"]["entities"]:
            create_sql = entity["createSql"].replace("${TABLE_NAME}", entity["tableName"])
            connection.execute(create_sql)
        source = dao_path.read_text(encoding="utf-8")
        query_matches = re.findall(
            r'@Query\(\s*(?:"""(.*?)"""|"([^"]+)")\s*,?\s*\)\s*suspend fun (\w+)',
            source,
            flags=re.DOTALL,
        )
        queries = {name: multiline or single for multiline, single, name in query_matches}
        expected_queries = {
            "findPlan",
            "findPracticeAccuracy",
            "findTopicRecommendations",
            "countDueReviews",
            "findNextActivity",
            "findScheduleAdherence",
            "findOverdueResource",
            "findNearestGoal",
        }
        if set(queries) != expected_queries:
            failures.append(
                "StudyDiscoveryDao recommendation SQL set drifted: "
                f"expected {sorted(expected_queries)}, found {sorted(queries)}"
            )
            return
        parameters = {
            "query": "law",
            "includeArchived": 0,
            "planId": "plan-id",
            "now": 1_800_000_000_000,
        }
        for name, query in queries.items():
            try:
                connection.execute("EXPLAIN QUERY PLAN " + query, parameters).fetchall()
            except sqlite3.Error as error:
                failures.append(f"StudyDiscoveryDao SQL query {name} is invalid: {error}")

        def insert(table, **values):
            columns = []
            arguments = []
            for _, name, affinity, not_null, _, _ in connection.execute(f'PRAGMA table_info("{table}")'):
                if name in values:
                    value = values[name]
                elif not_null:
                    if affinity.upper().startswith("TEXT"):
                        value = f"{table}-{name}"
                    elif affinity.upper().startswith("REAL"):
                        value = 0.0
                    else:
                        value = 0
                else:
                    continue
                columns.append(name)
                arguments.append(value)
            quoted = ",".join(f'"{column}"' for column in columns)
            placeholders = ",".join("?" for _ in columns)
            connection.execute(
                f'INSERT INTO "{table}" ({quoted}) VALUES ({placeholders})',
                arguments,
            )

        insert(
            "study_plans",
            id="plan-id",
            name="Plan",
            description="",
            type="CUSTOM",
            status="ACTIVE",
            createdAtEpochMillis=1,
            updatedAtEpochMillis=1,
        )
        insert(
            "subjects",
            id="s-active",
            studyPlanId="plan-id",
            name="Active",
            description="",
            status="NOT_STARTED",
            archived=0,
        )
        insert(
            "subjects",
            id="s-archived",
            studyPlanId="plan-id",
            name="Archived",
            description="",
            status="NOT_STARTED",
            archived=1,
        )
        insert(
            "topics",
            id="t-active",
            studyPlanId="plan-id",
            subjectId="s-active",
            name="Active topic",
            description="",
            status="NOT_STARTED",
            archived=0,
            masteryPercent=20,
        )
        insert(
            "questions",
            id="q-other-plan",
            studyPlanId="other-plan",
            topicId="t-active",
            type="MULTIPLE_CHOICE",
            statement="Other plan",
            optionsJson="[]",
            correctAnswer="A",
            explanation="",
            tagsJson="[]",
        )
        insert(
            "error_entries",
            id="e-other-plan",
            studyPlanId="other-plan",
            topicId="t-active",
            category="OTHER",
            explanation="",
            recurrenceCount=9,
        )
        insert(
            "questions",
            id="q-archived",
            studyPlanId="plan-id",
            subjectId="s-archived",
            type="MULTIPLE_CHOICE",
            statement="Archived question",
            optionsJson="[]",
            correctAnswer="A",
            explanation="",
            tagsJson="[]",
        )
        insert(
            "review_items",
            id="r-archived",
            studyPlanId="plan-id",
            questionId="q-archived",
            method="SPACED_REPETITION",
            status="DUE",
            dueAtEpochMillis=0,
            easeFactor=2.5,
        )
        insert(
            "study_materials",
            id="m-archived-parent",
            studyPlanId="plan-id",
            subjectId="s-archived",
            type="BOOK",
            title="Hidden material",
            description="",
            completedUnits=0,
            archived=0,
            tagsJson="[]",
        )
        insert(
            "planned_activities",
            id="a-hidden",
            studyPlanId="plan-id",
            materialId="m-archived-parent",
            type="READING",
            title="Hidden activity",
            scheduledStartEpochMillis=1,
            status="MISSED",
        )
        insert(
            "study_goals",
            id="g-hidden",
            studyPlanId="plan-id",
            subjectId="s-archived",
            type="TIME",
            period="CUSTOM",
            title="Hidden goal",
            targetValue=1.0,
            currentValue=0.0,
            startsOnIso="2026-01-01",
            endsOnIso="2026-12-01",
            archived=0,
        )
        recommendation_parameters = {"planId": "plan-id", "now": 100}
        if connection.execute(queries["countDueReviews"], recommendation_parameters).fetchone()[0] != 0:
            failures.append("due-review recommendation includes content linked to an archived parent")
        if connection.execute(queries["findNextActivity"], recommendation_parameters).fetchone() is not None:
            failures.append("next-activity recommendation includes a material linked to an archived parent")
        if connection.execute(queries["findScheduleAdherence"], recommendation_parameters).fetchone() != (0, 0):
            failures.append("schedule adherence includes activities linked to archived content")
        if connection.execute(queries["findNearestGoal"], {"planId": "plan-id"}).fetchone() is not None:
            failures.append("goal recommendation includes a goal linked to an archived parent")
        topic = connection.execute(
            queries["findTopicRecommendations"],
            {"planId": "plan-id"},
        ).fetchone()
        if topic is None or topic[-2:] != (0, 0):
            failures.append("topic recommendation subqueries are not scoped to the selected Study Plan")
    finally:
        connection.close()


require("app/build.gradle.kts", 'project(":feature:search")')
if not phase_version_at_least(ROOT, 22):
    failures.append("app version is not Phase 22 or later")
require("settings.gradle.kts", '":feature:search"')
require(
    "core/domain/src/main/kotlin/com/mikeyphw/studyflow/core/domain/StudyDiscovery.kt",
    "GlobalSearchContentType",
    "GlobalSearchDestination",
    "StudyRecommendationEngine",
    "StudyRecommendationReasonType",
    "OVERDUE_RESOURCE",
)
require(
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/dao/StudyDiscoveryDao.kt",
    "@RawQuery",
    "searchIndexed",
    "findOverdueResource",
    "topicSubject",
)
require(
    "core/database/src/main/java/com/mikeyphw/studyflow/core/database/StudyFlowSearchIndex.kt",
    "object StudyFlowSearchIndex",
    "fts4",
    "fun install(db: SupportSQLiteDatabase)",
)
require(
    "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/IndexedStudyDiscoveryQuery.kt",
    "MATCH ?",
    "CandidateLimit = 320",
    "WITH hits AS",
    "UNION ALL",
)
require(
    "core/data/src/test/kotlin/com/mikeyphw/studyflow/core/data/IndexedStudyDiscoveryQueryTest.kt",
    "search is fts backed sql bounded and rechecks ownership visibility",
    "LIMIT 320",
)
require(
    "core/data/src/main/kotlin/com/mikeyphw/studyflow/core/data/RoomStudyDiscoveryRepository.kt",
    "RoomStudyDiscoveryRepository",
    "MaximumSearchResults",
    'setOf("ARCHIVED", "TRASHED", "COMPLETED")',
)
require(
    "feature/search/src/main/java/com/mikeyphw/studyflow/feature/search/StudySearchScreen.kt",
    "StudySearchScreen",
    "recentSearches",
    "FilterChip",
    "onOpenResult",
    "onOpenRecommendation",
)
require(
    "app/src/main/java/com/mikeyphw/studyflow/StudyFlowApp.kt",
    "SearchRoute",
    "navigateToResult",
    "navigateToRecommendation",
    "navigateToSearch",
    "when (result.type)",
    "GlobalSearchContentType.PLAN",
    "GlobalSearchContentType.ERROR",
)
require(
    "core/i18n/src/main/java/com/mikeyphw/studyflow/core/i18n/DataStoreAppPreferencesRepository.kt",
    "recordRecentSearch",
    "clearRecentSearches",
)
require(
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/StudyRecommendationEngineTest.kt",
    "recommendationsAreDeterministicStructuredAndExplainEveryChoice",
    "masteredTopicsDoNotProduceFalseWeakTopicRecommendations",
)
require(
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/GlobalSearchDestinationTest.kt",
    "everySearchTypeMapsToItsMatchingFeatureDestination",
)
require(
    "core/domain/src/test/kotlin/com/mikeyphw/studyflow/core/domain/ManageStudyDiscoveryTest.kt",
    "searchNormalizesWhitespaceAndPreservesTheRequestedFilterPolicy",
    "searchRejectsQueriesThatCannotProduceAUsefulResult",
)
require("PHASE_22_IMPLEMENTATION.md", "global search", "deterministic recommendations")
require(
    "docs/quality/PHASE_22_23_PROMISE_AUDIT.md",
    "Audit 1: implementation completeness",
    "Audit 2: independent release recheck",
    "Validation boundary",
)
require("docs/quality/PHASE_22_ACCEPTANCE.md", "eight promised content types")
require(
    ".devtool.toml",
    '":feature:search:compileDebugKotlin"',
    '":feature:search:compileDebugUnitTestKotlin"',
    '":feature:search:testDebugUnitTest"',
    '":feature:search:lintDebug"',
)

verify_discovery_sql()

for forbidden in ("androidx.appsearch", "kapt(", 'org.jetbrains.kotlin.android'):
    for path in ROOT.rglob("*.kts"):
        if forbidden in path.read_text(encoding="utf-8"):
            failures.append(f"forbidden Phase 22 toolchain token {forbidden}: {path.relative_to(ROOT)}")

if failures:
    print("PHASE_22_CONTRACT: FAILED")
    for failure in failures:
        print(f"- {failure}")
    sys.exit(1)
print("PHASE_22_CONTRACT: PASSED (search and deterministic recommendations)")
