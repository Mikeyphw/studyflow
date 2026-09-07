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
export interface ExamNoticeRevision { id:string; acceptedAt:string; sourceHash:string; added:number; archived:number; }
export interface LegislationProvision { id:string; label:string; text:string; status:"unseen"|"studying"|"reviewing"|"known"; }
export interface LegislationDocument { id:string; title:string; reference:string|null; notes:string|null; provisions:LegislationProvision[]; }
export type ExamScoringPolicy="no-penalty"|"fixed-negative"|"right-minus-wrong"|"weighted";
export interface MockExamSection { id:string; subjectId:string|null; count:number; weight:number; }
export interface MockExamBlueprint { scoring:ExamScoringPolicy; negativePoints:number; totalQuestions:number; durationMinutes:number; sections:MockExamSection[]; }
export interface PublicExamConfiguration { target:ExamTargetProfile; stages:ExamStage[]; noticeRevisions:ExamNoticeRevision[]; reviewIntervalsDays:number[]; legislation:LegislationDocument[]; mockBlueprint:MockExamBlueprint; }
export interface ProgrammingExercise { id:string; title:string; language:string; prompt:string; starterCode:string; expectedOutput:string|null; hints:string[]; solutionNotes:string|null; difficulty:"easy"|"medium"|"hard"; status:"todo"|"attempted"|"completed"; }
export interface MiniProject { id:string; title:string; status:"planned"|"active"|"completed"; checkpoints:string[]; deliverables:string[]; }
export interface ProgrammingConfiguration { language:string; level:"beginner"|"intermediate"|"advanced"; runtime:"browser-only"; exercises:ProgrammingExercise[]; projects:MiniProject[]; }
export type LanguageReviewDirection="recognition"|"production"|"fill-blank"|"natural-usage"|"mistake-spotting"|"sentence-creation";
export interface LanguageExpression { id:string; expression:string; meaning:string; literalTranslation:string|null; region:string|null; register:string|null; pronunciationNote:string|null; commonMistakes:string[]; }
export interface LanguageConfiguration { language:string; nativeLanguage:string|null; expressions:LanguageExpression[]; reviewDirections:LanguageReviewDirection[]; }
export interface ReadingItem { id:string; title:string; totalUnits:number; currentUnit:number; unit:string; status:"planned"|"reading"|"completed"; notes:string|null; }
export interface ReadingConfiguration { items:ReadingItem[]; }
export interface GeneralSkillDrill { id:string; title:string; metric:string; target:number; unit:string; status:"active"|"paused"|"completed"; }
export interface GeneralSkillConfiguration { drills:GeneralSkillDrill[]; }
export interface PlanConfiguration { version:1; publicExam?:PublicExamConfiguration; programming?:ProgrammingConfiguration; language?:LanguageConfiguration; reading?:ReadingConfiguration; general?:GeneralSkillConfiguration; }
export interface Plan extends EntityBase { name: string; mode: StudyMode; status: "active" | "archived"; examDate: string | null; configuration:PlanConfiguration; }
export interface Subject extends EntityBase { planId: string; name: string; weight: number | null; order: number; notes: string | null; archived: boolean; }
export interface Topic extends EntityBase { planId: string; subjectId: string; name: string; order: number; priority: TopicPriority; status: TopicStatus; notes: string | null; }
export interface Activity extends EntityBase { planId: string; subjectId: string | null; topicId: string | null; title: string; kind: ActivityKind; durationMinutes: number; status: ActivityStatus; origin: "manual"|"seed"|"cycle"|"review"|"import"|"schedule"; queue: "today"|"future"|"backlog"; scheduledFor: string | null; }
export interface StudySession extends EntityBase { planId: string; activityId: string | null; subjectId: string | null; topicId: string | null; startedAt: string; endedAt: string | null; durationMinutes: number | null; status: "running"|"paused"|"completed"|"cancelled"; note: string | null; }
export interface Goal extends EntityBase { planId: string; name: string; metric: string; target: number; period: "daily"|"weekly"|"monthly"|"plan"; active: boolean; }
export interface Material extends EntityBase { planId: string; subjectId: string | null; topicId: string | null; title: string; kind: "note"|"file"|"link"|"book"|"legislation"; locator: string | null; mimeType: string | null; attachmentId: string | null; sizeBytes: number | null; }
export interface Review extends EntityBase { planId: string; subjectId: string | null; topicId: string | null; dueAt: string; status: ReviewStatus; sourceType: string | null; sourceId: string | null; }
export interface Question extends EntityBase { planId: string; subjectId: string | null; topicId: string | null; prompt: string; choices: string[]; answer: string | null; source: string | null; explanation: string | null; disputed: boolean; organization:string|null; role:string|null; examStage:string|null; examBoard:string|null; year:number|null; authorship:string|null; editalRevision:string|null; externalReference:string|null; points:number|null; }
export interface Evidence extends EntityBase { planId: string; subjectId: string | null; topicId: string | null; activityId: string | null; sessionId: string | null; questionId: string | null; kind: string; observedAt: string; value: unknown; provenance: "user"|"session"|"question"|"import"|"derived"; }
export interface ErrorEntry extends EntityBase { planId: string; subjectId: string | null; topicId: string | null; questionId: string | null; reviewId: string | null; category: ErrorCategory; note: string | null; recurrenceCount: number; status: "open"|"resolved"; observedAt: string; resolvedAt: string | null; }
export interface FocusState { status: FocusStatus; activityId: string | null; durationMinutes: number; remainingMs: number; elapsedMs: number; startedAt: string | null; lastResumedAt: string | null; endsAt: string | null; note: string | null; }
export interface FocusSnapshot extends FocusState { remainingMs: number; }
export interface StudyCycleState { subjectIds: string[]; cursor: number; }
export interface StudyState { schemaVersion: 5; revision: number; activePlanId: string | null; focus: FocusState; studyCycle: StudyCycleState; plans: Plan[]; subjects: Subject[]; topics: Topic[]; activities: Activity[]; sessions: StudySession[]; goals: Goal[]; materials: Material[]; reviews: Review[]; questions: Question[]; evidence: Evidence[]; errorEntries: ErrorEntry[]; }
export interface PersistenceStatus { status: string; message: string; durable: boolean; }
export interface RepositoryResult { status: string; state?: StudyState | null; reason?: string | null; }
export interface StudyRepository { backend?: string; load(): RepositoryResult; save(state: StudyState): RepositoryResult; clear(): RepositoryResult; flush?(): Promise<void>; close?(): void; }
export interface AttachmentMetadata { id: string; name: string; mimeType: string; sizeBytes: number; lastModified: number | null; createdAt: string; }
export interface AttachmentRecord extends AttachmentMetadata { blob: Blob; }
export interface AttachmentRepository { put(file: File|Blob, metadata?: Record<string, unknown>): Promise<AttachmentMetadata>; get(id: string): Promise<AttachmentRecord|null>; list(): Promise<AttachmentMetadata[]>; delete(id: string): Promise<void>; clear(): Promise<void>; }
export interface BrowserPersistence { backend: string; repository: StudyRepository; attachments: AttachmentRepository | null; }
export interface ScheduleOperation { action: "add"|"move"; activityId?: string; from?: string|null; to?: string; scheduledFor?: string; activity?: Partial<Activity>; reason?: string; }
export interface SchedulePreview { baseRevision: number; generatedAt: string; operations: ScheduleOperation[]; summary: {add:number; move:number}; reasons: string[]; }
export interface PortableImportPreview { baseRevision: number; generatedAt: string; packageExportedAt: string|null; sourceActivePlanId: string|null; summary: {add:number; update:number; remove:number; unchanged:number}; collections: Record<string,{add:number;update:number;remove:number;unchanged:number}>; nextState: StudyState; }

