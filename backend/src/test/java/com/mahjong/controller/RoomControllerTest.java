package com.mahjong.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mahjong.model.dto.CreateRoomRequest;
import com.mahjong.model.dto.JoinRoomRequest;
import com.mahjong.model.entity.Room;
import com.mahjong.model.entity.RoomPlayer;
import com.mahjong.model.enums.RoomStatus;
import com.mahjong.service.JwtTokenService;
import com.mahjong.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for RoomController
 */
@WebMvcTest(RoomController.class)
class RoomControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private RoomService roomService;
    
    @MockBean
    private JwtTokenService jwtTokenService;
    
    private Room testRoom;
    private RoomPlayer testRoomPlayer;
    private final String validToken = "Bearer valid-jwt-token";
    
    @BeforeEach
    void setUp() {
        testRoom = new Room("123456", 1L, 1L);
        testRoom.setStatus(RoomStatus.WAITING);
        
        testRoomPlayer = new RoomPlayer("123456", 1L, 0);
        testRoom.getPlayers().add(testRoomPlayer);
        
        // Mock JWT token service
        when(jwtTokenService.extractUserId("valid-jwt-token")).thenReturn(1L);
    }
    
    @Test
    void createRoom_Success() throws Exception {
        // Arrange
        CreateRoomRequest request = new CreateRoomRequest(1L);
        when(roomService.createRoom(1L, 1L)).thenReturn(testRoom);
        
        // Act & Assert
        mockMvc.perform(post("/api/rooms")
                .header("Authorization", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("123456"))
                .andExpect(jsonPath("$.data.ownerId").value(1))
                .andExpect(jsonPath("$.data.status").value("WAITING"));
        
        verify(roomService).createRoom(1L, 1L);
    }
    
    @Test
    void createRoom_InvalidRequest() throws Exception {
        // Arrange
        CreateRoomRequest request = new CreateRoomRequest(null); // Invalid - null ruleId
        
        // Act & Assert
        mockMvc.perform(post("/api/rooms")
                .header("Authorization", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        
        verify(roomService, never()).createRoom(anyLong(), anyLong());
    }
    
    @Test
    void createRoom_ServiceException() throws Exception {
        // Arrange
        CreateRoomRequest request = new CreateRoomRequest(1L);
        when(roomService.createRoom(1L, 1L))
                .thenThrow(new IllegalStateException("User already has maximum number of active rooms"));
        
        // Act & Assert
        mockMvc.perform(post("/api/rooms")
                .header("Authorization", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ROOM_CREATION_FAILED"));
    }
    
    @Test
    void createRoom_NoAuthToken() throws Exception {
        // Arrange
        CreateRoomRequest request = new CreateRoomRequest(1L);
        
        // Act & Assert
        mockMvc.perform(post("/api/rooms")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        
        verify(roomService, never()).createRoom(anyLong(), anyLong());
    }
    
    @Test
    void joinRoom_Success() throws Exception {
        // Arrange
        JoinRoomRequest request = new JoinRoomRequest("123456");
        RoomPlayer newPlayer = new RoomPlayer("123456", 2L, 1);
        
        when(jwtTokenService.extractUserId("valid-jwt-token")).thenReturn(2L);
        when(roomService.joinRoom("123456", 2L)).thenReturn(newPlayer);
        when(roomService.getRoomById("123456")).thenReturn(Optional.of(testRoom));
        
        // Act & Assert
        mockMvc.perform(post("/api/rooms/join")
                .header("Authorization", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("123456"));
        
        verify(roomService).joinRoom("123456", 2L);
    }
    
    @Test
    void joinRoom_InvalidRoomId() throws Exception {
        // Arrange
        JoinRoomRequest request = new JoinRoomRequest("12345"); // Invalid - not 6 digits
        
        // Act & Assert
        mockMvc.perform(post("/api/rooms/join")
                .header("Authorization", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        
        verify(roomService, never()).joinRoom(anyString(), anyLong());
    }
    
    @Test
    void joinRoom_RoomFull() throws Exception {
        // Arrange
        JoinRoomRequest request = new JoinRoomRequest("123456");
        when(jwtTokenService.extractUserId("valid-jwt-token")).thenReturn(2L);
        when(roomService.joinRoom("123456", 2L))
                .thenThrow(new IllegalStateException("Room is full"));
        
        // Act & Assert
        mockMvc.perform(post("/api/rooms/join")
                .header("Authorization", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ROOM_JOIN_FAILED"));
    }
    
    @Test
    void leaveRoom_Success() throws Exception {
        // Arrange
        doNothing().when(roomService).leaveRoom("123456", 1L);
        
        // Act & Assert
        mockMvc.perform(post("/api/rooms/123456/leave")
                .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        
        verify(roomService).leaveRoom("123456", 1L);
    }
    
    @Test
    void leaveRoom_UserNotInRoom() throws Exception {
        // Arrange
        doThrow(new IllegalArgumentException("User not in room"))
                .when(roomService).leaveRoom("123456", 1L);
        
        // Act & Assert
        mockMvc.perform(post("/api/rooms/123456/leave")
                .header("Authorization", validToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }
    
    @Test
    void dissolveRoom_Success() throws Exception {
        // Arrange
        doNothing().when(roomService).dissolveRoom("123456", 1L);
        
        // Act & Assert
        mockMvc.perform(post("/api/rooms/123456/dissolve")
                .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        
        verify(roomService).dissolveRoom("123456", 1L);
    }
    
    @Test
    void dissolveRoom_NotOwner() throws Exception {
        // Arrange
        doThrow(new IllegalStateException("Only room owner can dissolve room"))
                .when(roomService).dissolveRoom("123456", 1L);
        
        // Act & Assert
        mockMvc.perform(post("/api/rooms/123456/dissolve")
                .header("Authorization", validToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ROOM_DISSOLUTION_FAILED"));
    }
    
    @Test
    void getRoomDetails_Success() throws Exception {
        // Arrange
        when(roomService.getRoomById("123456")).thenReturn(Optional.of(testRoom));
        
        // Act & Assert
        mockMvc.perform(get("/api/rooms/123456")
                .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value("123456"))
                .andExpect(jsonPath("$.data.ownerId").value(1))
                .andExpect(jsonPath("$.data.status").value("WAITING"));
        
        verify(roomService).getRoomById("123456");
    }
    
    @Test
    void getRoomDetails_RoomNotFound() throws Exception {
        // Arrange
        when(roomService.getRoomById("123456")).thenReturn(Optional.empty());
        
        // Act & Assert
        mockMvc.perform(get("/api/rooms/123456")
                .header("Authorization", validToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }
    
    @Test
    void getRoomDetails_AccessDenied() throws Exception {
        // Arrange
        Room otherRoom = new Room("123456", 2L, 1L); // Different owner
        when(jwtTokenService.extractUserId("valid-jwt-token")).thenReturn(3L); // Different user
        when(roomService.getRoomById("123456")).thenReturn(Optional.of(otherRoom));
        
        // Act & Assert
        mockMvc.perform(get("/api/rooms/123456")
                .header("Authorization", validToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
    }
    
    @Test
    void getMyRooms_Success() throws Exception {
        // Arrange
        when(roomService.getRoomsByUserId(1L)).thenReturn(Arrays.asList(testRoom));
        
        // Act & Assert
        mockMvc.perform(get("/api/rooms/my-rooms")
                .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value("123456"));
        
        verify(roomService).getRoomsByUserId(1L);
    }
    
    @Test
    void getMyRooms_EmptyList() throws Exception {
        // Arrange
        when(roomService.getRoomsByUserId(1L)).thenReturn(Collections.emptyList());
        
        // Act & Assert
        mockMvc.perform(get("/api/rooms/my-rooms")
                .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
    
    @Test
    void updateReadyStatus_Success() throws Exception {
        // Arrange
        doNothing().when(roomService).updatePlayerReadyStatus("123456", 1L, true);
        
        // Act & Assert
        mockMvc.perform(post("/api/rooms/123456/ready")
                .header("Authorization", validToken)
                .param("ready", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        
        verify(roomService).updatePlayerReadyStatus("123456", 1L, true);
    }
    
    @Test
    void updateReadyStatus_UserNotInRoom() throws Exception {
        // Arrange
        doThrow(new IllegalArgumentException("User not in room"))
                .when(roomService).updatePlayerReadyStatus("123456", 1L, true);
        
        // Act & Assert
        mockMvc.perform(post("/api/rooms/123456/ready")
                .header("Authorization", validToken)
                .param("ready", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }
    
    @Test
    void getActiveRooms_Success() throws Exception {
        // Arrange
        when(roomService.getActiveRooms()).thenReturn(Arrays.asList(testRoom));
        
        // Act & Assert
        mockMvc.perform(get("/api/rooms/active")
                .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value("123456"));
        
        verify(roomService).getActiveRooms();
    }
}