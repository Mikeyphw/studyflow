export type StudyMode = "EXAM_PLAN" | "PROGRAMMING_COURSE" | "LANGUAGE_DECK" | "READING_STUDY" | "GENERAL_SKILL";
export type ActivityKind = "study" | "questions" | "review";
export type ActivityStatus = "planned" | "completed" | "cancelled";
export type FocusStatus = "idle" | "running" | "paused";
export type TopicPriority = "low" | "normal" | "high";
export type TopicStatus = "not-started" | "learning" | "reviewing" | "mastered" | "archived";
export type ReviewStatus = "due" | "completed" | "dismissed";
export type ErrorCategory = "content-gap" | "forgotten" | "concept-confusion" | "interpretation" | "distraction" | "calculation" | "time-pressure" | "disputed" | "other";

export interface EntityBase { id: string; createdAt: string; updatedAt: string; }
export type ExamDateStatus = "confirmed" | "expected" | "unknown";
export interface ExamTargetProfile { organization:string|null; role:string|null; board:string|null; editalRef:string|null; dateStatus:ExamDateStatus; examDate:string|null; openings:number|null; estimatedCutoff:number|null; }
export interface ExamStage { id:string; name:string; date:string|null; status:"planned"|"completed"|"cancelled"; }
export interface ExamNoticeRevision { id:string; acceptedAt:string; sourceHash:string; added:number; archived:number; reactivated:number; unchanged:number; previousRevisionId:string|null; syllabusFingerprint:string|null; }
export interface LegislationProvision { id:string; label:string; text:string; status:"unseen"|"studying"|"reviewing"|"known"; }
export interface LegislationEvidenceLink { id:string; provisionId:string; questionId:string|null; errorId:string|null; reviewId:string|null; note:string|null; createdAt:string; }
export interface LegislationDocument { id:string; title:string; reference:string|null; notes:string|null; sourceUpdatedAt:string|null; verifiedAt:string|null; provisions:LegislationProvision[]; evidenceLinks:LegislationEvidenceLink[]; }
export type ExamScoringPolicy="no-penalty"|"fixed-negative"|"right-minus-wrong"|"weighted";
export interface MockExamSection { id:string; subjectId:string|null; count:number; weight:number; }
export interface MockExamBlueprint { scoring:ExamScoringPolicy; negativePoints:number; totalQuestions:number; durationMinutes:number; sections:MockExamSection[]; }
export type PublicExamStudyPhase="pre-edital"|"post-edital"|"final-stretch";
export interface PublicExamConfiguration { target:ExamTargetProfile; stages:ExamStage[]; noticeRevisions:ExamNoticeRevision[]; reviewIntervalsDays:number[]; legislation:LegislationDocument[]; mockBlueprint:MockExamBlueprint; studyPhase:PublicExamStudyPhase; }
export interface ProgrammingCodeFile { path:string; language:string; content:string; }
export interface ProgrammingExercise { id:string; title:string; language:string; prompt:string; starterCode:string; expectedOutput:string|null; hints:string[]; solutionNotes:string|null; difficulty:"easy"|"medium"|"hard"; status:"todo"|"attempted"|"completed"; files:ProgrammingCodeFile[]; }
export interface MiniProject { id:string; title:string; status:"planned"|"active"|"completed"; checkpoints:string[]; completedCheckpoints:string[]; deliverables:string[]; }
export interface ProgrammingConfiguration { language:string; level:"beginner"|"intermediate"|"advanced"; runtime:"browser-only"; exercises:ProgrammingExercise[]; projects:MiniProject[]; }
export type LanguageReviewDirection="recognition"|"production"|"fill-blank"|"natural-usage"|"mistake-spotting"|"sentence-creation";
export interface LanguageExpression { id:string; expression:string; meaning:string; literalTranslation:string|null; region:string|null; register:string|null; pronunciationNote:string|null; commonMistakes:string[]; exampleSentence:string|null; }
export interface LanguageConfiguration { language:string; nativeLanguage:string|null; expressions:LanguageExpression[]; reviewDirections:LanguageReviewDirection[]; }
export interface ReadingItem { id:string; title:string; totalUnits:number; currentUnit:number; unit:string; status:"planned"|"reading"|"completed"; notes:string|null; }
export interface ReadingConfiguration { items:ReadingItem[]; }
export interface GeneralSkillDrill { id:string; title:string; metric:string; target:number; unit:string; status:"active"|"paused"|"completed"; }
export interface GeneralSkillConfiguration { drills:GeneralSkillDrill[]; }
export interface ScheduleAvailabilityWindow { id:string; weekday:number; startMinute:number; endMinute:number; }
export interface FixedScheduleBlock { id:string; title:string; date:string|null; weekday:number|null; startMinute:number; durationMinutes:number; }
export interface ScheduleConfiguration { dailyCapacityMinutes:number; availability:ScheduleAvailabilityWindow[]; fixedBlocks:FixedScheduleBlock[]; }
export interface SavedPracticeSet { id:string; name:string; questionIds:string[]; filter:ExamQuestionFilter; createdAt:string; }
export interface PracticeConfiguration { savedSets:SavedPracticeSet[]; weeklyQuestionQuota:number|null; }
export interface PlanConfiguration { version:1; schedule:ScheduleConfiguration; practice:PracticeConfiguration; publicExam?:PublicExamConfiguration; programming?:ProgrammingConfiguration; language?:LanguageConfiguration; reading?:ReadingConfiguration; general?:GeneralSkillConfiguration; }
export interface Plan extends EntityBase { name: string; mode: StudyMode; status: "active" | "archived"; examDate: string | null; configuration:PlanConfiguration; }
export interface Subject extends EntityBase { planId: string; name: string; weight: number | null; examIncidence:number|null; order: number; notes: string | null; archived: boolean; }
export interface Topic extends EntityBase { planId: string; subjectId: string; parentTopicId: string | null; name: string; order: number; priority: TopicPriority; status: TopicStatus; notes: string | null; }
export interface ActivityScheduleProvenance { source:"manual"|"adaptive"; reason:string|null; previewGeneratedAt:string|null; baseRevision:number|null; }
export interface Activity extends EntityBase { planId: string; subjectId: string | null; topicId: string | null; title: string; kind: ActivityKind; durationMinutes: number; status: ActivityStatus; origin: "manual"|"seed"|"cycle"|"review"|"import"|"schedule"; queue: "today"|"future"|"backlog"; scheduledFor: string | null; scheduledStartMinute:number|null; locked:boolean; scheduleProvenance:ActivityScheduleProvenance|null; }
export interface StudySession extends EntityBase { planId: string; activityId: string | null; subjectId: string | null; topicId: string | null; startedAt: string; endedAt: string | null; durationMinutes: number | null; status: "running"|"paused"|"completed"|"cancelled"; note: string | null; }
export interface Goal extends EntityBase { planId: string; name: string; metric: string; target: number; period: "daily"|"weekly"|"monthly"|"plan"; active: boolean; }
export interface Material extends EntityBase { planId: string; subjectId: string | null; topicId: string | null; title: string; kind: "note"|"file"|"link"|"book"|"legislation"; locator: string | null; mimeType: string | null; attachmentId: string | null; sizeBytes: number | null; }
export interface Review extends EntityBase { planId: string; subjectId: string | null; topicId: string | null; dueAt: string; status: ReviewStatus; sourceType: string | null; sourceId: string | null; intervalIndex: number; intervalDays: number | null; repetitionCount: number; previousReviewId: string | null; completedAt: string | null; }
export interface Question extends EntityBase { planId: string; subjectId: string | null; topicId: string | null; prompt: string; choices: string[]; answer: string | null; source: string | null; explanation: string | null; disputed: boolean; difficulty:"easy"|"medium"|"hard"|null; favorite:boolean; organization:string|null; role:string|null; examStage:string|null; examBoard:string|null; year:number|null; authorship:string|null; editalRevision:string|null; externalReference:string|null; points:number|null; }
export interface Evidence extends EntityBase { planId: string; subjectId: string | null; topicId: string | null; activityId: string | null; sessionId: string | null; questionId: string | null; kind: string; observedAt: string; value: unknown; provenance: "user"|"session"|"question"|"import"|"derived"; }
export interface ErrorEntry extends EntityBase { planId: string; subjectId: string | null; topicId: string | null; questionId: string | null; reviewId: string | null; category: ErrorCategory; note: string | null; recurrenceCount: number; status: "open"|"resolved"; observedAt: string; resolvedAt: string | null; }
export interface FocusState { status: FocusStatus; activityId: string | null; durationMinutes: number; remainingMs: number; elapsedMs: number; startedAt: string | null; lastResumedAt: string | null; endsAt: string | null; note: string | null; }
export interface FocusSnapshot extends FocusState { remainingMs: number; }
export interface StudyCycleState { subjectIds: string[]; cursor: number; }
export interface StudyState { schemaVersion: 6; revision: number; activePlanId: string | null; focus: FocusState; studyCycle: StudyCycleState; studyCycles: Record<string,StudyCycleState>; plans: Plan[]; subjects: Subject[]; topics: Topic[]; activities: Activity[]; sessions: StudySession[]; goals: Goal[]; materials: Material[]; reviews: Review[]; questions: Question[]; evidence: Evidence[]; errorEntries: ErrorEntry[]; }
export interface PersistenceStatus { status: string; message: string; durable: boolean; }
export interface RepositoryResult { status: string; state?: StudyState | null; reason?: string | null; }
export interface StudyRepository { backend?: string; load(): RepositoryResult; save(state: StudyState): RepositoryResult; clear(): RepositoryResult; flush?(): Promise<void>; close?(): void; }
export interface AttachmentMetadata { id: string; name: string; mimeType: string; sizeBytes: number; lastModified: number | null; createdAt: string; }
export interface AttachmentRecord extends AttachmentMetadata { blob: Blob; }
export interface AttachmentRepository { put(file: File|Blob, metadata?: Record<string, unknown>): Promise<AttachmentMetadata>; get(id: string): Promise<AttachmentRecord|null>; list(): Promise<AttachmentMetadata[]>; delete(id: string): Promise<void>; clear(): Promise<void>; }
export interface BrowserPersistence { backend: string; repository: StudyRepository; attachments: AttachmentRepository | null; }
export interface ScheduleOperation { action: "add"|"move"; activityId?: string; from?: string|null; to?: string; fromStartMinute?:number|null; toStartMinute?:number|null; scheduledFor?: string; scheduledStartMinute?:number|null; activity?: Partial<Activity>; reason?: string; }
export interface ScheduleConflict { date:string; kind:"capacity"|"overlap"|"locked-overdue"; activityId:string|null; fixedBlockId:string|null; message:string; }
export interface ScheduleDaySummary { date:string; capacityMinutes:number; fixedMinutes:number; plannedMinutes:number; remainingMinutes:number; overloaded:boolean; }
export interface SchedulePreview { baseRevision: number; generatedAt: string; operations: ScheduleOperation[]; summary: {add:number; move:number; conflicts:number; overloadedDays:number; unscheduled:number}; reasons: string[]; conflicts:ScheduleConflict[]; days:ScheduleDaySummary[]; unscheduled:Array<{activityId:string|null;title:string;durationMinutes:number;reason:string}>; }
export interface PortableAttachmentPayload { id:string; name:string; mimeType:string; sizeBytes:number; lastModified:number|null; createdAt:string; sha256:string; base64:string; }
export type PortableImportMode="replace"|"new-plan";
export interface PortableImportPreview { baseRevision: number; generatedAt: string; packageExportedAt: string|null; sourceActivePlanId: string|null; mode?:PortableImportMode; summary: {add:number; update:number; remove:number; unchanged:number}; collections: Record<string,{add:number;update:number;remove:number;unchanged:number}>; nextState: StudyState; attachmentRecords?:PortableAttachmentPayload[]; attachmentIdMap?:Record<string,string>; }

