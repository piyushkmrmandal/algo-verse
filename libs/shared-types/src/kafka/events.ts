/**
 * AlgoVerse Kafka Event Type Definitions
 *
 * All Kafka events share a common envelope (KafkaEvent<T>) and use
 * discriminated unions so TypeScript can narrow payload types from eventType.
 *
 * Version: 1.0
 * Schema registry subject naming: <topic-name>-value
 */

// ---------------------------------------------------------------------------
// Base Envelope
// ---------------------------------------------------------------------------

/**
 * Universal envelope wrapping every Kafka message produced by AlgoVerse services.
 * The `payload` field carries the domain-specific data; all other fields are
 * infrastructure-level concerns (tracing, schema versioning, audit).
 */
export interface KafkaEvent<T = unknown> {
  /** UUID v4 — globally unique identifier for this specific event instance */
  eventId: string;
  /** Discriminant used for consumer routing, e.g. 'submission.judged' */
  eventType: string;
  /** The primary entity this event is about, e.g. submissionId for a judged event */
  aggregateId: string;
  /** Domain entity type, e.g. 'Submission', 'User', 'Room' */
  aggregateType: string;
  /** Monotonically increasing schema version for this event type (starts at 1) */
  version: number;
  /** ISO 8601 timestamp of when the domain event occurred */
  occurredAt: string;
  /** Propagated from upstream HTTP request for distributed tracing (X-Correlation-ID) */
  correlationId: string;
  /** eventId of the upstream event that caused this one (optional causal chain) */
  causationId?: string;
  /** Domain-specific event data */
  payload: T;
  metadata: KafkaEventMetadata;
}

export interface KafkaEventMetadata {
  /** Logical service name that produced this event, e.g. 'execution-service' */
  serviceId: string;
  /** AWS region or datacenter identifier, e.g. 'us-east-1' */
  region: string;
  /** Subject user if the event was triggered by a user action */
  userId?: string;
}

// ---------------------------------------------------------------------------
// Topic Name Constants
// ---------------------------------------------------------------------------

export const KafkaTopics = {
  SUBMISSIONS_CREATED: 'algoverse.submissions.created',
  SUBMISSIONS_JUDGED: 'algoverse.submissions.judged',
  USERS_REGISTERED: 'algoverse.users.registered',
  USERS_XP_UPDATED: 'algoverse.users.xp-updated',
  STREAKS_UPDATED: 'algoverse.streaks.updated',
  BADGES_EARNED: 'algoverse.badges.earned',
  ROOMS_CREATED: 'algoverse.rooms.created',
  ROOMS_ENDED: 'algoverse.rooms.ended',
  AI_HINT_REQUESTED: 'algoverse.ai.hint-requested',
  NOTIFICATIONS_REQUESTED: 'algoverse.notifications.requested',
  ANALYTICS_EVENTS: 'algoverse.analytics.events',
  LEADERBOARD_UPDATED: 'algoverse.leaderboard.updated',
  PROBLEMS_SOLVED_FIRST_TIME: 'algoverse.problems.solved-first-time',
} as const;

export type KafkaTopic = (typeof KafkaTopics)[keyof typeof KafkaTopics];

// ---------------------------------------------------------------------------
// Event Type String Constants
// ---------------------------------------------------------------------------

export const KafkaEventTypes = {
  SUBMISSION_CREATED: 'submission.created',
  SUBMISSION_JUDGED: 'submission.judged',
  USER_REGISTERED: 'user.registered',
  USER_XP_UPDATED: 'user.xp-updated',
  STREAK_UPDATED: 'streak.updated',
  BADGE_EARNED: 'badge.earned',
  ROOM_CREATED: 'room.created',
  ROOM_ENDED: 'room.ended',
  HINT_REQUESTED: 'hint.requested',
  NOTIFICATION_REQUESTED: 'notification.requested',
  ANALYTICS_EVENT: 'analytics.event',
  LEADERBOARD_UPDATED: 'leaderboard.updated',
  PROBLEM_SOLVED_FIRST_TIME: 'problem.solved-first-time',
} as const;

