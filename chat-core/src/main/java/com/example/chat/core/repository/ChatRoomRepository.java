package com.example.chat.core.repository;

import com.example.chat.core.entity.ChatRoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoomEntity, Long> {
    Optional<ChatRoomEntity> findByInviteCode(String inviteCode);

    @Query("SELECT r FROM ChatRoomEntity r JOIN ChatRoomMemberEntity m ON r.roomId = m.roomId WHERE m.userId = :userId ORDER BY r.updatedAt DESC")
    List<ChatRoomEntity> findJoinedRoomsByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE ChatRoomEntity r SET r.currentCount = r.currentCount + 1 WHERE r.roomId = :roomId AND r.currentCount < r.maxCapacity")
    int incrementCurrentCountIfSpaceAvailable(@Param("roomId") Long roomId);

    @Modifying
    @Query("UPDATE ChatRoomEntity r SET r.currentCount = r.currentCount - 1 WHERE r.roomId = :roomId AND r.currentCount > 0")
    int decrementCurrentCount(@Param("roomId") Long roomId);
}
