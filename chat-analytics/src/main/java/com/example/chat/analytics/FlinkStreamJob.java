package com.example.chat.analytics;

import com.example.chat.common.dto.ChatMessageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;

public class FlinkStreamJob {

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        String kafkaBootstrapServers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");

        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(kafkaBootstrapServers)
                .setTopics("chat-messages")
                .setGroupId("flink-chat-analytics-group")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .build();

        DataStream<String> kafkaStream = env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source");

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        DataStream<ChatMessageDto> messageStream = kafkaStream.map((MapFunction<String, ChatMessageDto>) json -> {
            try {
                return objectMapper.readValue(json, ChatMessageDto.class);
            } catch (Exception e) {
                return null;
            }
        }).filter(msg -> msg != null && msg.getRoomId() != null);

        // Windowed Aggregation: Calculate total messages per room every 10 seconds
        messageStream
                .keyBy(ChatMessageDto::getRoomId)
                .window(TumblingProcessingTimeWindows.of(Time.seconds(10)))
                .reduce((m1, m2) -> {
                    m1.setContent("[Window Summary] Messages count increased in room " + m1.getRoomId());
                    return m1;
                })
                .print();

        env.execute("Spring-Chat Realtime Analytics Job");
    }
}
