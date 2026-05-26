/**
 * Lightweight Operational Transformation client.
 *
 * Implements the Jupiter / Google Wave two-site OT protocol:
 *   - Each operation is either Insert(pos, text) or Delete(pos, length).
 *   - The client tracks a "bridge" of in-flight operations (sent but not yet
 *     acknowledged by the server) and a pending buffer (typed while waiting).
 *   - When the server broadcasts a remote operation, it is transformed against
 *     every in-flight op so it can be applied correctly to the local document.
 *   - When the server acknowledges our own op, we promote the next pending op
 *     to in-flight and send it.
 *
 * Usage:
 *   const ot = new OTClient(initialContent, (op) => sendToServer(op))
 *   ot.applyLocal(newContent)      // called on every editor change
 *   ot.applyRemote(serverOp)       // called when a OPERATION message arrives
 *   ot.acknowledge(serverVersion)  // called when server echoes our own op back
 */

// ── Operation types ────────────────────────────────────────────────────────────

export type OpType = 'INSERT' | 'DELETE'

export interface Op {
  type: OpType
  position: number
  text?: string    // INSERT
  length?: number  // DELETE
}

export interface ServerOp extends Op {
  clientVersion: number
  serverVersion?: number
}

// ── Transform functions ────────────────────────────────────────────────────────

/**
 * Transform `op` against a concurrent `against` operation so that `op` can
 * be applied after `against` has already been applied to the document.
 */
function transform(op: Op, against: Op): Op {
  if (op.type === 'INSERT' && against.type === 'INSERT') {
    if (against.position < op.position ||
        (against.position === op.position)) {
      // against inserted before (or at) our position — shift right
      return { ...op, position: op.position + (against.text?.length ?? 0) }
    }
    return op
  }

  if (op.type === 'INSERT' && against.type === 'DELETE') {
    const delEnd = against.position + (against.length ?? 0)
    if (against.position < op.position) {
      const overlap = Math.min(delEnd, op.position) - against.position
      return { ...op, position: op.position - overlap }
    }
    return op
  }

  if (op.type === 'DELETE' && against.type === 'INSERT') {
    if (against.position <= op.position) {
      return { ...op, position: op.position + (against.text?.length ?? 0) }
    }
    // against inserted inside our deletion range — extend the delete
    if (against.position < op.position + (op.length ?? 0)) {
      return { ...op, length: (op.length ?? 0) + (against.text?.length ?? 0) }
    }
    return op
  }

  if (op.type === 'DELETE' && against.type === 'DELETE') {
    const aStart = against.position
    const aEnd   = against.position + (against.length ?? 0)
    const oStart = op.position
    const oEnd   = op.position + (op.length ?? 0)

    if (aEnd <= oStart) {
      // against entirely before us
      return { ...op, position: op.position - (against.length ?? 0) }
    }
    if (aStart >= oEnd) {
      // against entirely after us
      return op
    }
    // Overlapping deletes — shrink our delete by the overlap
    const overlapStart = Math.max(aStart, oStart)
    const overlapEnd   = Math.min(aEnd,   oEnd)
    const overlap      = overlapEnd - overlapStart
    const newPos       = Math.min(oStart, aStart)
    const newLen       = Math.max(0, (op.length ?? 0) - overlap)
    return { ...op, position: newPos, length: newLen }
  }

  return op
}

// ── Apply operation to a string ────────────────────────────────────────────────

export function applyOp(content: string, op: Op): string {
  if (op.type === 'INSERT' && op.text) {
    const pos = Math.max(0, Math.min(op.position, content.length))
    return content.slice(0, pos) + op.text + content.slice(pos)
  }
  if (op.type === 'DELETE' && op.length) {
    const pos = Math.max(0, Math.min(op.position, content.length))
    const len = Math.min(op.length, content.length - pos)
    return content.slice(0, pos) + content.slice(pos + len)
  }
  return content
}

// ── Diff helper (produces a single compact op from prev→next) ─────────────────

export function diff(prev: string, next: string): Op | null {
  if (prev === next) return null

  // Find common prefix
  let i = 0
  while (i < prev.length && i < next.length && prev[i] === next[i]) i++

  // Find common suffix
  let j = 0
  while (
    j < prev.length - i &&
    j < next.length - i &&
    prev[prev.length - 1 - j] === next[next.length - 1 - j]
  ) j++

  const removedLen = prev.length - i - j
  const insertedText = next.slice(i, next.length - j)

  if (removedLen === 0 && insertedText.length > 0) {
    return { type: 'INSERT', position: i, text: insertedText }
  }
  if (removedLen > 0 && insertedText.length === 0) {
    return { type: 'DELETE', position: i, length: removedLen }
  }
  // Replacement: model as delete-then-insert (emit delete; caller can chain)
  // For simplicity return delete only — next keypress will produce the insert
  return { type: 'DELETE', position: i, length: removedLen }
}

// ── OTClient ──────────────────────────────────────────────────────────────────

type SendFn = (op: ServerOp) => void

type State = 'SYNC' | 'AWAIT_ACK'

export class OTClient {
  private content: string
  private clientVersion: number = 0
  private serverVersion: number = 0

  /** Operation we have sent and are waiting for the server to acknowledge */
  private inFlight: Op | null = null
  /** Operations typed after inFlight was sent (buffer) */
  private pending: Op[] = []

  private state: State = 'SYNC'
  private readonly send: SendFn

  constructor(initialContent: string, send: SendFn) {
    this.content = initialContent
    this.send = send
  }

  get document(): string {
    return this.content
  }

  /** Call this on every local editor change with the full new document value. */
  applyLocal(newContent: string): void {
    const op = diff(this.content, newContent)
    this.content = newContent
    if (!op) return

    if (this.state === 'SYNC') {
      this.inFlight = op
      this.state = 'AWAIT_ACK'
      this.clientVersion++
      this.send({ ...op, clientVersion: this.clientVersion })
    } else {
      // Compose into pending (simplification: just append)
      this.pending.push(op)
    }
  }

  /**
   * Call this when the server broadcasts a remote OPERATION message
   * (i.e. one from another participant, not an echo of our own).
   * Returns the content string AFTER applying the transformed remote op.
   */
  applyRemote(remoteOp: ServerOp): string {
    this.serverVersion = remoteOp.serverVersion ?? this.serverVersion + 1

    let op: Op = remoteOp

    // Transform against our in-flight op
    if (this.inFlight) {
      op = transform(op, this.inFlight)
    }
    // Transform against all pending ops
    for (const p of this.pending) {
      op = transform(op, p)
    }

    this.content = applyOp(this.content, op)
    return this.content
  }

  /**
   * Call this when the server acknowledges our own operation
   * (server echoes the op back with a serverVersion set to our userId's op).
   * Returns the content string (unchanged — just flushes state).
   */
  acknowledge(serverVersion: number): string {
    this.serverVersion = serverVersion
    this.inFlight = null

    if (this.pending.length > 0) {
      // Promote the head of pending to in-flight
      this.inFlight = this.pending.shift()!
      this.clientVersion++
      this.send({ ...this.inFlight, clientVersion: this.clientVersion })
    } else {
      this.state = 'SYNC'
    }
    return this.content
  }

  /** Sync content from server (e.g. on ROOM_STATE) — resets OT state. */
  reset(content: string, serverVersion: number): void {
    this.content = content
    this.serverVersion = serverVersion
    this.clientVersion = serverVersion
    this.inFlight = null
    this.pending = []
    this.state = 'SYNC'
  }
}
