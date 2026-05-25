package com.algoverse.collaboration.dto;

import com.algoverse.collaboration.domain.Room;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class CreateRoomRequest {

    @NotNull
    private Room.RoomType type;

    private UUID problemId;

    @Min(2) @Max(20)
    private int maxParticipants = 4;

    private Map<String, Object> settings = Map.of();
}
