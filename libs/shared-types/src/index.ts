// Kafka events
export * from './kafka/events';

// Domain enums shared across frontend and services
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type Language = 'PYTHON' | 'JAVA' | 'CPP' | 'JAVASCRIPT' | 'GO' | 'RUST';
export type SubmissionStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'TIME_LIMIT_EXCEEDED'
  | 'MEMORY_LIMIT_EXCEEDED'
  | 'RUNTIME_ERROR'
  | 'COMPILATION_ERROR'
  | 'SYSTEM_ERROR';
export type UserRole = 'USER' | 'ADMIN' | 'MODERATOR';
export type BadgeRarity = 'COMMON' | 'RARE' | 'EPIC' | 'LEGENDARY';

// Pagination envelope
export interface Page<T> {
  items: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
  hasNext: boolean;
}

// WebSocket message types (execution-service STOMP)
export type WsMessageType = 'STATUS_UPDATE' | 'TEST_RESULT' | 'COMPLETE' | 'ERROR';

export interface WsStatusUpdate {
  type: 'STATUS_UPDATE';
  submissionId: string;
  payload: { status: SubmissionStatus };
}

export interface WsTestResult {
  type: 'TEST_RESULT';
  submissionId: string;
  payload: {
    testCaseId: string;
    passed: boolean;
    runtimeMs: number;
    actualOutput?: string;
    errorMessage?: string;
  };
}

export interface WsComplete {
  type: 'COMPLETE';
  submissionId: string;
  payload: {
    status: SubmissionStatus;
    runtimeMs: number;
    passedTestCases: number;
    totalTestCases: number;
  };
}

export interface WsError {
  type: 'ERROR';
  submissionId: string;
  payload: { message: string };
}

export type WsMessage = WsStatusUpdate | WsTestResult | WsComplete | WsError;

// API response shapes
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: {
    id: string;
    email: string;
    displayName: string;
    avatarUrl: string | null;
    role: UserRole;
    isEmailVerified: boolean;
  };
}

export interface ProblemSummary {
  id: string;
  slug: string;
  title: string;
  difficulty: Difficulty;
  tags: string[];
  isPremium: boolean;
  totalSubmissions: number;
  acceptedSubmissions: number;
  acceptanceRate: number;
}

export interface ProblemDetail extends ProblemSummary {
  description: string;
  constraints: string;
  inputFormat: string;
  outputFormat: string;
  timeLimit: number;
  memoryLimit: number;
  sampleTestCases: Array<{
    id: string;
    input: string;
    expectedOutput: string;
    explanation: string | null;
  }>;
  topics: string[];
}

export interface Submission {
  id: string;
  userId: string;
  problemId: string;
  language: Language;
  status: SubmissionStatus;
  runtime: number | null;
  memoryUsed: number | null;
  passedTestCases: number;
  totalTestCases: number;
  errorMessage: string | null;
  createdAt: string;
}
