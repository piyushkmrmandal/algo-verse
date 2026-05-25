package com.algoverse.collaboration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic WebSocket envelope. The {@code payload} field carries type-specific data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WsMessage {

    public enum Type {
        // Server → client
        ROOM_STATE,       // full room snapshot on join
        OPERATION,        // transformed text operation broadcast
        CURSOR,           // cursor position update
        CHAT,             // chat message
        PARTICIPANT_JOIN,
        PARTICIPANT_LEAVE,
        LANGUAGE_CHANGE,
        ERROR,

        // Client → server (inbound from /app/room/{id}/*)
        OP,               // text operation
        CURSOR_UPDATE,
        CHAT_MESSAGE,
        CHANGE_LANGUAGE,
        LEAVE
    }

    private Type type;
    private String userId;
    private Object payload;
    private long serverVersion; // document version after applying this operation
    private long timestamp;

    public static WsMessage of(Type type, String userId, Object payload, long serverVersion) {
        return WsMessage.builder()
                .type(type)
                .userId(userId)
                .payload(payload)
                .serverVersion(serverVersion)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static WsMessage error(String message) {
        return WsMessage.builder()
                .type(Type.ERROR)
                .payload(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
