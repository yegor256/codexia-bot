package com.iakunin.codexiabot.hackernews.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iakunin.codexiabot.hackernews.entity.HackernewsItem;
import com.iakunin.codexiabot.hackernews.repository.jpa.HackernewsItemRepository;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;

@Component("hackernews.kafka.consumer.SaveToDatabase")
@Slf4j
public final class SaveToDatabase {

    private static final String TOPIC = "hackernews_item";

    private final ObjectMapper objectMapper;

    private final HackernewsItemRepository repository;

    public SaveToDatabase(
        @Value("${app.kafka.bootstrap-servers}") String kafkaBootstrapServers,
        ObjectMapper objectMapper,
        HackernewsItemRepository repository
    ) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        KafkaReceiver.create(
            ReceiverOptions.<Integer, String>create(
                new HashMap<>(){{
                    put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
                    //@TODO: rename consumer to `this.getClass().getName() + "-consumer"`
                    put(ConsumerConfig.GROUP_ID_CONFIG, "hackernews-item-save-to-database-consumer");
                    put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, IntegerDeserializer.class);
                    put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
                    put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
                    put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, Integer.MAX_VALUE);
                }}
            )
            .commitBatchSize(100)
            .commitInterval(Duration.ofSeconds(1))
            .subscription(Collections.singleton(TOPIC))
        )
        .receiveAutoAck()
        .concatMap(r -> r)
        .doOnNext(r -> {
            final HackernewsItem item = fromBinary(r.value(), HackernewsItem.class);
            log.info("Got Hackernews.Item: {}", item);

            if (!this.repository.existsByExternalId(item.getExternalId())) {
                log.info("Saving new Hackernews.Item: {}", item);
                this.repository.save(item);
            }
        })
        .subscribe();
    }

    //@TODO: maybe it's possible to pass serializer for concrete type?
    //  this will help to get rid of the `fromBinary()` method
    private <T> T fromBinary(String object, Class<T> resultType) {
        try {
            return this.objectMapper.readValue(object, resultType);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