export interface ExamNoticePreview { baseRevision:number; generatedAt:string; sourceHash:string; subjects:Array<{name:string;topics:string[]}>; duplicates:string[]; unrecognized:string[]; }
export interface ExamQuestionFilter { board?:string; organization?:string; role?:string; subjectId?:string; topicId?:string; yearFrom?:number; yearTo?:number; disputed?:boolean; }
export interface MockExamPreview { baseRevision:number; generatedAt:string; blueprint:MockExamBlueprint; questionIds:string[]; deficits:string[]; ready:boolean; }
export interface FinalStretchGuidance { band:"unknown"|"over-30"|"30-day"|"15-day"|"7-day"|"2-day"|"eve"; daysRemaining:number|null; allocation:{newContent:number;reviews:number;errors:number;questions:number;mocks:number;legislation:number}; }
export interface ModeMetric { label:string; value:number|null; }
export interface ModeAnalytics { mode:StudyMode|null; metrics:ModeMetric[]; }
export interface ProgressMetrics { weeklyStudyMinutes:number; gradedAttempts:number; accuracy:number|null; topicCoverage:number|null; coveredTopics:number; activeTopics:number; reviewCompletionRate:number|null; readiness:number|null; readinessReason:string; confidence:string; }
export interface StudyStore {
  getState(): StudyState; getSnapshot(): StudyState; getPersistenceStatus(): PersistenceStatus; getFocusSnapshot(at?: string|number): FocusSnapshot;
  subscribe(listener: (state: StudyState, persistence: PersistenceStatus, reason: string)=>void): ()=>void;
  setPlanMode(mode:StudyMode):Plan; updatePublicExamTarget(input:Partial<ExamTargetProfile>):Plan; addExamStage(input:Partial<ExamStage> & Pick<ExamStage,"name">):Plan; previewExamNotice(source:string):ExamNoticePreview; applyExamNoticePreview(preview:ExamNoticePreview):Plan; addLegislation(input:{title:string;reference?:string|null;notes?:string|null;provisions?:Array<{label:string;text:string}>}):Plan; setMockBlueprint(input:Partial<MockExamBlueprint>):Plan; recordMockExam(preview:MockExamPreview,answers:Record<string,string>):Evidence;
  addProgrammingExercise(input:Partial<ProgrammingExercise> & Pick<ProgrammingExercise,"title"|"prompt">):Plan; updateProgrammingExercise(id:string,changes:Partial<ProgrammingExercise>):Plan; recordProgrammingExercise(id:string,input?:{completed?:boolean;note?:string|null}):Evidence;
  addLanguageExpression(input:Partial<LanguageExpression> & Pick<LanguageExpression,"expression"|"meaning">):Plan; recordLanguageReview(expressionId:string,direction:LanguageReviewDirection,rating:number,input?:{answer?:string|null;note?:string|null}):Evidence;
  addReadingItem(input:Partial<ReadingItem> & Pick<ReadingItem,"title">):Plan; updateReadingProgress(id:string,currentUnit:number):Evidence; addGeneralSkillDrill(input:Partial<GeneralSkillDrill> & Pick<GeneralSkillDrill,"title"|"metric"|"target">):Plan; recordGeneralSkillDrill(id:string,value:number,input?:{note?:string|null}):Evidence;
  addActivity(input: Partial<Activity> & Pick<Activity,"title"|"kind"|"durationMinutes">): Activity;
  startFocus(input?: {activityId?:string|null;durationMinutes?:number|null;note?:string|null}): FocusState; pauseFocus(): FocusState; completeFocus(input?:{note?:string|null}): StudySession|null; cancelFocus(input?:{note?:string|null}): StudySession|null; resetFocus():FocusState; setFocusNote(note:string|null):FocusState;
  setStudyCycle(subjectIds:string[]):StudyCycleState; advanceStudyCycle():StudyCycleState;
  addSubject(input: Partial<Subject> & Pick<Subject,"name">):Subject; updateSubject(id:string,changes:Partial<Subject>):Subject; deleteSubject(id:string):void; moveSubject(id:string,direction:"up"|"down"):void;
  addTopic(input: Partial<Topic> & Pick<Topic,"subjectId"|"name">):Topic; updateTopic(id:string,changes:Partial<Topic>):Topic; deleteTopic(id:string):void; moveTopic(id:string,direction:"up"|"down"):void;
  previewSchedule(options?:Record<string,unknown>):SchedulePreview; applySchedulePreview(preview:SchedulePreview): Activity[];
  addMaterial(input: Partial<Material> & Pick<Material,"title">):Material; deleteMaterial(id:string):void;
  addGoal(input: Partial<Goal> & Pick<Goal,"name"|"metric"|"target">):Goal; toggleGoal(id:string):Goal; deleteGoal(id:string):void;
  applyPortableImportPreview(preview:PortableImportPreview):StudyState;
  addQuestion(input: Partial<Question> & Pick<Question,"prompt">):Question; answerQuestion(questionId:string,answer:string,input?:{durationSeconds?:number|null;errorCategory?:ErrorCategory;note?:string|null}):unknown;
  completeReview(reviewId:string,input?:{rating?:number|null}):Review; resolveError(errorId:string,input?:{note?:string|null}):ErrorEntry;
  replaceState(nextState:StudyState,reason?:string):StudyState; resetLocalState():StudyState;
}
