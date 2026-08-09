package com.example.chat.api.service;

import com.example.chat.common.enums.Role;
import com.example.chat.common.exception.ChatException;
import com.example.chat.common.exception.ErrorCode;
import com.example.chat.core.entity.ChatRoomEntity;
import com.example.chat.core.entity.ChatRoomMemberEntity;
import com.example.chat.core.entity.UserEntity;
import com.example.chat.core.repository.ChatRoomMemberRepository;
import com.example.chat.core.repository.ChatRoomRepository;
import com.example.chat.core.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class UserAccountService {

    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(
            UserRepository userRepository,
            ChatRoomRepository chatRoomRepository,
            ChatRoomMemberRepository chatRoomMemberRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.chatRoomRepository = chatRoomRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public boolean withdraw(Long userId, String password) {
        UserEntity user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ChatException(ErrorCode.USER_NOT_FOUND));
        if (user.isWithdrawn()) {
            return false;
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ChatException(ErrorCode.INVALID_PASSWORD);
        }

        List<ChatRoomMemberEntity> memberships = chatRoomMemberRepository.findByUserIdOrderByRoomIdAsc(userId);
        for (ChatRoomMemberEntity membership : memberships) {
            withdrawFromRoom(userId, membership.getRoomId());
        }

        String invalidatedPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
        user.withdraw(invalidatedPasswordHash, ZonedDateTime.now());
        return true;
    }

    private void withdrawFromRoom(Long userId, Long roomId) {
        ChatRoomEntity room = chatRoomRepository.findByIdForUpdate(roomId).orElse(null);
        if (room == null) {
            chatRoomMemberRepository.deleteByRoomIdAndUserId(roomId, userId);
            return;
        }

        List<ChatRoomMemberEntity> members = chatRoomMemberRepository.findByRoomIdForUpdate(roomId);
        ChatRoomMemberEntity withdrawingMember = members.stream()
                .filter(member -> member.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
        if (withdrawingMember == null) {
            return;
        }

        List<ChatRoomMemberEntity> remainingMembers = members.stream()
                .filter(member -> !member.getUserId().equals(userId))
                .toList();
        chatRoomMemberRepository.delete(withdrawingMember);

        if (remainingMembers.isEmpty()) {
            // Empty rooms are removed, while chat_message rows remain as retained history.
            chatRoomRepository.delete(room);
            return;
        }

        if (withdrawingMember.getRole() == Role.OWNER || room.getCreatedBy().equals(userId)) {
            ChatRoomMemberEntity nextOwner = remainingMembers.getFirst();
            remainingMembers.forEach(member -> member.setRole(Role.MEMBER));
            nextOwner.setRole(Role.OWNER);
            room.setCreatedBy(nextOwner.getUserId());
        }
        room.setCurrentCount(remainingMembers.size());
    }
}
