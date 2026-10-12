package com.example.chatservice.service;

import com.example.common.exception.ForbiddenException;
import com.example.chatservice.dto.response.ChatRoomResponse;
import com.example.chatservice.entity.ChatRoom;
import com.example.chatservice.entity.RoomStatus;
import com.example.chatservice.repository.ChatMessageRepository;
import com.example.chatservice.repository.ChatRoomRepository;
import com.example.chatservice.service.impl.ChatRoomServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    private ChatRoomService chatRoomService;

    private UUID userId;
    private String email;

    @BeforeEach
    void setUp() {
        chatRoomService = new ChatRoomServiceImpl(chatRoomRepository, chatMessageRepository, messagingTemplate);
        userId = UUID.randomUUID();
        email = "test.user@example.com";
    }

    @Test
    @DisplayName("Khách hàng đã có phòng OPEN -> Trả về phòng hiện tại, không tạo mới")
    void testGetOrCreateRoomReturnsExistingOpenRoom() {
        ChatRoom existingRoom = new ChatRoom(userId, email);
        existingRoom.setId(UUID.randomUUID());

        when(chatRoomRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, RoomStatus.OPEN))
                .thenReturn(Optional.of(existingRoom));

        ChatRoomResponse response = chatRoomService.getOrCreateRoomForUser(userId, email);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(existingRoom.getId());
        verify(chatRoomRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/admin/inbox"), any(Object.class));
    }

    @Test
    @DisplayName("Khách hàng chưa có phòng OPEN -> Tạo phòng mới và thông báo đến /topic/admin/inbox")
    void testGetOrCreateRoomCreatesNewRoomAndNotifiesAdmin() {
        when(chatRoomRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, RoomStatus.OPEN))
                .thenReturn(Optional.empty());

        ChatRoom savedRoom = new ChatRoom(userId, email);
        savedRoom.setId(UUID.randomUUID());
        when(chatRoomRepository.save(any(ChatRoom.class))).thenReturn(savedRoom);

        ChatRoomResponse response = chatRoomService.getOrCreateRoomForUser(userId, email);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(savedRoom.getId());
        verify(chatRoomRepository, times(1)).save(any(ChatRoom.class));
        // Kiểm tra đã phát tin nhắn thông báo phòng mới đến admin
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/admin/inbox"), any(Object.class));
    }

    @Test
    @DisplayName("Admin tiếp nhận phòng chat -> Cập nhật adminId và thông báo realtime cho phòng")
    void testAssignAdminToRoom() {
        UUID roomId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        ChatRoom room = new ChatRoom(userId, email);
        room.setId(roomId);

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(i -> i.getArgument(0));

        ChatRoomResponse response = chatRoomService.assignAdminToRoom(roomId, adminId);

        assertThat(response.getAdminId()).isEqualTo(adminId);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/rooms." + roomId), any(Object.class));
    }

    @Test
    @DisplayName("Người lạ cố tình đóng phòng chat của người khác -> Ném lỗi ForbiddenException")
    void testUpdateRoomStatusForbidden() {
        UUID roomId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        ChatRoom room = new ChatRoom(userId, email);
        room.setId(roomId);

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

        // Người lạ không phải admin và không phải chủ phòng
        assertThatThrownBy(() -> chatRoomService.updateRoomStatus(roomId, RoomStatus.CLOSED, strangerId, false))
                .isInstanceOf(ForbiddenException.class);
    }
}
