/**
 * AlgoVerse k6 WebSocket load test — collaboration-service
 *
 * Usage:
 *   k6 run testing/load/scripts/collaboration.js
 *   BASE_URL=http://staging.example.com k6 run testing/load/scripts/collaboration.js
 *
 * What it tests:
 *   - STOMP WebSocket connection establishment
 *   - Room creation via REST, then join via WS
 *   - Concurrent operation broadcast (simulating collaborative edits)
 *   - Presence heartbeat under load
 *   - Graceful leave and disconnect
 */

import ws from 'k6/ws';
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ── Metrics ───────────────────────────────────────────────────────────────────
const wsConnectErrors   = new Counter('ws_connect_errors');
const wsMessagesSent    = new Counter('ws_messages_sent');
const wsMessagesRecv    = new Counter('ws_messages_received');
const opBroadcastTime   = new Trend('op_broadcast_ms', true);
const roomCreateTime    = new Trend('room_create_ms', true);
const errorRate         = new Rate('errors');

// ── Config ────────────────────────────────────────────────────────────────────
const BASE_HTTP = __ENV.BASE_URL      || 'http://localhost:8088';
const BASE_WS   = __ENV.WS_URL        || 'ws://localhost:8088';
const WS_PATH   = '/ws-collab/websocket';

export const options = {
  stages: [
    { duration: '20s', target: 5  },   // ramp up to 5 concurrent users
    { duration: '1m',  target: 20 },   // ramp to 20 (multi-room steady state)
    { duration: '30s', target: 50 },   // spike — many rooms
    { duration: '1m',  target: 50 },   // hold spike
    { duration: '30s', target: 0  },   // ramp down
  ],
  thresholds: {
    ws_connect_errors:       ['count<5'],
    errors:                  ['rate<0.05'],
    op_broadcast_ms:         ['p(95)<400'],
    room_create_ms:          ['p(95)<800'],
    http_req_duration:       ['p(95)<1000'],
  },
};

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Build a minimal STOMP CONNECT frame (no auth for load test — security disabled in test profile) */
function stompConnect() {
  return 'CONNECT\naccept-version:1.2\nheart-beat:10000,10000\n\n\0';
}

/** Build a STOMP SUBSCRIBE frame */
function stompSubscribe(id, destination) {
  return `SUBSCRIBE\nid:${id}\ndestination:${destination}\n\n\0`;
}

/** Build a STOMP SEND frame */
function stompSend(destination, body) {
  const bodyStr = JSON.stringify(body);
  return `SEND\ndestination:${destination}\ncontent-type:application/json\ncontent-length:${bodyStr.length}\n\n${bodyStr}\0`;
}

/** Create a room via REST, return the room code */
function createRoom(token) {
  const start = Date.now();
  const res = http.post(
    `${BASE_HTTP}/api/v1/collab/rooms`,
    JSON.stringify({ language: 'javascript', type: 'PRACTICE', isPublic: false }),
    {
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
    }
  );
  roomCreateTime.add(Date.now() - start);

  const ok = check(res, {
    'room created: status 200 or 201': (r) => r.status === 200 || r.status === 201,
    'room created: has code':           (r) => {
      try { return !!JSON.parse(r.body).code; } catch { return false; }
    },
  });
  if (!ok) { errorRate.add(1); return null; }

  return JSON.parse(res.body).code;
}

// ── Default VU function ───────────────────────────────────────────────────────

export default function () {
  // 1. Create a room (each VU owns its own room to avoid serialisation contention)
  const roomCode = createRoom(null);
  if (!roomCode) {
    sleep(2);
    return;
  }

  // 2. Open STOMP WebSocket
  const wsUrl = `${BASE_WS}${WS_PATH}`;
  let connected       = false;
  let subscribed      = false;
  let opSentAt        = 0;
  let opsAcked        = 0;
  const TARGET_OPS    = 5;

  const res = ws.connect(wsUrl, {}, (socket) => {

    socket.on('open', () => {
      // Send STOMP CONNECT
      socket.send(stompConnect());
      wsMessagesSent.add(1);
    });

    socket.on('message', (data) => {
      wsMessagesRecv.add(1);

      // CONNECTED frame
      if (!connected && data.startsWith('CONNECTED')) {
        connected = true;

        // Subscribe to room topic
        socket.send(stompSubscribe('sub-0', `/topic/room/${roomCode}`));
        wsMessagesSent.add(1);

        // Send JOIN
        socket.send(stompSend('/app/collab.join', {
          type: 'JOIN',
          roomCode,
          payload: {},
        }));
        wsMessagesSent.add(1);
        subscribed = true;
      }

      // Any MESSAGE frame — record op broadcast latency if we sent an op
      if (data.startsWith('MESSAGE') && opSentAt > 0) {
        opBroadcastTime.add(Date.now() - opSentAt);
        opSentAt = 0;
        opsAcked++;
      }

      // Once subscribed and joined, start sending operations
      if (subscribed && opsAcked < TARGET_OPS) {
        opSentAt = Date.now();
        socket.send(stompSend('/app/collab.operation', {
          type: 'OPERATION',
          roomCode,
          payload: {
            type: 'INSERT',
            position: opsAcked * 5,
            text: 'hello',
            clientVersion: opsAcked,
          },
        }));
        wsMessagesSent.add(1);
      }

      // All ops acknowledged — send LEAVE and close
      if (opsAcked >= TARGET_OPS) {
        socket.send(stompSend('/app/collab.leave', {
          type: 'LEAVE',
          roomCode,
          payload: {},
        }));
        wsMessagesSent.add(1);
        socket.close();
      }
    });

    socket.on('error', (e) => {
      wsConnectErrors.add(1);
      errorRate.add(1);
    });

    // Safety timeout — close after 15s regardless
    socket.setTimeout(() => { socket.close(); }, 15000);
  });

  check(res, { 'ws status is 101': (r) => r && r.status === 101 }) || errorRate.add(1);

  sleep(1);
}
