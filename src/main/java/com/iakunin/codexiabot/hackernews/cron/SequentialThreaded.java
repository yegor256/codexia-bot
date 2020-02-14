package com.iakunin.codexiabot.hackernews.cron;

import com.iakunin.codexiabot.hackernews.entity.HackernewsItem;
import com.iakunin.codexiabot.hackernews.repository.jpa.HackernewsItemRepository;
import com.iakunin.codexiabot.hackernews.sdk.client.Hackernews;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@AllArgsConstructor(onConstructor_ ={@Autowired})
public final class SequentialThreaded {

    private HackernewsItemRepository hackernewsItemRepository;

    private Hackernews hackernewsClient;

//    @Scheduled(cron="* * * * * *") // every second
    public void run() {
        log.info("SequentialThreaded");

        // 22 threads -> 105 items/sec
        final int nThreads = 44;
        ExecutorService executorService = Executors.newFixedThreadPool(nThreads);
        List<Future<?>> futureList = new LinkedList<>();

        final int maxExternalId = 22_275_145;
        for (int i = 0; i < nThreads; i++) {
            int finalI = i;
            Runnable runnableTask = () -> {
                final int batchSize = 500_000;
                IntStream.rangeClosed(finalI * batchSize + 1, (finalI + 1) * batchSize)
                    .mapToObj(String::valueOf)
                    .filter(
                        id -> this.hackernewsItemRepository.findByExternalId(id).isEmpty()
                    )
                    .map(
                        id -> Objects.requireNonNull(
                            this.hackernewsClient.getItem(id).getBody()
                        )
                    )
                    .forEach(
                        item -> {
                            log.info("Saving item with id: '{}'", item.getId());
                            this.hackernewsItemRepository.save(HackernewsItem.Factory.from(item));
                        }
                    );
            };

            futureList.add(executorService.submit(runnableTask));
        }

        futureList.forEach(f -> {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        });
    }
}