package com.algoverse.collaboration.controller;

import com.algoverse.collaboration.dto.CreateRoomRequest;
import com.algoverse.collaboration.dto.RoomDto;
import com.algoverse.collaboration.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/collab")
@RequiredArgsConstructor
@Tag(name = "Collaboration", description = "Real-time pair coding rooms")
public class RoomController {

    private final RoomService roomService;

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a collaboration room")
    public RoomDto createRoom(
            @Valid @RequestBody CreateRoomRequest req,
            @AuthenticationPrincipal String userId
    ) {
        return roomService.createRoom(req, UUID.fromString(userId));
    }

    @PostMapping("/rooms/join/{code}")
    @Operation(summary = "Join a room by join code")
    public RoomDto joinRoom(
            @PathVariable String code,
            @AuthenticationPrincipal String userId
    ) {
        return roomService.joinRoom(code, UUID.fromString(userId));
    }

    @GetMapping("/rooms/{roomId}")
    @Operation(summary = "Get room details")
    public RoomDto getRoom(@PathVariable UUID roomId) {
        return roomService.getRoomById(roomId);
    }

    @GetMapping("/rooms/me")
    @Operation(summary = "List my rooms")
    public List<RoomDto> myRooms(@AuthenticationPrincipal String userId) {
        return roomService.getMyRooms(UUID.fromString(userId));
    }

    @DeleteMapping("/rooms/{roomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "End a room (host only)")
    public ResponseEntity<Void> endRoom(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal String userId
    ) {
        RoomDto room = roomService.getRoomById(roomId);
        if (!room.getHostId().equals(UUID.fromString(userId))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        roomService.leaveRoom(roomId, UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }
}