export type KafkaEventType = (typeof KafkaEventTypes)[keyof typeof KafkaEventTypes];

// ---------------------------------------------------------------------------
// Shared Domain Enums
// ---------------------------------------------------------------------------

export type ProblemDifficulty = 'EASY' | 'MEDIUM' | 'HARD';

export type SubmissionStatus =
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'TIME_LIMIT_EXCEEDED'
  | 'MEMORY_LIMIT_EXCEEDED'
  | 'RUNTIME_ERROR'
  | 'COMPILE_ERROR'
  | 'INTERNAL_ERROR';

export type ProgrammingLanguage =
  | 'PYTHON'
  | 'JAVA'
  | 'CPP'
  | 'C'
  | 'JAVASCRIPT'
  | 'TYPESCRIPT'
  | 'GO'
  | 'RUST'
  | 'KOTLIN'
  | 'SWIFT';

export type OAuthProvider = 'GOOGLE' | 'GITHUB' | 'DISCORD';

export type NotificationChannel = 'EMAIL' | 'PUSH' | 'IN_APP';

export type BadgeRarity = 'COMMON' | 'UNCOMMON' | 'RARE' | 'EPIC' | 'LEGENDARY';

export type XpSource =
  | 'SUBMISSION_ACCEPTED'
  | 'FIRST_SOLVE_BONUS'
  | 'STREAK_BONUS'
  | 'BADGE_REWARD'
  | 'DAILY_LOGIN'
  | 'COLLABORATION_BONUS'
  | 'HINT_PENALTY';

export type RoomType = 'PAIR_PROGRAMMING' | 'COMPETITIVE' | 'PRACTICE';

export type LeaderboardPeriod = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'ALL_TIME';

export type HintLevel = 1 | 2 | 3;

export type DeviceType = 'DESKTOP' | 'MOBILE' | 'TABLET';

// ---------------------------------------------------------------------------
// Payload Definitions
// ---------------------------------------------------------------------------

/**
 * Fired when a submission record is created and queued for execution.
 * Topic: algoverse.submissions.created
 * Partition key: userId
 */
export interface SubmissionCreatedPayload {
  submissionId: string;
  userId: string;
  problemId: string;
  problemSlug: string;
  language: ProgrammingLanguage;
  /** Code size in bytes — used for resource pre-allocation */
  codeSizeBytes: number;
  /** 1-based position in the execution queue at time of submission */
  queuePosition: number;
  /** Estimated wait time in seconds based on current queue depth */
  estimatedWaitSeconds: number;
}

/**
 * Fired when the judge sandbox completes evaluation of a submission.
 * Topic: algoverse.submissions.judged
 * Partition key: submissionId
 */
export interface SubmissionJudgedPayload {
  submissionId: string;
  userId: string;
  problemId: string;
  problemSlug: string;
  status: SubmissionStatus;
  /** Wall-clock execution time in milliseconds */
  runtimeMs: number;
  /** Peak memory usage in megabytes */
  memoryMb: number;
  testCasesPassed: number;
  testCasesTotal: number;
  language: ProgrammingLanguage;
  difficulty: ProblemDifficulty;
  /** True if this is the user's first ACCEPTED submission for this problem */
  isFirstAccepted: boolean;
  /** Percentile rank for runtime among all accepted submissions (0–100) */
  runtimePercentile?: number;
  /** Percentile rank for memory among all accepted submissions (0–100) */
  memoryPercentile?: number;
  /** Total time the submission spent in queue + execution, in ms */
  totalProcessingMs: number;
}

/**
 * Fired when a new user account is successfully created.
 * Topic: algoverse.users.registered
 * Partition key: userId
 */
export interface UserRegisteredPayload {
  userId: string;
  email: string;
  displayName: string;
  /** Null for email/password registrations */
  oauthProvider?: OAuthProvider;
  /** Country code from IP geolocation (ISO 3166-1 alpha-2), e.g. 'US' */
  countryCode?: string;
  /** UTM source tag for acquisition attribution */
  utmSource?: string;
  /** UTM campaign tag */
  utmCampaign?: string;
}

/**
 * Fired when a user's XP balance changes for any reason.
 * Topic: algoverse.users.xp-updated
 * Partition key: userId
 */
