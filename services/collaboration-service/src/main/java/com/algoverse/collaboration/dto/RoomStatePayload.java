package com.algoverse.collaboration.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class RoomStatePayload {
    private String content;         // full document text
    private long version;           // current server version
    private String language;
    private List<ParticipantDto> participants;
    private Map<String, Object> settings;
}
