package com.example.mahjong.controller;

import com.example.mahjong.dto.CreateRoomRequest;
import com.example.mahjong.dto.CreateRoomResponse;
import com.example.mahjong.dto.JoinRoomRequest;
import com.example.mahjong.entity.Room;
import com.example.mahjong.entity.User;
import com.example.mahjong.repository.UserRepository;
import com.example.mahjong.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/room")
public class RoomController {

    @Autowired
    private RoomService roomService;

    @Autowired
    private UserRepository userRepository; // For simulating a logged-in user

    @PostMapping("/create")
    public ResponseEntity<CreateRoomResponse> createRoom(@RequestBody CreateRoomRequest createRoomRequest) {
        // In a real app, the user would be retrieved from the Spring Security context.
        // For now, we'll simulate getting the first user from the DB as the owner.
        // This is a temporary measure and assumes a user with ID 1 exists.
        User owner = userRepository.findById(1L).orElseThrow(() -> new RuntimeException("Simulated owner user not found"));

        Room room = roomService.createRoom(createRoomRequest, owner);
        return ResponseEntity.ok(new CreateRoomResponse(room.getId()));
    }

    @PostMapping("/join")
    public ResponseEntity<?> joinRoom(@RequestBody JoinRoomRequest joinRoomRequest) {
        // Simulate getting the user who wants to join
        User joiner = userRepository.findById(2L).orElseThrow(() -> new RuntimeException("Simulated joiner user not found"));

        roomService.joinRoom(joinRoomRequest.getRoomId(), joiner);
        return ResponseEntity.ok().build();
    }
}