export interface XpUpdatedPayload {
  userId: string;
  /** Change in XP (positive = gain, negative = penalty) */
  deltaXp: number;
  /** Absolute XP total after applying deltaXp */
  newTotalXp: number;
  /** User's level after applying the XP change */
  newLevel: number;
  /** Previous level — used to detect level-up events downstream */
  previousLevel: number;
  /** Whether this XP update triggered a level-up */
  isLevelUp: boolean;
  source: XpSource;
  /** ID of the entity that caused the XP change (e.g. submissionId, badgeId) */
  referenceId: string;
}

/**
 * Fired when a user's daily coding streak is evaluated.
 * Topic: algoverse.streaks.updated
 * Partition key: userId
 */
export interface StreakUpdatedPayload {
  userId: string;
  /** Current active streak count in days */
  currentStreak: number;
  /** All-time longest streak for this user */
  longestStreak: number;
  /** The calendar date (YYYY-MM-DD) this streak entry represents */
  date: string;
  /** True if a previously active streak was broken (currentStreak resets to 1 or 0) */
  streakBroken: boolean;
  /** Previous streak length before this update — useful for milestone detection */
  previousStreak: number;
}

/**
 * Fired when a user unlocks a badge.
 * Topic: algoverse.badges.earned
 * Partition key: userId
 */
export interface BadgeEarnedPayload {
  userId: string;
  badgeId: string;
  /** URL-friendly unique identifier, e.g. 'first-blood', 'century-streak' */
  badgeSlug: string;
  badgeName: string;
  /** Human-readable description displayed in the badge UI */
  badgeDescription: string;
  rarity: BadgeRarity;
  /** XP awarded as part of the badge unlock */
  xpReward: number;
  /** URL to the badge icon image in S3/CDN */
  iconUrl: string;
}

/**
 * Fired when a new collaborative coding room is created.
 * Topic: algoverse.rooms.created
 * Partition key: roomId
 */
export interface RoomCreatedPayload {
  roomId: string;
  /** Short alphanumeric code used by guests to join, e.g. 'XK29TM' */
  roomCode: string;
  hostId: string;
  type: RoomType;
  /** Present for practice and competitive modes; absent for open-ended sessions */
  problemId?: string;
  problemSlug?: string;
  /** Maximum number of participants allowed */
  maxParticipants: number;
  /** Scheduled end time if a time limit is set (ISO 8601) */
  scheduledEndAt?: string;
  /** User IDs of participants invited at room creation */
  invitedUserIds: string[];
}

/**
 * Fired when a collaborative room session ends.
 * Topic: algoverse.rooms.ended
 * Partition key: roomId
 */
export interface RoomEndedPayload {
  roomId: string;
  hostId: string;
  /** Number of unique participants who joined during the session */
  participantCount: number;
  /** Total active session duration in minutes */
  durationMinutes: number;
  problemId?: string;
  problemSlug?: string;
  /** How the room was terminated */
  endReason: 'HOST_ENDED' | 'INACTIVITY_TIMEOUT' | 'SCHEDULED_END' | 'SYSTEM_SHUTDOWN';
  /** userId → submissionStatus map if a problem was assigned */
  participantResults?: Record<string, SubmissionStatus>;
}

/**
 * Fired after an AI hint is generated and delivered to the user.
 * Topic: algoverse.ai.hint-requested
 * Partition key: userId
 */
export interface HintRequestedPayload {
  hintId: string;
  userId: string;
  problemId: string;
  problemSlug: string;
  /** 1 = nudge, 2 = partial solution, 3 = full approach */
  hintLevel: HintLevel;
  /** Total LLM response latency in milliseconds */
  latencyMs: number;
  /** Which LLM model was used for generation */
  modelId: string;
  /** Number of prompt cache tokens used (for cost tracking) */
  promptCacheTokens: number;
  /** Number of completion tokens used */
  completionTokens: number;
  /** How many hints the user has remaining for this problem today */
  hintsRemaining: number;
}

/**
 * Generic notification dispatch request published by any service.
 * Topic: algoverse.notifications.requested
 * Partition key: userId
 */
