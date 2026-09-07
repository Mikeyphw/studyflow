export type StudyMode = "EXAM_PLAN" | "PROGRAMMING_COURSE" | "LANGUAGE_DECK" | "READING_STUDY" | "GENERAL_SKILL";
export type ActivityKind = "study" | "questions" | "review";
export type ActivityStatus = "planned" | "completed" | "cancelled";
export type FocusStatus = "idle" | "running" | "paused";
export type TopicPriority = "low" | "normal" | "high";
export type TopicStatus = "not-started" | "learning" | "reviewing" | "mastered" | "archived";
export type ReviewStatus = "due" | "completed" | "dismissed";
export type ErrorCategory = "content-gap" | "forgotten" | "concept-confusion" | "interpretation" | "distraction" | "calculation" | "time-pressure" | "disputed" | "other";

export interface EntityBase { id: string; createdAt: string; updatedAt: string; }
export interface Plan extends EntityBase { name: string; mode: StudyMode; status: "active" | "archived"; examDate: string | null; }
export interface Subject extends EntityBase { planId: string; name: string; weight: number | null; order: number; notes: string | null; archived: boolean; }
export interface Topic extends EntityBase { planId: string; subjectId: string; name: string; order: number; priority: TopicPriority; status: TopicStatus; notes: string | null; }
export interface Activity extends EntityBase { planId: string; subjectId: string | null; topicId: string | null; title: string; kind: ActivityKind; durationMinutes: number; status: ActivityStatus; origin: "manual"|"seed"|"cycle"|"review"|"import"|"schedule"; queue: "today"|"future"|"backlog"; scheduledFor: string | null; }
export interface StudySession extends EntityBase { planId: string; activityId: string | null; subjectId: string | null; topicId: string | null; startedAt: string; endedAt: string | null; durationMinutes: number | null; status: "running"|"paused"|"completed"|"cancelled"; note: string | null; }
export interface Goal extends EntityBase { planId: string; name: string; metric: string; target: number; period: "daily"|"weekly"|"monthly"|"plan"; active: boolean; }
export interface Material extends EntityBase { planId: string; subjectId: string | null; topicId: string | null; title: string; kind: "note"|"file"|"link"|"book"|"legislation"; locator: string | null; mimeType: string | null; attachmentId: string | null; sizeBytes: number | null; }
export interface Review extends EntityBase { planId: string; subjectId: string | null; topicId: string | null; dueAt: string; status: ReviewStatus; sourceType: string | null; sourceId: string | null; }
export interface Question extends EntityBase { planId: string; subjectId: string | null; topicId: string | null; prompt: string; choices: string[]; answer: string | null; source: string | null; explanation: string | null; disputed: boolean; }
export interface Evidence extends EntityBase { planId: string; subjectId: string | null; topicId: string | null; activityId: string | null; sessionId: string | null; questionId: string | null; kind: string; observedAt: string; value: unknown; provenance: "user"|"session"|"question"|"import"|"derived"; }
export interface ErrorEntry extends EntityBase { planId: string; subjectId: string | null; topicId: string | null; questionId: string | null; reviewId: string | null; category: ErrorCategory; note: string | null; recurrenceCount: number; status: "open"|"resolved"; observedAt: string; resolvedAt: string | null; }
export interface FocusState { status: FocusStatus; activityId: string | null; durationMinutes: number; remainingMs: number; elapsedMs: number; startedAt: string | null; lastResumedAt: string | null; endsAt: string | null; note: string | null; }
export interface FocusSnapshot extends FocusState { remainingMs: number; }
export interface StudyCycleState { subjectIds: string[]; cursor: number; }
export interface StudyState { schemaVersion: 4; revision: number; activePlanId: string | null; focus: FocusState; studyCycle: StudyCycleState; plans: Plan[]; subjects: Subject[]; topics: Topic[]; activities: Activity[]; sessions: StudySession[]; goals: Goal[]; materials: Material[]; reviews: Review[]; questions: Question[]; evidence: Evidence[]; errorEntries: ErrorEntry[]; }
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
export interface ProgressMetrics { weeklyStudyMinutes:number; gradedAttempts:number; accuracy:number|null; topicCoverage:number|null; coveredTopics:number; activeTopics:number; reviewCompletionRate:number|null; readiness:number|null; readinessReason:string; confidence:string; }
export interface StudyStore {
  getState(): StudyState; getSnapshot(): StudyState; getPersistenceStatus(): PersistenceStatus; getFocusSnapshot(at?: string|number): FocusSnapshot;
  subscribe(listener: (state: StudyState, persistence: PersistenceStatus, reason: string)=>void): ()=>void;
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
