package com.example.chatservice.service;

import com.example.common.exception.BadRequestException;
import com.example.common.exception.ForbiddenException;
import com.example.chatservice.dto.request.SendMessageRequest;
import com.example.chatservice.dto.response.ChatMessageResponse;
import com.example.chatservice.entity.ChatMessage;
import com.example.chatservice.entity.ChatRoom;
import com.example.chatservice.entity.RoomStatus;
import com.example.chatservice.entity.SenderRole;
import com.example.chatservice.repository.ChatMessageRepository;
import com.example.chatservice.repository.ChatRoomRepository;
import com.example.chatservice.service.impl.ChatMessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    private ChatMessageService chatMessageService;

    private UUID userId;
    private UUID roomId;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        chatMessageService = new ChatMessageServiceImpl(chatRoomRepository, chatMessageRepository, messagingTemplate);
        userId = UUID.randomUUID();
        roomId = UUID.randomUUID();
        room = new ChatRoom(userId, "user@example.com");
        room.setId(roomId);
        room.setStatus(RoomStatus.OPEN);
    }

    @Test
    @DisplayName("Khách hàng gửi tin nhắn vào phòng mình -> Lưu DB và bắn realtime tới /topic/rooms.{roomId}")
    void testUserSendMessageSuccess() {
        SendMessageRequest request = new SendMessageRequest(roomId, "Xin chào hỗ trợ viên!");

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> {
            ChatMessage msg = i.getArgument(0);
            msg.setId(UUID.randomUUID());
            msg.setCreatedAt(Instant.now());
            return msg;
        });

        ChatMessageResponse response = chatMessageService.sendMessage(userId, SenderRole.ROLE_USER, request);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).isEqualTo("Xin chào hỗ trợ viên!");
        assertThat(response.getSenderRole()).isEqualTo(SenderRole.ROLE_USER);
        assertThat(response.getRoomId()).isEqualTo(roomId);

        // Xác minh lưu DB
        verify(chatMessageRepository, times(1)).save(any(ChatMessage.class));
        // Xác minh bắn tin nhắn qua WebSocket STOMP
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/rooms." + roomId), any(ChatMessageResponse.class));
    }

    @Test
    @DisplayName("Admin gửi tin nhắn vào phòng -> Thành công và phát realtime")
    void testAdminSendMessageSuccess() {
        UUID adminId = UUID.randomUUID();
        SendMessageRequest request = new SendMessageRequest(roomId, "Chào bạn, mình có thể giúp gì cho bạn?");

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(i -> {
            ChatMessage msg = i.getArgument(0);
            msg.setId(UUID.randomUUID());
            msg.setCreatedAt(Instant.now());
            return msg;
        });

        ChatMessageResponse response = chatMessageService.sendMessage(adminId, SenderRole.ROLE_ADMIN, request);

        assertThat(response.getSenderRole()).isEqualTo(SenderRole.ROLE_ADMIN);
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/rooms." + roomId), any(ChatMessageResponse.class));
    }

    @Test
    @DisplayName("Khách hàng cố tình gửi tin nhắn vào phòng người khác -> Ném ForbiddenException")
    void testUserCannotSendToOtherUserRoom() {
        UUID strangerId = UUID.randomUUID();
        SendMessageRequest request = new SendMessageRequest(roomId, "Hack phòng!");

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> chatMessageService.sendMessage(strangerId, SenderRole.ROLE_USER, request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Bạn không có quyền gửi tin nhắn");

        verify(chatMessageRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Gửi tin nhắn vào phòng đã đóng (CLOSED) -> Ném BadRequestException")
    void testCannotSendMessageToClosedRoom() {
        room.setStatus(RoomStatus.CLOSED);
        SendMessageRequest request = new SendMessageRequest(roomId, "Alo còn ai không?");

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> chatMessageService.sendMessage(userId, SenderRole.ROLE_USER, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Phòng chat đã kết thúc");
    }

    @Test
    @DisplayName("Lấy lịch sử tin nhắn trong phòng -> Trả về danh sách tin nhắn")
    void testGetMessagesSuccess() {
        ChatMessage msg1 = new ChatMessage(room, userId, SenderRole.ROLE_USER, "Hello");
        msg1.setId(UUID.randomUUID());
        ChatMessage msg2 = new ChatMessage(room, UUID.randomUUID(), SenderRole.ROLE_ADMIN, "Hi");
        msg2.setId(UUID.randomUUID());

        when(chatRoomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId)).thenReturn(List.of(msg1, msg2));

        List<ChatMessageResponse> messages = chatMessageService.getMessagesByRoomId(roomId, userId, false);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getContent()).isEqualTo("Hello");
        assertThat(messages.get(1).getContent()).isEqualTo("Hi");
    }
}
