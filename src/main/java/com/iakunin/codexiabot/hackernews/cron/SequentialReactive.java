package com.iakunin.codexiabot.hackernews.cron;

import com.iakunin.codexiabot.hackernews.entity.HackernewsItem;
import com.iakunin.codexiabot.hackernews.repository.jpa.HackernewsItemRepository;
import com.iakunin.codexiabot.hackernews.repository.reactive.HackernewsItemRepositoryImpl;
import com.iakunin.codexiabot.hackernews.sdk.client.Hackernews;
import java.util.concurrent.CountDownLatch;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.JettyClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Slf4j
@AllArgsConstructor(onConstructor_ ={@Autowired})
public final class SequentialReactive {

    private final HackernewsItemRepositoryImpl reactiveRepository;

    private final HackernewsItemRepository nonReactiveRepository;

//    @Scheduled(cron="* * * * * *") // every second
    public void run() throws InterruptedException {
        log.info("SequentialReactive run");

        final int maxExternalId = this.nonReactiveRepository.getMaxExternalId();

        SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
        final HttpClient httpClient = new HttpClient(sslContextFactory);
        httpClient.setMaxRequestsQueuedPerDestination(10000);
        ClientHttpConnector clientConnector = new JettyClientHttpConnector(httpClient);
        WebClient client = WebClient.builder().clientConnector(clientConnector).build();

        reactiveRepository
            .findAbsentExternalIds(1, maxExternalId)
            .flatMap(
                id -> client.get()
                    .uri(
                        String.format("https://hacker-news.firebaseio.com/v0/item/%s.json", id)
                    )
                    .retrieve()
                    .bodyToMono(Hackernews.Item.class)
                    .onErrorReturn(
                        new Hackernews.Item()
                            .setId(id)
                            .setType("")
                    )
            )
            .doOnError(e -> log.error("Getting from hacker-news is failed", e))
            .map(HackernewsItem.Factory::from)
            .window(10000)
            .flatMap(reactiveRepository::save)
            .doOnError(e -> log.error("DecrementByIdReactive failed", e))
            .subscribe()
        ;

        new CountDownLatch(1).await();
    }
}
