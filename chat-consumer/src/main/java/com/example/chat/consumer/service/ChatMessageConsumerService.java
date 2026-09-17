package com.example.chat.consumer.service;

import com.example.chat.common.dto.ChatMessageDto;
import com.example.chat.common.dto.ChatMessageBatch;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class ChatMessageConsumerService {

    private final ChatMessageBatchStore store;
    private final KafkaTemplate<String, Object> kafka;

    public ChatMessageConsumerService(ChatMessageBatchStore store, KafkaTemplate<String, Object> kafka) {
        this.store = store;
        this.kafka = kafka;
    }

    @KafkaListener(topics = "chat-messages", groupId = "chat-db-persistence-group", batch = "true")
    public void consumeMessages(List<ConsumerRecord<String, ChatMessageDto>> records) {
        List<ChatMessageDto> messages = records.stream().map(record -> {
            ChatMessageDto message = record.value();
            if (message.getEventId() == null) {
                String identity = record.topic() + ":" + record.partition() + ":" + record.offset();
                message.setEventId(UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString());
            }
            return message;
        }).toList();
        // The separate transactional service commits before fanout publication.
        store.persist(messages);
        Map<Long, List<ChatMessageDto>> rooms = new LinkedHashMap<>();
        messages.forEach(message -> rooms.computeIfAbsent(message.getRoomId(), key -> new ArrayList<>()).add(message));
        List<CompletableFuture<?>> publications = new ArrayList<>();
        rooms.forEach((roomId, roomMessages) -> {
            long previous = store.previousMessageId(roomId, roomMessages.getFirst().getMessageId());
            for (int start = 0; start < roomMessages.size();) {
                int end = start;
                long estimatedBytes = 0;
                while (end < roomMessages.size() && end - start < 100) {
                    ChatMessageDto message = roomMessages.get(end);
                    // JSON escaping can expand one UTF-16 character to six bytes.
                    long weight = 2048L + 6L * message.getContent().length();
                    if (end > start && estimatedBytes + weight > 512 * 1024) break;
                    estimatedBytes += weight;
                    end++;
                }
                ChatMessageBatch batch = new ChatMessageBatch(roomId,
                        previous,
                        roomMessages.subList(start, end));
                publications.add(kafka.send(ChatMessageBatch.TOPIC, roomId.toString(), batch));
                previous = roomMessages.get(end - 1).getMessageId();
                start = end;
            }
        });
        // Enqueue in partition order, then wait once per poll. Partial publication
        // is replayed on failure; clients use the predecessor to repair any gap.
        CompletableFuture.allOf(publications.toArray(CompletableFuture[]::new)).join();
    }
}
