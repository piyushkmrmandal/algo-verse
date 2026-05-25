package com.algoverse.collaboration.websocket;

import com.algoverse.collaboration.domain.RoomEvent;
import com.algoverse.collaboration.dto.*;
import com.algoverse.collaboration.ot.OtEngine;
import com.algoverse.collaboration.ot.TextOperation;
import com.algoverse.collaboration.repository.RoomEventRepository;
import com.algoverse.collaboration.repository.RoomParticipantRepository;
import com.algoverse.collaboration.service.DocumentStateService;
import com.algoverse.collaboration.service.PresenceService;
import com.algoverse.collaboration.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CollabMessageHandler {

    private final SimpMessagingTemplate broker;
    private final OtEngine otEngine;
    private final DocumentStateService docService;
    private final PresenceService presenceService;
    private final RoomService roomService;
    private final RoomParticipantRepository participantRepo;
    private final RoomEventRepository eventRepo;

    @Value("${collab.snapshot-interval:50}")
    private int snapshotInterval;

    // Per-room history of committed ops since last snapshot, for OT transform
    private final ConcurrentHashMap<String, java.util.Deque<TextOperation>> opHistory =
            new ConcurrentHashMap<>();

    // ──────────────────────────────────────────────────────────────────────────
    // JOIN — client subscribes, then sends JOIN to get the current room state
    // ──────────────────────────────────────────────────────────────────────────
    @MessageMapping("/room/{roomId}/join")
    @SendToUser("/queue/room-state")
    public WsMessage handleJoin(
            @DestinationVariable String roomId,
            Principal principal
    ) {
        UUID uid = UUID.fromString(principal.getName());
        UUID rid = UUID.fromString(roomId);

        presenceService.markOnline(rid, uid);

        // Notify everyone else that this user joined
        broker.convertAndSend("/topic/room/" + roomId,
                WsMessage.of(WsMessage.Type.PARTICIPANT_JOIN, principal.getName(),
                        Map.of("userId", principal.getName()), docService.getVersion(rid)));

        // Return full state to the joining user only
        RoomDto room = roomService.getRoomById(rid);
        RoomStatePayload state = RoomStatePayload.builder()
                .content(docService.getContent(rid))
                .version(docService.getVersion(rid))
                .language(room.getSettings().getOrDefault("language", "javascript").toString())
                .participants(room.getParticipants())
                .settings(room.getSettings())
                .build();

        return WsMessage.of(WsMessage.Type.ROOM_STATE, "server", state, docService.getVersion(rid));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // OPERATION — text edit from a client
    // ──────────────────────────────────────────────────────────────────────────
    @MessageMapping("/room/{roomId}/op")
    public void handleOperation(
            @DestinationVariable String roomId,
            @Payload TextOperation incoming,
            Principal principal
    ) {
        UUID rid = UUID.fromString(roomId);
        String userId = principal.getName();

        TextOperation transformed = transformAgainstHistory(roomId, incoming);
        if (transformed == null) return; // no-op after transform

        long newVersion = docService.applyOperation(rid, transformed);
        opHistory.computeIfAbsent(roomId, k -> new java.util.ArrayDeque<>()).addLast(transformed);

        // Persist to event log
        eventRepo.save(RoomEvent.builder()
                .roomId(rid)
                .userId(UUID.fromString(userId))
                .eventType("CODE_CHANGE")
                .payload(Map.of(
                        "type", transformed.getType().name(),
                        "position", transformed.getPosition(),
                        "text", transformed.getText() != null ? transformed.getText() : "",
                        "length", transformed.getLength(),
                        "version", newVersion
                ))
                .build());

        // Broadcast transformed op to all room subscribers
        WsMessage msg = WsMessage.of(WsMessage.Type.OPERATION, userId, transformed, newVersion);
        broker.convertAndSend("/topic/room/" + roomId, msg);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CURSOR — cursor position/selection update
    // ──────────────────────────────────────────────────────────────────────────
    @MessageMapping("/room/{roomId}/cursor")
    public void handleCursor(
            @DestinationVariable String roomId,
            @Payload Map<String, Object> cursorData,
            Principal principal
    ) {
        WsMessage msg = WsMessage.of(
                WsMessage.Type.CURSOR,
                principal.getName(),
                cursorData,
                docService.getVersion(UUID.fromString(roomId))
        );
        broker.convertAndSend("/topic/room/" + roomId, msg);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CHAT — in-room chat message
    // ──────────────────────────────────────────────────────────────────────────
    @MessageMapping("/room/{roomId}/chat")
    public void handleChat(
            @DestinationVariable String roomId,
            @Payload Map<String, Object> chatData,
            Principal principal
    ) {
        UUID rid = UUID.fromString(roomId);
        String userId = principal.getName();

        eventRepo.save(RoomEvent.builder()
                .roomId(rid)
                .userId(UUID.fromString(userId))
                .eventType("CHAT_MESSAGE")
                .payload(chatData)
                .build());

        WsMessage msg = WsMessage.of(WsMessage.Type.CHAT, userId,
                Map.of("text", chatData.getOrDefault("text", ""),
                        "sentAt", Instant.now().toString()),
                0L);
        broker.convertAndSend("/topic/room/" + roomId, msg);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // LANGUAGE CHANGE
    // ──────────────────────────────────────────────────────────────────────────
    @MessageMapping("/room/{roomId}/language")
    public void handleLanguageChange(
            @DestinationVariable String roomId,
            @Payload Map<String, String> payload,
            Principal principal
    ) {
        String language = payload.getOrDefault("language", "javascript");
        eventRepo.save(RoomEvent.builder()
                .roomId(UUID.fromString(roomId))
                .userId(UUID.fromString(principal.getName()))
                .eventType("LANGUAGE_CHANGE")
                .payload(Map.of("language", language))
                .build());

        broker.convertAndSend("/topic/room/" + roomId,
                WsMessage.of(WsMessage.Type.LANGUAGE_CHANGE, principal.getName(),
                        Map.of("language", language), 0L));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // LEAVE — explicit graceful leave
    // ──────────────────────────────────────────────────────────────────────────
    @MessageMapping("/room/{roomId}/leave")
    public void handleLeave(
            @DestinationVariable String roomId,
            Principal principal
    ) {
        UUID rid = UUID.fromString(roomId);
        UUID uid = UUID.fromString(principal.getName());
        roomService.leaveRoom(rid, uid);

        broker.convertAndSend("/topic/room/" + roomId,
                WsMessage.of(WsMessage.Type.PARTICIPANT_LEAVE, principal.getName(),
                        Map.of("userId", principal.getName()), 0L));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // OT: transform incoming op against all ops committed since clientVersion
    // ──────────────────────────────────────────────────────────────────────────
    private TextOperation transformAgainstHistory(String roomId, TextOperation incoming) {
        java.util.Deque<TextOperation> history = opHistory.get(roomId);
        if (history == null || history.isEmpty()) return incoming;

        TextOperation result = incoming;
        for (TextOperation committed : history) {
            if (committed == null) continue;
            if (result.getClientVersion() <= committed.getClientVersion()) continue;
            result = otEngine.transform(result, committed);
            if (result == null) return null;
        }
        return result;
    }
}
