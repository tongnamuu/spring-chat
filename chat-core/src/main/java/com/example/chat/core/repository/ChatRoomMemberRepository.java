package com.example.chat.core.repository;

import com.example.chat.core.entity.ChatRoomMemberEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMemberEntity, Long> {
    Optional<ChatRoomMemberEntity> findByRoomIdAndUserId(Long roomId, Long userId);
    boolean existsByRoomIdAndUserId(Long roomId, Long userId);
    List<ChatRoomMemberEntity> findByRoomId(Long roomId);
    List<ChatRoomMemberEntity> findByUserIdOrderByRoomIdAsc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM ChatRoomMemberEntity m WHERE m.roomId = :roomId ORDER BY m.joinedAt ASC, m.roomMemberId ASC")
    List<ChatRoomMemberEntity> findByRoomIdForUpdate(@Param("roomId") Long roomId);

    void deleteByRoomIdAndUserId(Long roomId, Long userId);
}