export type PlanContentImportFormat = "txt"|"markdown"|"csv"|"tsv"|"json";
export type PlanContentImportMode = "merge"|"reconcile";
export interface PlanContentImportIssue { severity:"warning"|"error"; code:string; row:number|null; message:string; }
export interface PlanContentImportOperation { action:"add-subject"|"update-subject"|"archive-subject"|"add-topic"|"update-topic"|"archive-topic"; subjectRef:string; topicRef?:string; subjectName:string; topicPath?:string[]; parentRef?:string|null; changes:Record<string,unknown>; }
export interface PlanContentImportPreview { baseRevision:number; generatedAt:string; sourceFingerprint:string; format:PlanContentImportFormat; mode:PlanContentImportMode; target:"active"|"new-plan"; targetPlanId:string|null; targetPlanName:string|null; targetPlanMode:StudyMode; ready:boolean; summary:{subjectsAdd:number;topicsAdd:number;update:number;reactivate:number;archive:number;matched:number;duplicate:number;invalid:number;ambiguous:number}; issues:PlanContentImportIssue[]; operations:PlanContentImportOperation[]; }

export interface ExamNoticeDiff { add:string[]; archive:string[]; reactivate:string[]; unchanged:string[]; }
export interface ExamNoticePreview { baseRevision:number; generatedAt:string; sourceHash:string; syllabusFingerprint:string; previousRevisionId:string|null; subjects:Array<{name:string;topics:string[];topicPaths:string[][]}>; duplicates:string[]; unrecognized:string[]; destructiveChanges:number; diff:ExamNoticeDiff; }
export interface ExamQuestionFilter { board?:string; organization?:string; role?:string; stage?:string; subjectId?:string; topicId?:string; yearFrom?:number; yearTo?:number; authorship?:string; difficulty?:"easy"|"medium"|"hard"; favorite?:boolean; disputed?:boolean; evidence?:"correct"|"wrong"|"unseen"; }
export type QuestionImportFormat="csv"|"tsv"|"markdown";
export interface QuestionImportIssue { severity:"warning"|"error"; row:number|null; code:string; message:string; }
export interface QuestionImportDraft { row:number; prompt:string; choices:string[]; answer:string|null; explanation:string|null; source:string|null; subjectName:string|null; topicPath:string[]; examBoard:string|null; organization:string|null; role:string|null; examStage:string|null; year:number|null; authorship:string|null; externalReference:string|null; points:number|null; difficulty:"easy"|"medium"|"hard"|null; favorite:boolean; disputed:boolean; }
export interface QuestionImportPreview { baseRevision:number; generatedAt:string; format:QuestionImportFormat; ready:boolean; drafts:QuestionImportDraft[]; accepted:QuestionImportDraft[]; issues:QuestionImportIssue[]; summary:{parsed:number;accepted:number;duplicates:number;invalid:number}; }
export interface WeaknessDrillPreview { baseRevision:number; generatedAt:string; ready:boolean; questionIds:string[]; reasons:string[]; signals:Array<{subjectId:string|null;topicId:string|null;score:number;reasons:string[]}>; }
export interface PublicExamPriorityAction { subjectId:string; topicId:string|null; score:number; reasonCodes:string[]; explanation:string; }
export interface EditalMatrixRow { subjectId:string; subjectName:string; relativeWeight:number|null; incidence:number|null; mastery:number|null; backlog:number; priority:number|null; }
export interface MockExamPreview { baseRevision:number; generatedAt:string; blueprint:MockExamBlueprint; questionIds:string[]; deficits:string[]; ready:boolean; }
export interface MockExamQuestionScore { questionId:string; sectionId:string; subjectId:string|null; outcome:"correct"|"wrong"|"unanswered"; basePoints:number; maximumPoints:number; earnedPoints:number; }
export interface MockExamSectionScore { sectionId:string; subjectId:string|null; correct:number; wrong:number; unanswered:number; earnedPoints:number; maximumPoints:number; percent:number; }
export interface MockExamScoreResult { scoringPolicy:ExamScoringPolicy; correct:number; wrong:number; unanswered:number; rawScore:number; maximumScore:number; percent:number|null; rounding:"nearest-0.01"; sections:MockExamSectionScore[]; questions:MockExamQuestionScore[]; }
export interface FinalStretchGuidance { band:"unknown"|"over-30"|"30-day"|"15-day"|"7-day"|"2-day"|"eve"; daysRemaining:number|null; allocation:{newContent:number;reviews:number;errors:number;questions:number;mocks:number;legislation:number}; }
export interface ModeMetric { label:string; value:number|null; }
export interface ModeAnalytics { mode:StudyMode|null; metrics:ModeMetric[]; }

