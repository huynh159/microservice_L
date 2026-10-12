package com.example.chatservice.controller;

import com.example.chatservice.client.UserServiceClient;
import com.example.chatservice.dto.request.SendMessageRequest;
import com.example.chatservice.dto.request.UpdateRoomStatusRequest;
import com.example.chatservice.dto.response.ChatMessageResponse;
import com.example.chatservice.dto.response.ChatRoomResponse;
import com.example.chatservice.dto.response.UserSummaryResponse;
import com.example.chatservice.entity.SenderRole;
import com.example.chatservice.security.UserPrincipal;
import com.example.chatservice.service.ChatMessageService;
import com.example.chatservice.service.ChatRoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ==============================================================================
 * REST CONTROLLER HỖ TRỢ CHAT & GIAO TIẾP VỚI USER-SERVICE
 * ------------------------------------------------------------------------------
 * Cung cấp các API REST HTTP cho:
 * 1. Khởi tạo/Lấy danh sách phòng chat
 * 2. Tải lịch sử tin nhắn khi vừa mở ứng dụng
 * 3. Gọi sang user-service để hiển thị hồ sơ khách hàng cho Admin (Token Relay)
 * ==============================================================================
 */
@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat Management", description = "Các API quản lý phòng chat, lịch sử tin nhắn và thông tin khách hàng")
public class ChatRestController {

    private final ChatRoomService chatRoomService;
    private final ChatMessageService chatMessageService;
    private final UserServiceClient userServiceClient;

    public ChatRestController(
            ChatRoomService chatRoomService,
            ChatMessageService chatMessageService,
            UserServiceClient userServiceClient
    ) {
        this.chatRoomService = chatRoomService;
        this.chatMessageService = chatMessageService;
        this.userServiceClient = userServiceClient;
    }

    /**
     * KHÁCH HÀNG: Mở hoặc lấy phòng chat đang hoạt động
     */
    @PostMapping("/rooms")
    @Operation(summary = "Mở hoặc lấy phòng chat của bản thân", description = "Tự động tìm phòng chat đang mở hoặc tạo phòng mới")
    public ResponseEntity<ChatRoomResponse> getOrCreateRoom(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ChatRoomResponse room = chatRoomService.getOrCreateRoomForUser(principal.getId(), principal.getEmail());
        return ResponseEntity.ok(room);
    }

    /**
     * KHÁCH HÀNG: Lấy danh sách các phòng chat của bản thân
     */
    @GetMapping("/rooms/my")
    @Operation(summary = "Xem lịch sử các phòng chat của tôi")
    public ResponseEntity<List<ChatRoomResponse>> getMyRooms(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(chatRoomService.getMyRooms(principal.getId()));
    }

    /**
     * QUẢN TRỊ VIÊN: Lấy danh sách tất cả các phòng chat trong hệ thống
     */
    @GetMapping("/rooms")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: Lấy danh sách toàn bộ phòng chat đang chờ hoặc đã xử lý")
    public ResponseEntity<List<ChatRoomResponse>> getAllRoomsForAdmin() {
        return ResponseEntity.ok(chatRoomService.getAllRoomsForAdmin());
    }

    /**
     * XEM CHI TIẾT PHÒNG CHAT THEO ID
     */
    @GetMapping("/rooms/{roomId}")
    @Operation(summary = "Xem chi tiết một phòng chat")
    public ResponseEntity<ChatRoomResponse> getRoomDetails(@PathVariable UUID roomId) {
        return ResponseEntity.ok(chatRoomService.getRoomById(roomId));
    }

    /**
     * GIAO TIẾP MICROSERVICES (INTER-SERVICE CALL VỚI TOKEN RELAY):
     * Admin xem phòng chat -> Gọi sang user-service lấy thêm họ tên, avatar, SĐT của khách
     */
    @GetMapping("/rooms/{roomId}/user-info")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: Lấy thông tin chi tiết của khách hàng từ user-service (Token Relay)")
    public ResponseEntity<?> getCustomerProfileFromUserService(
            @PathVariable UUID roomId,
            @RequestHeader("Authorization") String bearerToken
    ) {
        ChatRoomResponse room = chatRoomService.getRoomById(roomId);

        // Gọi sang user-service qua Spring RestClient
        Optional<UserSummaryResponse> userProfile = userServiceClient.getUserById(room.getUserId(), bearerToken);

        if (userProfile.isPresent()) {
            return ResponseEntity.ok(userProfile.get());
        }

        // Fallback: nếu user-service tạm thời bận thì trả về thông tin tối thiểu có sẵn trong phòng
        return ResponseEntity.ok(new UserSummaryResponse(room.getUserId(), room.getUserEmail(), "Khách hàng", null, null, "ACTIVE"));
    }

    /**
     * ADMIN: TIẾP NHẬN HỖ TRỢ PHÒNG CHAT
     */
    @PostMapping("/rooms/{roomId}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: Bấm tiếp nhận hỗ trợ một phòng chat")
    public ResponseEntity<ChatRoomResponse> assignAdminToRoom(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ChatRoomResponse room = chatRoomService.assignAdminToRoom(roomId, principal.getId());
        return ResponseEntity.ok(room);
    }

    /**
     * CẬP NHẬT TRẠNG THÁI PHÒNG (RESOLVED / CLOSED)
     */
    @PatchMapping("/rooms/{roomId}/status")
    @Operation(summary = "Cập nhật trạng thái phòng chat (Đóng phòng hoặc hoàn thành)")
    public ResponseEntity<ChatRoomResponse> updateRoomStatus(
            @PathVariable UUID roomId,
            @Valid @RequestBody UpdateRoomStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ChatRoomResponse response = chatRoomService.updateRoomStatus(
                roomId, request.getStatus(), principal.getId(), principal.isAdmin()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * LẤY LỊCH SỬ TIN NHẮN TRONG PHÒNG CHAT (KHI MỞ KHUNG CHAT)
     */
    @GetMapping("/rooms/{roomId}/messages")
    @Operation(summary = "Lấy lịch sử tin nhắn trong phòng chat")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(
            @PathVariable UUID roomId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<ChatMessageResponse> messages = chatMessageService.getMessagesByRoomId(
                roomId, principal.getId(), principal.isAdmin()
        );
        return ResponseEntity.ok(messages);
    }

    /**
     * GỬI TIN NHẮN QUA REST API (Hỗ trợ thêm song song với WebSocket STOMP)
     */
    @PostMapping("/rooms/{roomId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Gửi tin nhắn qua REST API (Dự phòng cho client không dùng websocket)")
    public ResponseEntity<ChatMessageResponse> sendMessageViaRest(
            @PathVariable UUID roomId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        request.setRoomId(roomId);
        SenderRole role = principal.isAdmin() ? SenderRole.ROLE_ADMIN : SenderRole.ROLE_USER;
        ChatMessageResponse response = chatMessageService.sendMessage(principal.getId(), role, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
