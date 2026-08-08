package com.example.chat.api.controller;

import com.example.chat.common.dto.ChatMessageDto;
import com.example.chat.common.dto.ChatRoomDto;
import com.example.chat.common.dto.CreateRoomRequest;
import com.example.chat.common.dto.JoinRoomRequest;
import com.example.chat.core.service.ChatRoomService;
import com.example.chat.core.security.ChatPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    public ChatRoomController(ChatRoomService chatRoomService) {
        this.chatRoomService = chatRoomService;
    }

    @PostMapping
    public ResponseEntity<ChatRoomDto> createRoom(
            @AuthenticationPrincipal ChatPrincipal principal,
            @Valid @RequestBody CreateRoomRequest request
    ) {
        ChatRoomDto room = chatRoomService.createRoom(principal.userId(), request);
        return ResponseEntity.ok(room);
    }

    @PostMapping("/join")
    public ResponseEntity<ChatRoomDto> joinRoom(
            @AuthenticationPrincipal ChatPrincipal principal,
            @Valid @RequestBody JoinRoomRequest request
    ) {
        ChatRoomDto room = chatRoomService.joinRoomByInviteCode(principal.userId(), request.getInviteCode());
        return ResponseEntity.ok(room);
    }

    @GetMapping("/my")
    public ResponseEntity<List<ChatRoomDto>> getMyJoinedRooms(
            @AuthenticationPrincipal ChatPrincipal principal
    ) {
        List<ChatRoomDto> rooms = chatRoomService.getMyJoinedRooms(principal.userId());
        return ResponseEntity.ok(rooms);
    }

    @GetMapping("/{roomId}/messages")
    public ResponseEntity<List<ChatMessageDto>> getMessageHistory(
            @AuthenticationPrincipal ChatPrincipal principal,
            @PathVariable("roomId") Long roomId,
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        List<ChatMessageDto> messages = chatRoomService.getMessageHistory(principal.userId(), roomId, limit);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/{roomId}/sync")
    public ResponseEntity<List<ChatMessageDto>> getMissedMessages(
            @AuthenticationPrincipal ChatPrincipal principal,
            @PathVariable("roomId") Long roomId,
            @RequestParam("lastReceivedMessageId") Long lastReceivedMessageId
    ) {
        List<ChatMessageDto> missed = chatRoomService.getMissedMessages(
                principal.userId(), roomId, lastReceivedMessageId);
        return ResponseEntity.ok(missed);
    }
}
