package com.example.chat.core.repository;

import com.example.chat.core.entity.ChatRoomBanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomBanRepository extends JpaRepository<ChatRoomBanEntity, Long> {
    boolean existsByRoomIdAndUserId(Long roomId, Long userId);
}
