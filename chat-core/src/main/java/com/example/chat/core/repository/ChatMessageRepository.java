package com.example.chat.core.repository;

import com.example.chat.core.entity.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    Optional<ChatMessageEntity> findByEventId(String eventId);

    List<ChatMessageEntity> findByRoomIdOrderByMessageIdDesc(Long roomId, Pageable pageable);

    @Query("SELECT m FROM ChatMessageEntity m WHERE m.roomId = :roomId AND m.messageId > :lastReceivedMessageId AND m.messageId <= :throughMessageId ORDER BY m.messageId ASC")
    List<ChatMessageEntity> findMissedMessages(@Param("roomId") Long roomId,
            @Param("lastReceivedMessageId") Long lastReceivedMessageId,
            @Param("throughMessageId") Long throughMessageId, Pageable pageable);
}
