package com.mahjong.service;

import com.mahjong.model.entity.Room;
import com.mahjong.model.entity.RoomPlayer;
import com.mahjong.model.entity.User;
import com.mahjong.model.enums.RoomStatus;
import com.mahjong.repository.RoomPlayerRepository;
import com.mahjong.repository.RoomRepository;
import com.mahjong.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RoomService
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {
    
    @Mock
    private RoomRepository roomRepository;
    
    @Mock
    private RoomPlayerRepository roomPlayerRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private RoomService roomService;
    
    private User testUser1;
    private User testUser2;
    private User testUser3;
    private Room testRoom;
    private RoomPlayer testRoomPlayer;
    
    @BeforeEach
    void setUp() {
        testUser1 = new User();
        testUser1.setId(1L);
        testUser1.setNickname("Player1");
        
        testUser2 = new User();
        testUser2.setId(2L);
        testUser2.setNickname("Player2");
        
        testUser3 = new User();
        testUser3.setId(3L);
        testUser3.setNickname("Player3");
        
        testRoom = new Room("123456", 1L, 1L);
        testRoom.setStatus(RoomStatus.WAITING);
        
        testRoomPlayer = new RoomPlayer("123456", 1L, 0);
    }
    
    @Test
    void createRoom_Success() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser1));
        when(roomRepository.countActiveRoomsByOwner(1L)).thenReturn(0L);
        when(roomRepository.existsById(anyString())).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenReturn(testRoom);
        when(roomPlayerRepository.save(any(RoomPlayer.class))).thenReturn(testRoomPlayer);
        
        // Act
        Room result = roomService.createRoom(1L, 1L);
        
        // Assert
        assertNotNull(result);
        assertEquals("123456", result.getId());
        assertEquals(1L, result.getOwnerId());
        assertEquals(RoomStatus.WAITING, result.getStatus());
        
        verify(roomRepository).save(any(Room.class));
        verify(roomPlayerRepository).save(any(RoomPlayer.class));
    }
    
    @Test
    void createRoom_UserNotFound() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> roomService.createRoom(1L, 1L)
        );
        
        assertEquals("Owner not found: 1", exception.getMessage());
        verify(roomRepository, never()).save(any());
    }
    
    @Test
    void createRoom_TooManyActiveRooms() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser1));
        when(roomRepository.countActiveRoomsByOwner(1L)).thenReturn(3L);
        
        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> roomService.createRoom(1L, 1L)
        );
        
        assertTrue(exception.getMessage().contains("maximum number of active rooms"));
        verify(roomRepository, never()).save(any());
    }
    
    @Test
    void joinRoom_Success() {
        // Arrange
        when(userRepository.findById(2L)).thenReturn(Optional.of(testUser2));
        when(roomRepository.findByIdWithPlayers("123456")).thenReturn(Optional.of(testRoom));
        when(roomRepository.findRoomsByUserId(2L)).thenReturn(Collections.emptyList());
        when(roomPlayerRepository.findNextAvailableSeatIndex("123456")).thenReturn(1);
        when(roomPlayerRepository.save(any(RoomPlayer.class))).thenReturn(new RoomPlayer("123456", 2L, 1));
        
        // Act
        RoomPlayer result = roomService.joinRoom("123456", 2L);
        
        // Assert
        assertNotNull(result);
        assertEquals("123456", result.getRoomId());
        assertEquals(2L, result.getUserId());
        assertEquals(1, result.getSeatIndex());
        
        verify(roomPlayerRepository).save(any(RoomPlayer.class));
        verify(roomRepository).save(testRoom);
    }
    
    @Test
    void joinRoom_RoomNotFound() {
        // Arrange
        when(userRepository.findById(2L)).thenReturn(Optional.of(testUser2));
        when(roomRepository.findByIdWithPlayers("123456")).thenReturn(Optional.empty());
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> roomService.joinRoom("123456", 2L)
        );
        
        assertEquals("Room not found: 123456", exception.getMessage());
    }
    
    @Test
    void joinRoom_RoomNotWaiting() {
        // Arrange
        testRoom.setStatus(RoomStatus.PLAYING);
        when(userRepository.findById(2L)).thenReturn(Optional.of(testUser2));
        when(roomRepository.findByIdWithPlayers("123456")).thenReturn(Optional.of(testRoom));
        
        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> roomService.joinRoom("123456", 2L)
        );
        
        assertTrue(exception.getMessage().contains("not accepting new players"));
    }
    
    @Test
    void joinRoom_RoomFull() {
        // Arrange
        testRoom.getPlayers().add(new RoomPlayer("123456", 1L, 0));
        testRoom.getPlayers().add(new RoomPlayer("123456", 2L, 1));
        testRoom.getPlayers().add(new RoomPlayer("123456", 3L, 2));
        
        when(userRepository.findById(4L)).thenReturn(Optional.of(new User()));
        when(roomRepository.findByIdWithPlayers("123456")).thenReturn(Optional.of(testRoom));
        
        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> roomService.joinRoom("123456", 4L)
        );
        
        assertEquals("Room is full", exception.getMessage());
    }
    
    @Test
    void joinRoom_UserAlreadyInRoom() {
        // Arrange
        testRoom.getPlayers().add(new RoomPlayer("123456", 2L, 1));
        
        when(userRepository.findById(2L)).thenReturn(Optional.of(testUser2));
        when(roomRepository.findByIdWithPlayers("123456")).thenReturn(Optional.of(testRoom));
        
        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> roomService.joinRoom("123456", 2L)
        );
        
        assertEquals("User already in room", exception.getMessage());
    }
    
    @Test
    void joinRoom_UserInAnotherRoom() {
        // Arrange
        Room anotherRoom = new Room("654321", 3L, 1L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(testUser2));
        when(roomRepository.findByIdWithPlayers("123456")).thenReturn(Optional.of(testRoom));
        when(roomRepository.findRoomsByUserId(2L)).thenReturn(Arrays.asList(anotherRoom));
        
        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> roomService.joinRoom("123456", 2L)
        );
        
        assertEquals("User is already in another active room", exception.getMessage());
    }
    
    @Test
    void leaveRoom_Success() {
        // Arrange
        RoomPlayer roomPlayer = new RoomPlayer("123456", 2L, 1);
        when(roomRepository.findByIdWithPlayers("123456")).thenReturn(Optional.of(testRoom));
        when(roomPlayerRepository.findByRoomIdAndUserId("123456", 2L)).thenReturn(Optional.of(roomPlayer));
        when(roomPlayerRepository.countByRoomId("123456")).thenReturn(2L);
        
        // Act
        roomService.leaveRoom("123456", 2L);
        
        // Assert
        verify(roomPlayerRepository).delete(roomPlayer);
        verify(roomRepository).save(testRoom);
    }
    
    @Test
    void leaveRoom_LastPlayerDissolves() {
        // Arrange
        RoomPlayer roomPlayer = new RoomPlayer("123456", 1L, 0);
        when(roomRepository.findByIdWithPlayers("123456")).thenReturn(Optional.of(testRoom));
        when(roomPlayerRepository.findByRoomIdAndUserId("123456", 1L)).thenReturn(Optional.of(roomPlayer));
        when(roomPlayerRepository.countByRoomId("123456")).thenReturn(0L);
        
        // Act
        roomService.leaveRoom("123456", 1L);
        
        // Assert
        verify(roomPlayerRepository).delete(roomPlayer);
        verify(roomPlayerRepository).deleteByRoomId("123456");
        verify(roomRepository, times(2)).save(testRoom); // Once for dissolution, once for activity update
    }
    
    @Test
    void leaveRoom_OwnerTransfersOwnership() {
        // Arrange
        RoomPlayer ownerPlayer = new RoomPlayer("123456", 1L, 0);
        RoomPlayer otherPlayer = new RoomPlayer("123456", 2L, 1);
        
        when(roomRepository.findByIdWithPlayers("123456")).thenReturn(Optional.of(testRoom));
        when(roomPlayerRepository.findByRoomIdAndUserId("123456", 1L)).thenReturn(Optional.of(ownerPlayer));
        when(roomPlayerRepository.countByRoomId("123456")).thenReturn(1L);
        when(roomPlayerRepository.findByRoomIdOrderBySeatIndex("123456")).thenReturn(Arrays.asList(otherPlayer));
        
        // Act
        roomService.leaveRoom("123456", 1L);
        
        // Assert
        verify(roomPlayerRepository).delete(ownerPlayer);
        assertEquals(2L, testRoom.getOwnerId()); // Ownership transferred
    }
    
    @Test
    void dissolveRoom_Success() {
        // Arrange
        when(roomRepository.findById("123456")).thenReturn(Optional.of(testRoom));
        
        // Act
        roomService.dissolveRoom("123456", 1L);
        
        // Assert
        verify(roomPlayerRepository).deleteByRoomId("123456");
        assertEquals(RoomStatus.DISSOLVED, testRoom.getStatus());
        verify(roomRepository).save(testRoom);
    }
    
    @Test
    void dissolveRoom_NotOwner() {
        // Arrange
        when(roomRepository.findById("123456")).thenReturn(Optional.of(testRoom));
        
        // Act & Assert
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> roomService.dissolveRoom("123456", 2L)
        );
        
        assertEquals("Only room owner can dissolve room", exception.getMessage());
    }
    
    @Test
    void dissolveRoom_SystemCleanup() {
        // Arrange
        when(roomRepository.findById("123456")).thenReturn(Optional.of(testRoom));
        
        // Act
        roomService.dissolveRoom("123456", null); // System cleanup
        
        // Assert
        verify(roomPlayerRepository).deleteByRoomId("123456");
        assertEquals(RoomStatus.DISSOLVED, testRoom.getStatus());
        verify(roomRepository).save(testRoom);
    }
    
    @Test
    void getRoomById_Success() {
        // Arrange
        when(roomRepository.findByIdWithPlayers("123456")).thenReturn(Optional.of(testRoom));
        
        // Act
        Optional<Room> result = roomService.getRoomById("123456");
        
        // Assert
        assertTrue(result.isPresent());
        assertEquals(testRoom, result.get());
    }
    
    @Test
    void getRoomsByUserId_Success() {
        // Arrange
        List<Room> rooms = Arrays.asList(testRoom);
        when(roomRepository.findRoomsByUserId(1L)).thenReturn(rooms);
        
        // Act
        List<Room> result = roomService.getRoomsByUserId(1L);
        
        // Assert
        assertEquals(1, result.size());
        assertEquals(testRoom, result.get(0));
    }
    
    @Test
    void getActiveRooms_Success() {
        // Arrange
        List<Room> rooms = Arrays.asList(testRoom);
        when(roomRepository.findActiveRooms()).thenReturn(rooms);
        
        // Act
        List<Room> result = roomService.getActiveRooms();
        
        // Assert
        assertEquals(1, result.size());
        assertEquals(testRoom, result.get(0));
    }
    
    @Test
    void updatePlayerReadyStatus_Success() {
        // Arrange
        when(roomRepository.findById("123456")).thenReturn(Optional.of(testRoom));
        testRoom.getPlayers().add(new RoomPlayer("123456", 1L, 0));
        
        // Act
        roomService.updatePlayerReadyStatus("123456", 1L, true);
        
        // Assert
        verify(roomPlayerRepository).updatePlayerReadyStatus("123456", 1L, true);
        verify(roomRepository).save(testRoom);
    }
    
    @Test
    void updatePlayerReadyStatus_UserNotInRoom() {
        // Arrange
        when(roomRepository.findById("123456")).thenReturn(Optional.of(testRoom));
        
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> roomService.updatePlayerReadyStatus("123456", 2L, true)
        );
        
        assertEquals("User not in room", exception.getMessage());
    }
    
    @Test
    void updatePlayerOnlineStatus_Success() {
        // Arrange
        when(roomRepository.findById("123456")).thenReturn(Optional.of(testRoom));
        
        // Act
        roomService.updatePlayerOnlineStatus("123456", 1L, true);
        
        // Assert
        verify(roomPlayerRepository).updatePlayerOnlineStatus("123456", 1L, true);
        verify(roomRepository).save(testRoom);
    }
    
    @Test
    void cleanupInactiveRooms_Success() {
        // Arrange
        Room inactiveRoom = new Room("654321", 2L, 1L);
        inactiveRoom.setLastActivityAt(LocalDateTime.now().minusHours(1));
        
        when(roomRepository.findInactiveRooms(any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(inactiveRoom));
        when(roomRepository.findById("654321")).thenReturn(Optional.of(inactiveRoom));
        
        // Act
        roomService.cleanupInactiveRooms();
        
        // Assert
        verify(roomPlayerRepository).deleteByRoomId("654321");
        verify(roomRepository).save(inactiveRoom);
        assertEquals(RoomStatus.DISSOLVED, inactiveRoom.getStatus());
    }
}