export type ModeImportFormat="csv"|"markdown";
export interface ModeImportIssue { row:number; code:string; message:string; }
export interface ModeImportPreview { baseRevision:number; generatedAt:string; mode:"PROGRAMMING_COURSE"|"LANGUAGE_DECK"; format:ModeImportFormat; rows:Array<{index:number;status:"add"|"duplicate"|"invalid";label:string;payload:Record<string,unknown>}>; issues:ModeImportIssue[]; summary:{add:number;duplicate:number;invalid:number}; }
export interface LanguageReviewPrompt { expressionId:string; direction:LanguageReviewDirection; prompt:string; expected:string|null; manualReview:boolean; ready:boolean; reason:string|null; }
export interface ProgrammingAttemptAssessment { exerciseId:string; expectedOutput:string|null; observedOutput:string|null; matchesExpected:boolean|null; executionMode:"manual-output"; completed:boolean; }
export interface CodeToken { kind:"plain"|"keyword"|"string"|"number"|"comment"; text:string; }
export interface ReadinessSignal { id:string; label:string; value:number|null; status:"good"|"risk"|"unknown"; reason:string; }
export interface ReadinessExplanation { value:number|null; confidence:string; reason:string; signals:ReadinessSignal[]; }
export interface WeeklyCoachItem { id:string; kind:"win"|"risk"|"action"; title:string; reason:string; action:string|null; evidenceCount:number; }
export interface WeeklyCoachReview { status:"ready"|"insufficient"; generatedAt:string; items:WeeklyCoachItem[]; }
export interface QuestionQuotaPlan { configured:number|null; completed:number; remaining:number|null; dailyTarget:number|null; reason:string; }
export type ContentCleanupIssueKind="duplicate-question"|"orphan-topic"|"detached-review"|"invalid-activity"|"detached-material";
export interface ContentCleanupIssue { id:string; kind:ContentCleanupIssueKind; entityId:string; label:string; reason:string; applicable:boolean; action:"archive-topic"|"dismiss-review"|"cancel-activity"|null; }
export interface ContentCleanupPreview { baseRevision:number; generatedAt:string; issues:ContentCleanupIssue[]; summary:Record<ContentCleanupIssueKind,number>; applicableCount:number; }
export interface ExamReportSnapshot { generatedAt:string; planId:string|null; planName:string|null; readiness:ReadinessExplanation; coach:WeeklyCoachReview; quota:QuestionQuotaPlan; weeklyStudyMinutes:number; sevenDayEvidence:number; weakAreas:string[]; dueReviews:number; overdueReviews:number; mockCount:number; mockAverage:number|null; cleanup:ContentCleanupPreview["summary"]; nextActions:string[]; }
export interface ExamRunbookItem { id:string; priority:"urgent"|"high"|"normal"; title:string; reason:string; }
export interface ExamRunbook { status:"ready"|"no-date"|"not-exam-plan"; daysRemaining:number|null; phase:string|null; items:ExamRunbookItem[]; }
export interface StudySprintPreview { baseRevision:number; generatedAt:string; schedule:SchedulePreview; days:Array<{date:string;plannedMinutes:number;remainingMinutes:number;overloaded:boolean}>; reasons:string[]; }

