package com.example.chat.consumer.service;

import com.example.chat.common.dto.ChatMessageBatch;
import com.example.chat.common.dto.ChatMessageDto;
import com.example.chat.common.enums.MessageType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatMessageConsumerServiceTest {
    private final ChatMessageBatchStore store = mock(ChatMessageBatchStore.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
    private final ChatMessageConsumerService service = new ChatMessageConsumerService(store, kafka);

    @Test
    void publishesBoundedOrderedBatchesOnlyAfterPersistenceReturns() {
        List<ConsumerRecord<String, ChatMessageDto>> records = records(250);
        doAnswer(invocation -> {
            List<ChatMessageDto> messages = invocation.getArgument(0);
            for (int i = 0; i < messages.size(); i++) messages.get(i).setMessageId((long) i + 1);
            return null;
        }).when(store).persist(anyList());
        when(kafka.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

        service.consumeMessages(records);

        var order = inOrder(store, kafka);
        order.verify(store).persist(anyList());
        ArgumentCaptor<Object> batches = ArgumentCaptor.forClass(Object.class);
        order.verify(kafka, times(3)).send(eq(ChatMessageBatch.TOPIC), eq("42"), batches.capture());
        assertThat(batches.getAllValues().stream().map(value -> ((ChatMessageBatch) value).messages().size()))
                .containsExactly(100, 100, 50);
        assertThat(batches.getAllValues().stream().flatMap(value -> ((ChatMessageBatch) value).messages().stream())
                .map(ChatMessageDto::getMessageId)).containsExactlyElementsOf(
                        IntStream.rangeClosed(1, 250).mapToObj(i -> (long) i).toList());
    }

    @Test
    void persistenceFailurePreventsFanout() {
        doThrow(new IllegalStateException("DB unavailable")).when(store).persist(anyList());
        assertThatThrownBy(() -> service.consumeMessages(records(1))).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(kafka);
    }

    @Test
    void publicationFailureEscapesForRetryEvenWhenOtherBatchesWereEnqueued() {
        when(kafka.send(anyString(), anyString(), any())).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")));
        assertThatThrownBy(() -> service.consumeMessages(records(250)))
                .hasRootCauseMessage("Kafka unavailable");
        verify(kafka, times(3)).send(anyString(), anyString(), any());
    }

    @Test
    void legacyRecordGetsSameEventIdAcrossRetries() {
        when(kafka.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        var first = records(1);
        var retry = records(1);
        service.consumeMessages(first);
        service.consumeMessages(retry);
        assertThat(first.getFirst().value().getEventId()).isEqualTo(retry.getFirst().value().getEventId());
    }

    private List<ConsumerRecord<String, ChatMessageDto>> records(int count) {
        return IntStream.range(0, count).mapToObj(i -> new ConsumerRecord<>("chat-messages", 0, i, "42",
                ChatMessageDto.builder().roomId(42L).senderId(7L).messageId((long) i + 1)
                        .messageType(MessageType.TALK).content("message-" + i).build())).toList();
    }
}
