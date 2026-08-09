package com.example.chat.core.service;

import com.example.chat.common.dto.ChatMessageDto;
import com.example.chat.common.dto.ChatRoomDto;
import com.example.chat.common.dto.CreateRoomRequest;
import com.example.chat.common.enums.MessageType;
import com.example.chat.common.enums.Role;
import com.example.chat.common.enums.RoomType;
import com.example.chat.common.enums.UserStatus;
import com.example.chat.common.exception.ChatException;
import com.example.chat.common.exception.ErrorCode;
import com.example.chat.core.entity.ChatMessageEntity;
import com.example.chat.core.entity.ChatRoomEntity;
import com.example.chat.core.entity.ChatRoomMemberEntity;
import com.example.chat.core.entity.UserEntity;
import com.example.chat.core.repository.ChatMessageRepository;
import com.example.chat.core.repository.ChatRoomMemberRepository;
import com.example.chat.core.repository.ChatRoomRepository;
import com.example.chat.core.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserRepository userRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatRoomService(
            ChatRoomRepository chatRoomRepository,
            ChatRoomMemberRepository chatRoomMemberRepository,
            UserRepository userRepository,
            ChatMessageRepository chatMessageRepository
    ) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.userRepository = userRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional
    public ChatRoomDto createRoom(Long userId, CreateRoomRequest request) {
        UserEntity creator = userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ChatException(ErrorCode.USER_NOT_FOUND));

        int maxCapacity = request.getMaxCapacity() != null ? request.getMaxCapacity() : 50;

        if (request.getRoomType() == RoomType.DIRECT) {
            maxCapacity = 2;
        } else if (maxCapacity < 2 || maxCapacity > 50) {
            throw new ChatException(ErrorCode.INVALID_ROOM_CAPACITY);
        }

        String inviteCode = generateUniqueInviteCode();

        ChatRoomEntity room = ChatRoomEntity.builder()
                .title(request.getTitle())
                .roomType(request.getRoomType())
                .inviteCode(inviteCode)
                .maxCapacity(maxCapacity)
                .currentCount(1)
                .createdBy(creator.getUserId())
                .build();

        ChatRoomEntity savedRoom = chatRoomRepository.save(room);

        // Add creator as OWNER
        ChatRoomMemberEntity ownerMember = ChatRoomMemberEntity.builder()
                .roomId(savedRoom.getRoomId())
                .userId(creator.getUserId())
                .role(Role.OWNER)
                .build();

        chatRoomMemberRepository.save(ownerMember);

        // If DIRECT chat, add target user as well
        if (request.getRoomType() == RoomType.DIRECT && request.getTargetUserId() != null) {
            UserEntity targetUser = userRepository.findById(request.getTargetUserId())
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                    .orElseThrow(() -> new ChatException(ErrorCode.USER_NOT_FOUND, "Target user not found"));

            ChatRoomMemberEntity targetMember = ChatRoomMemberEntity.builder()
                    .roomId(savedRoom.getRoomId())
                    .userId(targetUser.getUserId())
                    .role(Role.MEMBER)
                    .build();

            chatRoomMemberRepository.save(targetMember);
            savedRoom.setCurrentCount(2);
            chatRoomRepository.save(savedRoom);
        }

        return convertToDto(savedRoom);
    }

    @Transactional
    public ChatRoomDto joinRoomByInviteCode(Long userId, String inviteCode) {
        UserEntity user = userRepository.findById(userId)
                .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ChatException(ErrorCode.USER_NOT_FOUND));

        ChatRoomEntity room = chatRoomRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new ChatException(ErrorCode.INVALID_INVITE_CODE));

        if (chatRoomMemberRepository.existsByRoomIdAndUserId(room.getRoomId(), user.getUserId())) {
            throw new ChatException(ErrorCode.ALREADY_JOINED);
        }

        int updatedRows = chatRoomRepository.incrementCurrentCountIfSpaceAvailable(room.getRoomId());
        if (updatedRows == 0) {
            throw new ChatException(ErrorCode.ROOM_FULL);
        }

        ChatRoomMemberEntity member = ChatRoomMemberEntity.builder()
                .roomId(room.getRoomId())
                .userId(user.getUserId())
                .role(Role.MEMBER)
                .build();

        chatRoomMemberRepository.save(member);

        room.setCurrentCount(room.getCurrentCount() + 1);
        return convertToDto(room);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomDto> getMyJoinedRooms(Long userId) {
        if (!userRepository.existsByUserIdAndStatus(userId, UserStatus.ACTIVE)) {
            throw new ChatException(ErrorCode.USER_NOT_FOUND);
        }
        return chatRoomRepository.findJoinedRoomsByUserId(userId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessageHistory(Long userId, Long roomId, int limit) {
        if (!chatRoomMemberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ChatException(ErrorCode.NOT_ROOM_MEMBER);
        }

        List<ChatMessageEntity> messages = chatMessageRepository.findByRoomIdOrderByMessageIdDesc(
                roomId, PageRequest.of(0, Math.min(limit, 100))
        );

        return messages.stream()
                .map(this::convertMessageToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMissedMessages(Long userId, Long roomId, Long lastReceivedMessageId) {
        if (!chatRoomMemberRepository.existsByRoomIdAndUserId(roomId, userId)) {
            throw new ChatException(ErrorCode.NOT_ROOM_MEMBER);
        }

        List<ChatMessageEntity> missedMessages = chatMessageRepository.findMissedMessages(roomId, lastReceivedMessageId);
        return missedMessages.stream()
                .map(this::convertMessageToDto)
                .collect(Collectors.toList());
    }

    private String generateUniqueInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private ChatRoomDto convertToDto(ChatRoomEntity entity) {
        return ChatRoomDto.builder()
                .roomId(entity.getRoomId())
                .title(entity.getTitle())
                .roomType(entity.getRoomType())
                .inviteCode(entity.getInviteCode())
                .maxCapacity(entity.getMaxCapacity())
                .currentCount(entity.getCurrentCount())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private ChatMessageDto convertMessageToDto(ChatMessageEntity entity) {
        UserEntity sender = userRepository.findById(entity.getSenderId()).orElse(null);
        boolean withdrawnSender = sender == null || sender.isWithdrawn();
        String senderName = withdrawnSender ? UserEntity.WITHDRAWN_NICKNAME : sender.getNickname();
        String content = withdrawnSender ? anonymizeSystemMessage(entity.getMessageType(), entity.getContent()) : entity.getContent();

        return ChatMessageDto.builder()
                .messageId(entity.getMessageId())
                .roomId(entity.getRoomId())
                .senderId(entity.getSenderId())
                .senderName(senderName)
                .messageType(entity.getMessageType())
                .content(content)
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private String anonymizeSystemMessage(MessageType messageType, String content) {
        if (messageType == MessageType.ENTER) {
            return UserEntity.WITHDRAWN_NICKNAME + "님이 입장하셨습니다.";
        }
        if (messageType == MessageType.LEAVE) {
            return UserEntity.WITHDRAWN_NICKNAME + "님이 퇴장하셨습니다.";
        }
        return content;
    }
}
