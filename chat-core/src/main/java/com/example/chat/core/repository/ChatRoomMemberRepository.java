package com.example.chat.core.repository;

import com.example.chat.core.entity.ChatRoomMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMemberEntity, Long> {
    Optional<ChatRoomMemberEntity> findByRoomIdAndUserId(Long roomId, Long userId);
    boolean existsByRoomIdAndUserId(Long roomId, Long userId);
    List<ChatRoomMemberEntity> findByRoomId(Long roomId);
    void deleteByRoomIdAndUserId(Long roomId, Long userId);
}