export interface NotificationRequestedPayload {
  userId: string;
  channel: NotificationChannel;
  /** Logical notification type used to select template, e.g. 'badge.earned', 'streak.milestone' */
  type: string;
  /** Template variables — keys match the notification template placeholders */
  data: Record<string, unknown>;
  /** If set, do not deliver after this timestamp (ISO 8601) */
  expiresAt?: string;
  /** Deduplication key — prevents duplicate notifications within a time window */
  idempotencyKey?: string;
}

/**
 * Single analytics event for the high-volume firehose.
 * Topic: algoverse.analytics.events
 * Partition key: sessionId
 */
export interface AnalyticsEventPayload {
  /** Browser/app session identifier, rotated per-session */
  sessionId: string;
  /** Null for unauthenticated events */
  userId?: string;
  /** Snake_case event name, e.g. 'submission_created', 'page_view', 'hint_requested' */
  eventName: string;
  /** Arbitrary key-value pairs specific to this event type */
  properties: Record<string, unknown>;
  /** Full URL of the page where the event occurred */
  pageUrl?: string;
  deviceType?: DeviceType;
  /** User-Agent string (truncated to 256 chars) */
  userAgent?: string;
  /** Client IP — stored hashed for privacy compliance */
  ipHash?: string;
}

/**
 * Leaderboard snapshot published after a recalculation cycle.
 * Topic: algoverse.leaderboard.updated
 * Partition key: period
 */
export interface LeaderboardUpdatedPayload {
  period: LeaderboardPeriod;
  /** The date this snapshot represents (YYYY-MM-DD for daily/weekly, YYYY-MM for monthly) */
  date: string;
  /** Top N entries in this leaderboard snapshot (N is configurable, default 100) */
  topEntries: LeaderboardEntry[];
  /** Total number of users with at least 1 XP in this period */
  totalParticipants: number;
}

export interface LeaderboardEntry {
  userId: string;
  displayName: string;
  avatarUrl?: string;
  rank: number;
  xp: number;
  /** Change in rank since the previous snapshot (+3 means moved up 3 places) */
  rankDelta: number;
}

/**
 * Fired when a user solves a problem for the first time (first AC verdict).
 * Distinct from SubmissionJudged to simplify downstream idempotency checks.
 * Topic: algoverse.problems.solved-first-time
 * Partition key: userId
 */
export interface ProblemSolvedFirstTimePayload {
  userId: string;
  problemId: string;
  problemSlug: string;
  difficulty: ProblemDifficulty;
  language: ProgrammingLanguage;
  /** Runtime of the first accepted submission */
  runtimeMs: number;
  /** Memory of the first accepted submission */
  memoryMb: number;
  submissionId: string;
  /** Total number of unique problems this user has solved (including this one) */
  totalSolvedCount: number;
  /** How many attempts (submissions) it took to get the first AC */
  attemptCount: number;
}

// ---------------------------------------------------------------------------
// Typed KafkaEvent Aliases
// ---------------------------------------------------------------------------

export type SubmissionCreatedEvent = KafkaEvent<SubmissionCreatedPayload> & {
  eventType: typeof KafkaEventTypes.SUBMISSION_CREATED;
  aggregateType: 'Submission';
};

export type SubmissionJudgedEvent = KafkaEvent<SubmissionJudgedPayload> & {
  eventType: typeof KafkaEventTypes.SUBMISSION_JUDGED;
  aggregateType: 'Submission';
};

export type UserRegisteredEvent = KafkaEvent<UserRegisteredPayload> & {
  eventType: typeof KafkaEventTypes.USER_REGISTERED;
  aggregateType: 'User';
};

export type XpUpdatedEvent = KafkaEvent<XpUpdatedPayload> & {
  eventType: typeof KafkaEventTypes.USER_XP_UPDATED;
  aggregateType: 'User';
};

export type StreakUpdatedEvent = KafkaEvent<StreakUpdatedPayload> & {
  eventType: typeof KafkaEventTypes.STREAK_UPDATED;
  aggregateType: 'User';
};

