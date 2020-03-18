package dev.iakunin.codexiabot.hackernews.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.iakunin.codexiabot.github.GithubModule;
import dev.iakunin.codexiabot.hackernews.entity.HackernewsItem;
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

@Component("hackernews.kafka.consumer.GithubRepo")
@Slf4j
public final class GithubRepo {

    private static final String TOPIC = "hackernews_item";

    private final ObjectMapper objectMapper;

    private final GithubModule githubModule;

    public GithubRepo(
        ObjectMapper objectMapper,
        GithubModule githubModule,
        @Value("${app.kafka.bootstrap-servers}") String kafkaBootstrapServers
    ) {
        this.objectMapper = objectMapper;
        this.githubModule = githubModule;
        KafkaReceiver.create(
            ReceiverOptions.<Integer, String>create(
                    new HashMap<>(){{
                        //@TODO: move all there ConsumerConfigs to one place
                        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
                        //@TODO: rename consumer to `this.getClass().getName() + "-consumer"`
                        put(ConsumerConfig.GROUP_ID_CONFIG, "hackernews-item-consumer-1");
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
            //@TODO: rewrite via SpringRetry
            // https://objectpartners.com/2018/11/21/building-resilient-kafka-consumers-with-spring-retry/
            .receiveAutoAck()
            .concatMap(r -> r)
            .map(r -> {
                final HackernewsItem item = fromBinary(r.value(), HackernewsItem.class);
                log.info("Got an item from kafka with offset='{}'; item: {}", r.offset(), r.value());
                return item;
            })
            .filter(item ->
                item.getUrl() != null &&
                    item.getUrl().contains("github.com") &&
                    !item.getUrl().contains("gist.github.com")
            )
            .doOnNext(
                i -> {
                    try {
                        this.githubModule.createRepo(
                            new GithubModule.CreateArguments()
                                .setUrl(i.getUrl())
                                .setSource(GithubModule.Source.HACKERNEWS)
                                .setExternalId(String.valueOf(i.getExternalId()))
                        );
                    } catch (RuntimeException|IOException e) {
                        log.info("Unable to create github repo; source url='{}'", i.getUrl(), e);
                    }
                }
            )
            .subscribe()
        ;
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