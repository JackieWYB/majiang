package com.example.mahjong.service;

import com.example.mahjong.dto.CreateRoomRequest;
import com.example.mahjong.entity.Room;
import com.example.mahjong.entity.RoomPlayer;
import com.example.mahjong.entity.RoomRule;
import com.example.mahjong.entity.User;
import com.example.mahjong.repository.RoomPlayerRepository;
import com.example.mahjong.repository.RoomRepository;
import com.example.mahjong.repository.RoomRuleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class RoomService {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomRuleRepository roomRuleRepository;

    @Autowired
    private RoomPlayerRepository roomPlayerRepository;

    @Autowired
    private ObjectMapper objectMapper; // Spring Boot provides this bean

    @Transactional
    public Room createRoom(CreateRoomRequest request, User owner) {
        // Step 1: Create and save the rule
        RoomRule rule = new RoomRule();
        rule.setName(request.getRuleName());
        try {
            rule.setConfig(objectMapper.writeValueAsString(request.getConfig()));
        } catch (JsonProcessingException e) {
            // In a real app, throw a custom exception here
            throw new RuntimeException("Failed to serialize room config", e);
        }
        roomRuleRepository.save(rule);

        // Step 2: Create and save the room
        Room room = new Room();
        room.setOwner(owner);
        room.setRule(rule);
        room.setStatus("WAITING");
        room.setRoundIndex(0);
        // Extract max_rounds from config if it exists
        Object maxRoundsObj = request.getConfig().get("max_rounds");
        if (maxRoundsObj instanceof Integer) {
            room.setMaxRounds((Integer) maxRoundsObj);
        } else {
            room.setMaxRounds(8); // Default value
        }
        roomRepository.save(room);

        // Step 3: Add the owner as the first player
        joinRoom(room.getId(), owner);

        return room;
    }

    @Transactional
    public RoomPlayer joinRoom(Long roomId, User user) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found")); // Replace with custom exception

        // TODO: Add logic to check if room is full, game is in progress, etc.

        // For now, just add the player
        RoomPlayer roomPlayer = new RoomPlayer();
        roomPlayer.setRoom(room);
        roomPlayer.setUser(user);
        roomPlayer.setJoinAt(LocalDateTime.now());

        // Assign a seat index (simple logic for now)
        int seatCount = roomPlayerRepository.findByRoomId(roomId).size();
        if (seatCount >= 3) {
            throw new RuntimeException("Room is full");
        }
        roomPlayer.setSeatIndex(seatCount);

        return roomPlayerRepository.save(roomPlayer);
    }
}