export type BadgeEarnedEvent = KafkaEvent<BadgeEarnedPayload> & {
  eventType: typeof KafkaEventTypes.BADGE_EARNED;
  aggregateType: 'Badge';
};

export type RoomCreatedEvent = KafkaEvent<RoomCreatedPayload> & {
  eventType: typeof KafkaEventTypes.ROOM_CREATED;
  aggregateType: 'Room';
};

export type RoomEndedEvent = KafkaEvent<RoomEndedPayload> & {
  eventType: typeof KafkaEventTypes.ROOM_ENDED;
  aggregateType: 'Room';
};

export type HintRequestedEvent = KafkaEvent<HintRequestedPayload> & {
  eventType: typeof KafkaEventTypes.HINT_REQUESTED;
  aggregateType: 'Hint';
};

export type NotificationRequestedEvent = KafkaEvent<NotificationRequestedPayload> & {
  eventType: typeof KafkaEventTypes.NOTIFICATION_REQUESTED;
  aggregateType: 'Notification';
};

export type AnalyticsEvent = KafkaEvent<AnalyticsEventPayload> & {
  eventType: typeof KafkaEventTypes.ANALYTICS_EVENT;
  aggregateType: 'AnalyticsEvent';
};

export type LeaderboardUpdatedEvent = KafkaEvent<LeaderboardUpdatedPayload> & {
  eventType: typeof KafkaEventTypes.LEADERBOARD_UPDATED;
  aggregateType: 'Leaderboard';
};

export type ProblemSolvedFirstTimeEvent = KafkaEvent<ProblemSolvedFirstTimePayload> & {
  eventType: typeof KafkaEventTypes.PROBLEM_SOLVED_FIRST_TIME;
  aggregateType: 'Problem';
};

// ---------------------------------------------------------------------------
// Discriminated Union — exhaustive switch on eventType
// ---------------------------------------------------------------------------

/**
 * Master discriminated union of all AlgoVerse Kafka events.
 * Use this type in generic consumers that handle multiple event types.
 *
 * @example
 * function handle(event: AlgoVerseKafkaEvent) {
 *   switch (event.eventType) {
 *     case KafkaEventTypes.SUBMISSION_JUDGED:
 *       // event.payload is SubmissionJudgedPayload — fully typed
 *       break;
 *     case KafkaEventTypes.BADGE_EARNED:
 *       // event.payload is BadgeEarnedPayload
 *       break;
 *   }
 * }
 */
export type AlgoVerseKafkaEvent =
  | SubmissionCreatedEvent
  | SubmissionJudgedEvent
  | UserRegisteredEvent
  | XpUpdatedEvent
  | StreakUpdatedEvent
  | BadgeEarnedEvent
  | RoomCreatedEvent
  | RoomEndedEvent
  | HintRequestedEvent
  | NotificationRequestedEvent
  | AnalyticsEvent
  | LeaderboardUpdatedEvent
  | ProblemSolvedFirstTimeEvent;

// ---------------------------------------------------------------------------
// Type Guards
// ---------------------------------------------------------------------------

export function isSubmissionJudgedEvent(e: AlgoVerseKafkaEvent): e is SubmissionJudgedEvent {
  return e.eventType === KafkaEventTypes.SUBMISSION_JUDGED;
}

export function isBadgeEarnedEvent(e: AlgoVerseKafkaEvent): e is BadgeEarnedEvent {
  return e.eventType === KafkaEventTypes.BADGE_EARNED;
}

export function isStreakUpdatedEvent(e: AlgoVerseKafkaEvent): e is StreakUpdatedEvent {
  return e.eventType === KafkaEventTypes.STREAK_UPDATED;
}

export function isNotificationRequestedEvent(
  e: AlgoVerseKafkaEvent,
): e is NotificationRequestedEvent {
  return e.eventType === KafkaEventTypes.NOTIFICATION_REQUESTED;
}

export function isProblemSolvedFirstTimeEvent(
  e: AlgoVerseKafkaEvent,
): e is ProblemSolvedFirstTimeEvent {
  return e.eventType === KafkaEventTypes.PROBLEM_SOLVED_FIRST_TIME;
}