export interface ProgressMetrics { weeklyStudyMinutes:number; gradedAttempts:number; accuracy:number|null; topicCoverage:number|null; coveredTopics:number; activeTopics:number; reviewCompletionRate:number|null; readiness:number|null; readinessReason:string; confidence:string; }
export interface StudyStore {
  getState(): StudyState; getSnapshot(): StudyState; getPersistenceStatus(): PersistenceStatus; getFocusSnapshot(at?: string|number): FocusSnapshot;
  subscribe(listener: (state: StudyState, persistence: PersistenceStatus, reason: string)=>void): ()=>void;
  createPlan(input:Partial<Plan> & Pick<Plan,"name">,options?:{activate?:boolean}):Plan; renamePlan(planId:string,name:string):Plan; switchPlan(planId:string):Plan; setPlanArchived(planId:string,archived?:boolean):Plan; duplicatePlan(planId:string,options?:{name?:string;activate?:boolean}):Plan; deletePlan(planId:string):void;
  setPlanMode(mode:StudyMode):Plan; setPracticeConfiguration(input:Partial<PracticeConfiguration>):Plan; savePracticeSet(name:string,filter?:ExamQuestionFilter,questionIds?:string[]):SavedPracticeSet; deletePracticeSet(id:string):Plan; updatePublicExamTarget(input:Partial<ExamTargetProfile>):Plan; setPublicExamStudyPhase(phase:PublicExamStudyPhase):Plan; applyPublicExamStarterTemplate(templateId:"balanced"|"questions"|"revision"):Plan; addExamStage(input:Partial<ExamStage> & Pick<ExamStage,"name">):Plan; previewExamNotice(source:string):ExamNoticePreview; applyExamNoticePreview(preview:ExamNoticePreview):Plan; addLegislation(input:{title:string;reference?:string|null;notes?:string|null;sourceUpdatedAt?:string|null;provisions?:Array<{label:string;text:string}>}):Plan; verifyLegislation(documentId:string,verifiedAt?:string):Plan; linkLegislationEvidence(documentId:string,input:{provisionId:string;questionId?:string|null;errorId?:string|null;reviewId?:string|null;note?:string|null}):Plan; setReviewIntervalsDays(intervals:number[]):Plan; setMockBlueprint(input:Partial<MockExamBlueprint>):Plan; recordMockExam(preview:MockExamPreview,answers:Record<string,string>):Evidence;
  addProgrammingExercise(input:Partial<ProgrammingExercise> & Pick<ProgrammingExercise,"title"|"prompt">):Plan; updateProgrammingExercise(id:string,changes:Partial<ProgrammingExercise>):Plan; recordProgrammingExercise(id:string,input?:{completed?:boolean;note?:string|null;observedOutput?:string|null}):Evidence; revealProgrammingSolution(id:string):Evidence; setProgrammingProjectCheckpoint(projectId:string,checkpoint:string,completed?:boolean):Plan; previewModeImport(source:string,format?:ModeImportFormat):ModeImportPreview; applyModeImportPreview(preview:ModeImportPreview):Plan;
  addLanguageExpression(input:Partial<LanguageExpression> & Pick<LanguageExpression,"expression"|"meaning">):Plan; recordLanguageReview(expressionId:string,direction:LanguageReviewDirection,rating:number,input?:{answer?:string|null;note?:string|null}):Evidence;
  addReadingItem(input:Partial<ReadingItem> & Pick<ReadingItem,"title">):Plan; updateReadingProgress(id:string,currentUnit:number):Evidence; addGeneralSkillDrill(input:Partial<GeneralSkillDrill> & Pick<GeneralSkillDrill,"title"|"metric"|"target">):Plan; recordGeneralSkillDrill(id:string,value:number,input?:{note?:string|null}):Evidence;
  addActivity(input: Partial<Activity> & Pick<Activity,"title"|"kind"|"durationMinutes">): Activity;
  startFocus(input?: {activityId?:string|null;durationMinutes?:number|null;note?:string|null}): FocusState; pauseFocus(): FocusState; completeFocus(input?:{note?:string|null}): StudySession|null; cancelFocus(input?:{note?:string|null}): StudySession|null; resetFocus():FocusState; setFocusNote(note:string|null):FocusState;
  setStudyCycle(subjectIds:string[]):StudyCycleState; advanceStudyCycle():StudyCycleState; skipStudyCycleSubject():StudyCycleState; moveCurrentCycleSubjectToEnd():StudyCycleState;
  addSubject(input: Partial<Subject> & Pick<Subject,"name">):Subject; updateSubject(id:string,changes:Partial<Subject>):Subject; deleteSubject(id:string):void; moveSubject(id:string,direction:"up"|"down"):void;
  addTopic(input: Partial<Topic> & Pick<Topic,"subjectId"|"name">):Topic; updateTopic(id:string,changes:Partial<Topic>):Topic; deleteTopic(id:string):void; moveTopic(id:string,direction:"up"|"down"):void;
  previewPlanContentImport(source:string,options:{format:PlanContentImportFormat;mode?:PlanContentImportMode;target?:"active"|"new-plan";planName?:string;planMode?:StudyMode}):PlanContentImportPreview; applyPlanContentImportPreview(preview:PlanContentImportPreview):StudyState;
  setScheduleConfiguration(input:Partial<ScheduleConfiguration>):Plan; setActivityLocked(activityId:string,locked?:boolean):Activity; previewSchedule(options?:Record<string,unknown>):SchedulePreview; applySchedulePreview(preview:SchedulePreview): Activity[];
  addMaterial(input: Partial<Material> & Pick<Material,"title">):Material; deleteMaterial(id:string):void;
  addGoal(input: Partial<Goal> & Pick<Goal,"name"|"metric"|"target">):Goal; toggleGoal(id:string):Goal; deleteGoal(id:string):void;
  applyPortableImportPreview(preview:PortableImportPreview):StudyState;
  previewQuestionImport(source:string,format?:QuestionImportFormat):QuestionImportPreview; applyQuestionImportPreview(preview:QuestionImportPreview):Question[]; addQuestion(input: Partial<Question> & Pick<Question,"prompt">):Question; reclassifyError(errorId:string,category:ErrorCategory):ErrorEntry; answerQuestion(questionId:string,answer:string,input?:{durationSeconds?:number|null;errorCategory?:ErrorCategory;note?:string|null}):unknown;
  completeReview(reviewId:string,input?:{rating?:number|null}):Review; resolveError(errorId:string,input?:{note?:string|null}):ErrorEntry; applyContentCleanupPreview(preview:ContentCleanupPreview):StudyState;
  replaceState(nextState:StudyState,reason?:string):StudyState; resetLocalState():StudyState;
}
